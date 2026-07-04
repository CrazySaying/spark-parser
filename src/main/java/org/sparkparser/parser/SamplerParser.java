package org.sparkparser.parser;

import me.lucko.spark.proto.SparkProtos;
import me.lucko.spark.proto.SparkSamplerProtos;
import org.sparkparser.model.ParsedData;
import org.sparkparser.model.SamplerResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Parses .sparkprofile protobuf files into SamplerResult.
 *
 * The key algorithm is call tree reconstruction:
 * Spark stores threads as a flat array of StackTraceNode with integer
 * children_refs pointing to sibling indices. We rebuild the full tree.
 */
public class SamplerParser implements FileParser {

    @Override
    public ParsedData parse(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        SparkSamplerProtos.SamplerData proto = SparkSamplerProtos.SamplerData.parseFrom(bytes);

        SamplerResult result = new SamplerResult();
        result.class_sources = new LinkedHashMap<>(proto.getClassSourcesMap());
        result.method_sources = new LinkedHashMap<>(proto.getMethodSourcesMap());
        result.line_sources = new LinkedHashMap<>(proto.getLineSourcesMap());

        // metadata
        result.metadata = buildSamplerMetadata(proto.getMetadata());

        // threads + call tree reconstruction
        int totalNodes = 0;
        int maxDepth = 0;
        List<SamplerResult.ThreadInfo> threads = new ArrayList<>();
        double grandTotalTime = 0;

        // First pass: calculate grand total time for percentage calculation
        // (sum of all root node times across all threads)
        for (SparkSamplerProtos.ThreadNode thread : proto.getThreadsList()) {
            for (SparkSamplerProtos.StackTraceNode node : thread.getChildrenList()) {
                double nodeTotal = sumTimes(node.getTimesList());
                grandTotalTime += nodeTotal;
            }
        }

        for (SparkSamplerProtos.ThreadNode thread : proto.getThreadsList()) {
            SamplerResult.ThreadInfo threadInfo = new SamplerResult.ThreadInfo();
            threadInfo.name = thread.getName();
            threadInfo.times = new ArrayList<>(thread.getTimesList());

            int childCount = thread.getChildrenCount();
            totalNodes += childCount;

            // Step 1: Build TreeNode wrappers for each child
            List<TreeNodeWrapper> wrappers = new ArrayList<>();
            for (int i = 0; i < childCount; i++) {
                SparkSamplerProtos.StackTraceNode protoNode = thread.getChildren(i);
                TreeNodeWrapper wrapper = new TreeNodeWrapper();
                wrapper.index = i;
                wrapper.protoNode = protoNode;
                wrapper.treeNode = new SamplerResult.TreeNode();
                wrapper.treeNode.class_name = protoNode.getClassName();
                wrapper.treeNode.method_name = protoNode.getMethodName();
                wrapper.treeNode.method_desc = protoNode.getMethodDesc();
                wrapper.treeNode.line_number = protoNode.getLineNumber();
                wrapper.treeNode.parent_line_number = protoNode.getParentLineNumber();
                wrapper.treeNode.times = new ArrayList<>(protoNode.getTimesList());
                wrapper.treeNode.children = new ArrayList<>();
                double nodeTotal = sumTimes(protoNode.getTimesList());
                wrapper.treeNode.total_time_percent = grandTotalTime > 0 ? (100.0 * nodeTotal / grandTotalTime) : 0;
                wrappers.add(wrapper);
            }

            // Step 2: Link children via children_refs
            for (TreeNodeWrapper wrapper : wrappers) {
                for (int childRef : wrapper.protoNode.getChildrenRefsList()) {
                    if (childRef >= 0 && childRef < wrappers.size()) {
                        TreeNodeWrapper child = wrappers.get(childRef);
                        child.hasParent = true;
                        wrapper.treeNode.children.add(child.treeNode);
                    }
                }
            }

            // Step 3: Collect roots (nodes with no parent)
            for (TreeNodeWrapper wrapper : wrappers) {
                if (!wrapper.hasParent) {
                    threadInfo.root_nodes.add(wrapper.treeNode);
                }
            }

            // calculate depth
            for (SamplerResult.TreeNode root : threadInfo.root_nodes) {
                int depth = maxTreeDepth(root, 1);
                if (depth > maxDepth) maxDepth = depth;
            }

            threads.add(threadInfo);
        }

        result.threads = threads;

        // time windows
        List<SamplerResult.TimeWindowInfo> windows = new ArrayList<>();
        for (var entry : proto.getTimeWindowStatisticsMap().entrySet()) {
            windows.add(HeapParser.buildTimeWindow(entry.getKey(), entry.getValue()));
        }
        windows.sort(Comparator.comparingInt(w -> w.tick_index));
        result.time_windows = windows;

        // summary
        SamplerResult.SamplerMetadata meta = result.metadata;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_threads", threads.size());
        summary.put("total_stack_nodes", totalNodes);
        summary.put("max_call_depth", maxDepth);
        summary.put("sampler_mode", meta.sampler_mode);
        summary.put("sampler_engine", meta.sampler_engine);
        summary.put("interval_ms", meta.interval_ms);
        if (meta.start_time_epoch_ms > 0 && meta.end_time_epoch_ms > 0) {
            long durationMs = meta.end_time_epoch_ms - meta.start_time_epoch_ms;
            summary.put("sampling_duration_seconds", durationMs / 1000.0);
            summary.put("start_time", Instant.ofEpochMilli(meta.start_time_epoch_ms).toString());
            summary.put("end_time", Instant.ofEpochMilli(meta.end_time_epoch_ms).toString());
        }
        if (meta.platform_stats != null && meta.platform_stats.tps != null) {
            summary.put("avg_tps_last_1m", meta.platform_stats.tps.last_1m);
        }
        summary.put("time_windows_count", windows.size());
        if (meta.creator != null) {
            summary.put("created_by", meta.creator.name);
        }
        result.summary = summary;

        return result;
    }

