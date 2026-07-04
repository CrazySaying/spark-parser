package org.sparkparser.output;

import org.sparkparser.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes parsed data as concise plain text.
 */
public class TextOutputWriter implements OutputWriter {

    private static final int MAX_TREE_NODES = Integer.MAX_VALUE;

    @Override
    public void write(ParsedData data, OutputStream out) throws IOException {
        PrintWriter w = new PrintWriter(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8));
        if (data instanceof SamplerResult r) {
            writeSamplerText(w, r);
        } else if (data instanceof HeapResult r) {
            writeHeapText(w, r);
        } else if (data instanceof HealthResult r) {
            writeHealthText(w, r);
        } else if (data instanceof ActivityResult r) {
            writeActivityText(w, r);
        } else {
            w.println("Unknown data type: " + data.getFileType());
        }
        w.flush();
    }

    private void writeSamplerText(PrintWriter w, SamplerResult r) {
        w.println("=== Spark Sampler Profile ===");
        w.println();
        SamplerResult.SamplerMetadata meta = r.metadata;
        if (meta != null) {
            if (meta.creator != null) w.println("Created by: " + meta.creator.name);
            w.println("Engine: " + meta.sampler_engine + " | Mode: " + meta.sampler_mode +
                " | Interval: " + meta.interval_ms + "ms");
            if (meta.start_time_epoch_ms > 0) {
                w.println("Start: " + Instant.ofEpochMilli(meta.start_time_epoch_ms) +
                    " | End: " + Instant.ofEpochMilli(meta.end_time_epoch_ms));
            }
        }
        if (r.summary != null) {
            r.summary.forEach((k, v) -> w.println(k + ": " + v));
        }
        w.println();

        w.println("--- Thread Call Trees ---");
        int nodeCount = 0;
        for (SamplerResult.ThreadInfo thread : r.threads) {
            w.println();
            w.println("[" + thread.name + "]");
            for (SamplerResult.TreeNode root : thread.root_nodes) {
                nodeCount = writeTextTree(w, root, "", nodeCount);
            }
            if (nodeCount >= MAX_TREE_NODES) {
                w.println("... (truncated at " + MAX_TREE_NODES + " nodes)");
                break;
            }
        }
    }

    private int writeTextTree(PrintWriter w, SamplerResult.TreeNode node, String prefix, int count) {
        if (count >= MAX_TREE_NODES) return count;
        String pct = String.format("%.1f%%", node.total_time_percent);
        String label = node.class_name + "." + node.method_name;
        if (node.line_number > 0) label += ":" + node.line_number;
        w.println(prefix + label + " [" + pct + "]");
        count++;
        for (SamplerResult.TreeNode child : node.children) {
            count = writeTextTree(w, child, prefix + "  ", count);
            if (count >= MAX_TREE_NODES) return count;
        }
        return count;
    }

    private void writeHeapText(PrintWriter w, HeapResult r) {
        w.println("=== Spark Heap Summary ===");
        w.println();
        if (r.metadata != null && r.metadata.creator != null) {
            w.println("Created by: " + r.metadata.creator.name);
        }
        if (r.summary != null) {
            r.summary.forEach((k, v) -> w.println(k + ": " + v));
        }
        w.println();
        w.println("--- Top Classes by Size ---");
        int count = 0;
        for (HeapResult.HeapEntryInfo entry : r.entries) {
            if (count++ >= 30) {
                w.println("... (" + (r.entries.size() - 30) + " more)");
                break;
            }
            w.println(String.format("%3d. %-60s %,8d instances  %10s  %5.1f%%",
                count, entry.type, entry.instances,
                formatBytes(entry.size_bytes), entry.size_percent));
        }
    }

    private void writeHealthText(PrintWriter w, HealthResult r) {
        w.println("=== Spark Health Report ===");
        w.println();
        HealthResult.HealthMetadata meta = r.metadata;
        if (meta != null) {
            if (meta.creator != null) w.println("Created by: " + meta.creator.name);
            w.println("Generated: " + Instant.ofEpochMilli(meta.generated_time_epoch_ms));
            if (meta.platform_stats != null && meta.platform_stats.tps != null) {
                var tps = meta.platform_stats.tps;
                w.println(String.format("TPS: %.1f / %.1f / %.1f (1m/5m/15m)", tps.last_1m, tps.last_5m, tps.last_15m));
            }
            if (meta.platform_stats != null && meta.platform_stats.mspt != null) {
                var mspt = meta.platform_stats.mspt;
                w.println(String.format("MSPT: median=%.2f max=%.2f (1m)", mspt.last_1m.median, mspt.last_1m.max));
            }
            w.println("Players: " + (meta.platform_stats != null ? meta.platform_stats.player_count : "?"));
            if (meta.system_stats != null && meta.system_stats.cpu != null && meta.system_stats.cpu.process != null) {
                w.println(String.format("CPU: %.1f%% process", meta.system_stats.cpu.process.last_1m));
            }
        }
        if (r.summary != null) {
            r.summary.forEach((k, v) -> w.println(k + ": " + v));
        }
    }

    private void writeActivityText(PrintWriter w, ActivityResult r) {
        w.println("=== Spark Activity Log ===");
        w.println();
        if (r.summary != null) {
            r.summary.forEach((k, v) -> w.println(k + ": " + v));
        }
        w.println();
        for (ActivityResult.ActivityEntry entry : r.activities) {
            w.println(String.format("%s | %-16s | %-12s | %s",
                entry.time_iso != null ? entry.time_iso.substring(0, 19) : entry.time_epoch_ms,
                entry.user_name,
                entry.activity_type,
                entry.data_value != null ? entry.data_value : ""));
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}