    private SamplerResult.SamplerMetadata buildSamplerMetadata(SparkSamplerProtos.SamplerMetadata proto) {
        SamplerResult.SamplerMetadata md = new SamplerResult.SamplerMetadata();
        md.start_time_epoch_ms = proto.getStartTime();
        md.end_time_epoch_ms = proto.getEndTime();
        md.interval_ms = proto.getInterval();
        md.sampler_mode = proto.getSamplerMode().name();
        md.sampler_engine = proto.getSamplerEngine().name();
        md.sampler_engine_version = proto.getSamplerEngineVersion();
        md.comment = proto.getComment();

        if (proto.hasCreator()) {
            md.creator = HeapParser.buildCreator(proto.getCreator());
        }
        if (proto.hasPlatformMetadata()) {
            md.platform = HeapParser.buildPlatformInfo(proto.getPlatformMetadata());
        }
        if (proto.hasPlatformStatistics()) {
            md.platform_stats = HeapParser.buildPlatformStats(proto.getPlatformStatistics());
        }
        if (proto.hasSystemStatistics()) {
            md.system_stats = HeapParser.buildSystemStats(proto.getSystemStatistics());
        }

        if (proto.hasThreadDumper()) {
            md.thread_dumper_type = proto.getThreadDumper().getType().name();
        }
        if (proto.hasDataAggregator()) {
            md.data_aggregator_type = proto.getDataAggregator().getType().name();
            md.thread_grouper = proto.getDataAggregator().getThreadGrouper().name();
        }

        md.server_configurations = new LinkedHashMap<>(proto.getServerConfigurationsMap());
        md.sources = new LinkedHashMap<>();
        for (var entry : proto.getSourcesMap().entrySet()) {
            md.sources.put(entry.getKey(), HeapParser.buildSourceInfo(entry.getValue()));
        }
        return md;
    }

    private static double sumTimes(List<Double> times) {
        double sum = 0;
        for (double t : times) sum += t;
        return sum;
    }

    private static int maxTreeDepth(SamplerResult.TreeNode node, int currentDepth) {
        int maxChild = currentDepth;
        for (SamplerResult.TreeNode child : node.children) {
            int d = maxTreeDepth(child, currentDepth + 1);
            if (d > maxChild) maxChild = d;
        }
        return maxChild;
    }

    /** Wrapper class to track parent linkage during tree reconstruction. */
    private static class TreeNodeWrapper {
        int index;
        boolean hasParent;
        SparkSamplerProtos.StackTraceNode protoNode;
        SamplerResult.TreeNode treeNode;
    }
}
