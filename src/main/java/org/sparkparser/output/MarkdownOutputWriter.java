package org.sparkparser.output;

import org.sparkparser.model.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Writes parsed data as human-readable Markdown report.
 */
public class MarkdownOutputWriter implements OutputWriter {

    private static final int MAX_TREE_NODES = Integer.MAX_VALUE;

    @Override
    public void write(ParsedData data, OutputStream out) throws IOException {
        PrintWriter w = new PrintWriter(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8));
        if (data instanceof SamplerResult r) {
            writeSampler(w, r);
        } else if (data instanceof HeapResult r) {
            writeHeap(w, r);
        } else if (data instanceof HealthResult r) {
            writeHealth(w, r);
        } else if (data instanceof ActivityResult r) {
            writeActivity(w, r);
        } else {
            w.println("Unknown data type: " + data.getFileType());
        }
        w.flush();
    }

    // ---- Sampler ----

    private void writeSampler(PrintWriter w, SamplerResult r) {
        w.println("# 🔥 Spark Sampler Profile Report");
        w.println();

        SamplerResult.SamplerMetadata meta = r.metadata;
        if (meta != null && meta.creator != null) {
            w.println("**Created by:** " + meta.creator.name);
        }
        w.println();

        w.println("## Summary");
        w.println();
        writeSummaryTable(w, r.summary);
        w.println();

        if (meta != null) {
            w.println("## Configuration");
            w.println();
            w.println("| Setting | Value |");
            w.println("|---------|-------|");
            row(w, "Sampler Mode", meta.sampler_mode);
            row(w, "Sampler Engine", meta.sampler_engine + (meta.sampler_engine_version != null ? " v" + meta.sampler_engine_version : ""));
            row(w, "Interval", meta.interval_ms + " ms");
            row(w, "Thread Dumper", meta.thread_dumper_type);
            row(w, "Data Aggregator", meta.data_aggregator_type);
            row(w, "Thread Grouper", meta.thread_grouper);
            if (meta.comment != null && !meta.comment.isEmpty()) {
                row(w, "Comment", meta.comment);
            }
            w.println();

            if (meta.platform != null) {
                w.println("## Platform");
                w.println();
                w.println("| Property | Value |");
                w.println("|----------|-------|");
                row(w, "Type", meta.platform.type);
                row(w, "Server", meta.platform.name + " " + meta.platform.version);
                row(w, "Minecraft", meta.platform.minecraft_version);
                row(w, "Spark Version", String.valueOf(meta.platform.spark_version));
                w.println();
            }

            if (meta.platform_stats != null && meta.platform_stats.tps != null) {
                w.println("## TPS");
                w.println();
                var tps = meta.platform_stats.tps;
                w.println("Last 1m: **" + String.format("%.1f", tps.last_1m) +
                    "**  |  Last 5m: **" + String.format("%.1f", tps.last_5m) +
                    "**  |  Last 15m: **" + String.format("%.1f", tps.last_15m) +
                    "**  |  Target: " + tps.target_tps);
                w.println();
            }

            if (meta.platform_stats != null && meta.platform_stats.mspt != null) {
                w.println("## MSPT");
                w.println();
                writeRollingAvgTable(w, "Last 1 minute", meta.platform_stats.mspt.last_1m);
                writeRollingAvgTable(w, "Last 5 minutes", meta.platform_stats.mspt.last_5m);
                w.println();
            }

            // ---- System Statistics ----
            if (meta.system_stats != null) {
                var sys = meta.system_stats;
                w.println("## System");
                w.println();
                w.println("| Property | Value |");
                w.println("|----------|-------|");
                if (sys.cpu != null) {
                    if (sys.cpu.model_name != null && !sys.cpu.model_name.isEmpty()) {
                        row(w, "CPU", sys.cpu.model_name + " (" + sys.cpu.threads + " threads)");
                    } else {
                        row(w, "CPU Threads", String.valueOf(sys.cpu.threads));
                    }
                    if (sys.cpu.process != null) {
                        row(w, "CPU Process Usage", String.format("%.1f%% (1m) / %.1f%% (15m)",
                            sys.cpu.process.last_1m, sys.cpu.process.last_15m));
                    }
                    if (sys.cpu.system != null) {
                        row(w, "CPU System Usage", String.format("%.1f%% (1m) / %.1f%% (15m)",
                            sys.cpu.system.last_1m, sys.cpu.system.last_15m));
                    }
                }
                if (sys.memory != null) {
                    if (sys.memory.physical != null) {
                        double usedGB = sys.memory.physical.used_bytes / (1024.0 * 1024.0 * 1024.0);
                        double totalGB = sys.memory.physical.total_bytes / (1024.0 * 1024.0 * 1024.0);
                        row(w, "Physical Memory", String.format("%.1f GB / %.1f GB (%.1f%%)",
                            usedGB, totalGB, totalGB > 0 ? 100.0 * usedGB / totalGB : 0));
                    }
                    if (sys.memory.swap != null && sys.memory.swap.total_bytes > 0) {
                        row(w, "Swap", formatBytes(sys.memory.swap.used_bytes) + " / " + formatBytes(sys.memory.swap.total_bytes));
                    }
                }
                if (sys.disk != null && sys.disk.total_bytes > 0) {
                    double diskUsedGB = sys.disk.used_bytes / (1024.0 * 1024.0 * 1024.0);
                    double diskTotalGB = sys.disk.total_bytes / (1024.0 * 1024.0 * 1024.0);
                    row(w, "Disk", String.format("%.1f GB / %.1f GB (%.1f%%)",
                        diskUsedGB, diskTotalGB, 100.0 * diskUsedGB / diskTotalGB));
                }
                if (sys.os != null) {
                    row(w, "OS", sys.os.name + " " + sys.os.version + " (" + sys.os.arch + ")");
                }
                if (sys.uptime_seconds > 0) {
                    row(w, "System Uptime", formatUptime(sys.uptime_seconds));
                }
                w.println();

                // ---- Java & JVM ----
                boolean hasJavaInfo = sys.java != null;
                boolean hasJvmInfo = sys.jvm != null;
                if (hasJavaInfo || hasJvmInfo) {
                    w.println("## Java / JVM");
                    w.println();
                    w.println("| Property | Value |");
                    w.println("|----------|-------|");
                    if (sys.java != null) {
                        if (sys.java.vendor != null && !sys.java.vendor.isEmpty()) {
                            row(w, "Java Vendor", sys.java.vendor);
                        }
                        if (sys.java.version != null && !sys.java.version.isEmpty()) {
                            row(w, "Java Version", sys.java.version);
                        }
                        if (sys.java.vendor_version != null && !sys.java.vendor_version.isEmpty()) {
                            row(w, "Vendor Version", sys.java.vendor_version);
                        }
                    }
                    if (sys.jvm != null) {
                        if (sys.jvm.name != null && !sys.jvm.name.isEmpty()) {
                            row(w, "JVM Name", sys.jvm.name);
                        }
                        if (sys.jvm.vendor != null && !sys.jvm.vendor.isEmpty()) {
                            row(w, "JVM Vendor", sys.jvm.vendor);
                        }
                        if (sys.jvm.version != null && !sys.jvm.version.isEmpty()) {
                            row(w, "JVM Version", sys.jvm.version);
                        }
                    }
                    w.println();
                }

                // ---- VM Arguments ----
                if (sys.java != null && sys.java.vm_args != null && !sys.java.vm_args.isEmpty()) {
                    w.println("## JVM Startup Arguments");
                    w.println();
                    w.println("```");
                    // Split args by whitespace for readability, wrap long lines
                    String[] args = sys.java.vm_args.split("\\s+");
                    StringBuilder line = new StringBuilder();
                    for (String arg : args) {
                        if (arg.isEmpty()) continue;
                        if (line.length() + arg.length() + 1 > 120 && line.length() > 0) {
                            w.println(line.toString());
                            line.setLength(0);
                        }
                        if (line.length() > 0) line.append(' ');
                        line.append(arg);
                    }
                    if (line.length() > 0) {
                        w.println(line.toString());
                    }
                    w.println("```");
                    w.println();
                }

                // ---- GC Statistics (system level) ----
                if (sys.gc != null && !sys.gc.isEmpty()) {
                    w.println("## Garbage Collection (System)");
                    w.println();
                    w.println("| GC Name | Collections | Avg Time | Avg Frequency |");
                    w.println("|---------|-------------|----------|---------------|");
                    for (var gcEntry : sys.gc.entrySet()) {
                        var gc = gcEntry.getValue();
                        w.println("| " + gcEntry.getKey()
                            + " | " + String.format("%,d", gc.total_collections)
                            + " | " + String.format("%.2f ms", gc.avg_time_ms)
                            + " | " + String.format("%.2f/s", gc.avg_frequency_per_sec) + " |");
                    }
                    w.println();
                }
            }

            // ---- Platform Memory (JVM heap) ----
            if (meta.platform_stats != null && meta.platform_stats.memory != null) {
                var mem = meta.platform_stats.memory;
                boolean hasHeap = mem.heap != null;
                boolean hasNonHeap = mem.non_heap != null;
                boolean hasPools = mem.pools != null && !mem.pools.isEmpty();

                if (hasHeap || hasNonHeap) {
                    w.println("## JVM Memory");
                    w.println();
                    w.println("| Region | Used | Committed | Max |");
                    w.println("|--------|------|-----------|-----|");
                    if (hasHeap) {
                        w.println("| Heap | " + formatBytes(mem.heap.used_bytes)
                            + " | " + formatBytes(mem.heap.committed_bytes)
                            + " | " + formatBytes(mem.heap.max_bytes) + " |");
                    }
                    if (hasNonHeap) {
                        w.println("| Non-Heap | " + formatBytes(mem.non_heap.used_bytes)
                            + " | " + formatBytes(mem.non_heap.committed_bytes)
                            + " | " + (mem.non_heap.max_bytes > 0 ? formatBytes(mem.non_heap.max_bytes) : "N/A") + " |");
                    }
                    w.println();
                }

                if (hasPools) {
                    w.println("### Memory Pools");
                    w.println();
                    w.println("| Pool | Used | Committed | Max |");
                    w.println("|------|------|-----------|-----|");
                    for (var pool : mem.pools) {
                        w.println("| " + pool.name
                            + " | " + formatBytes(pool.usage.used_bytes)
                            + " | " + formatBytes(pool.usage.committed_bytes)
                            + " | " + (pool.usage.max_bytes > 0 ? formatBytes(pool.usage.max_bytes) : "N/A") + " |");
                    }
                    w.println();
                }
            }

            // ---- Platform GC ----
            if (meta.platform_stats != null && meta.platform_stats.gc != null && !meta.platform_stats.gc.isEmpty()) {
                w.println("## Garbage Collection (JVM)");
                w.println();
                w.println("| GC Name | Collections | Avg Time | Avg Frequency |");
                w.println("|---------|-------------|----------|---------------|");
                for (var gcEntry : meta.platform_stats.gc.entrySet()) {
                    var gc = gcEntry.getValue();
                    w.println("| " + gcEntry.getKey()
                        + " | " + String.format("%,d", gc.total_collections)
                        + " | " + String.format("%.2f ms", gc.avg_time_ms)
                        + " | " + String.format("%.2f/s", gc.avg_frequency_per_sec) + " |");
                }
                w.println();
            }

            // ---- Server Configurations ----
            if (meta.server_configurations != null && !meta.server_configurations.isEmpty()) {
                w.println("## Server Configurations");
                w.println();
                w.println("| Property | Value |");
                w.println("|----------|-------|");
                meta.server_configurations.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> row(w, e.getKey(), e.getValue()));
                w.println();
            }

            // ---- Sources (Mods / Plugins) ----
            if (meta.sources != null && !meta.sources.isEmpty()) {
                w.println("## Mods / Plugins (" + meta.sources.size() + ")");
                w.println();
                w.println("| Name | Version | Author |");
                w.println("|------|---------|--------|");
                meta.sources.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        var s = e.getValue();
                        w.println("| " + e.getKey()
                            + " | " + (s.version != null ? s.version : "-")
                            + " | " + (s.author != null ? s.author : "-") + " |");
                    });
                w.println();
            }
        }

        // Thread call trees
        w.println("## Thread Call Trees");
        w.println();
        int nodeCount = 0;
        for (SamplerResult.ThreadInfo thread : r.threads) {
            w.println("### " + thread.name);
            w.println();
            w.println("```");
            for (SamplerResult.TreeNode root : thread.root_nodes) {
                nodeCount = writeTree(w, root, "", nodeCount);
            }
            w.println("```");
            w.println();
            if (nodeCount >= MAX_TREE_NODES) {
                w.println("*... tree truncated at " + MAX_TREE_NODES + " nodes ...*");
                w.println();
                break;
            }
        }

        // Time windows
        if (r.time_windows != null && !r.time_windows.isEmpty()) {
            w.println("## Time Windows");
            w.println();
            w.println("| Tick | Duration | TPS | MSPT Med | MSPT Max | CPU% | Players | Entities |");
            w.println("|------|----------|-----|----------|----------|------|---------|----------|");
            for (SamplerResult.TimeWindowInfo tw : r.time_windows) {
                w.println("| " + tw.tick_index +
                    " | " + tw.duration_ms + "ms" +
                    " | " + String.format("%.1f", tw.tps) +
                    " | " + String.format("%.1f", tw.mspt_median) +
                    " | " + String.format("%.1f", tw.mspt_max) +
                    " | " + String.format("%.1f%%", tw.cpu_process) +
                    " | " + tw.players +
                    " | " + tw.entities + " |");
            }
            w.println();
        }
    }

    private int writeTree(PrintWriter w, SamplerResult.TreeNode node, String prefix, int count) {
        if (count >= MAX_TREE_NODES) return count;
        String pct = String.format("%.1f%%", node.total_time_percent);
        String label = node.class_name + "." + node.method_name;
        if (node.line_number > 0) label += ":" + node.line_number;
        w.println(prefix + label + " [" + pct + "]");
        count++;
        for (SamplerResult.TreeNode child : node.children) {
            count = writeTree(w, child, prefix + "  ", count);
            if (count >= MAX_TREE_NODES) return count;
        }
        return count;
    }

    // ---- Heap ----

    private void writeHeap(PrintWriter w, HeapResult r) {
        w.println("# 📦 Spark Heap Summary Report");
        w.println();

        HeapResult.HeapMetadata meta = r.metadata;
        if (meta != null && meta.creator != null) {
            w.println("**Created by:** " + meta.creator.name);
            w.println("**Generated:** " + Instant.ofEpochMilli(meta.generated_time_epoch_ms).toString());
        }
        w.println();

        w.println("## Summary");
        w.println();
        writeSummaryTable(w, r.summary);
        w.println();

        w.println("## Class Histogram (by size)");
        w.println();
        w.println("| # | Class | Instances | Size | % of Total |");
        w.println("|---|-------|-----------|------|------------|");
        int count = 0;
        for (HeapResult.HeapEntryInfo entry : r.entries) {
            if (count++ >= 50) {
                w.println("| ... | *truncated* | | | |");
                break;
            }
            w.println("| " + count +
                " | `" + entry.type + "`" +
                " | " + String.format("%,d", entry.instances) +
                " | " + formatBytes(entry.size_bytes) +
                " | " + String.format("%.1f%%", entry.size_percent) + " |");
        }
        w.println();
    }

    // ---- Health ----

    private void writeHealth(PrintWriter w, HealthResult r) {
        w.println("# 💚 Spark Server Health Report");
        w.println();

        HealthResult.HealthMetadata meta = r.metadata;
        if (meta != null) {
            if (meta.creator != null) {
                w.println("**Created by:** " + meta.creator.name);
            }
            w.println("**Generated:** " + Instant.ofEpochMilli(meta.generated_time_epoch_ms).toString());
        }
        w.println();

        w.println("## Summary");
        w.println();
        writeSummaryTable(w, r.summary);
        w.println();

        if (meta != null) {
            if (meta.platform != null) {
                w.println("## Platform");
                w.println();
                w.println("| Property | Value |");
                w.println("|----------|-------|");
                row(w, "Type", meta.platform.type);
                row(w, "Name", meta.platform.name);
                row(w, "Version", meta.platform.version);
                row(w, "Minecraft", meta.platform.minecraft_version);
                w.println();
            }

            if (meta.system_stats != null) {
                var sys = meta.system_stats;
                w.println("## System");
                w.println();
                w.println("| Property | Value |");
                w.println("|----------|-------|");
                if (sys.cpu != null) {
                    row(w, "CPU Model", sys.cpu.model_name);
                    row(w, "CPU Threads", String.valueOf(sys.cpu.threads));
                    if (sys.cpu.process != null) {
                        row(w, "CPU Process", String.format("%.1f%% (1m), %.1f%% (15m)", sys.cpu.process.last_1m, sys.cpu.process.last_15m));
                    }
                    if (sys.cpu.system != null) {
                        row(w, "CPU System", String.format("%.1f%% (1m), %.1f%% (15m)", sys.cpu.system.last_1m, sys.cpu.system.last_15m));
                    }
                }
                if (sys.memory != null && sys.memory.physical != null) {
                    row(w, "Physical Memory", formatBytes(sys.memory.physical.used_bytes) + " / " + formatBytes(sys.memory.physical.total_bytes));
                }
                if (sys.disk != null) {
                    row(w, "Disk", formatBytes(sys.disk.used_bytes) + " / " + formatBytes(sys.disk.total_bytes));
                }
                if (sys.os != null) {
                    row(w, "OS", sys.os.name + " " + sys.os.version + " (" + sys.os.arch + ")");
                }
                if (sys.java != null) {
                    row(w, "Java", sys.java.vendor + " " + sys.java.version);
                }
                w.println();
            }

            if (meta.platform_stats != null && meta.platform_stats.world != null) {
                var world = meta.platform_stats.world;
                w.println("## World");
                w.println();
                w.println("**Total Entities:** " + world.total_entities);
                w.println();
                if (world.entity_counts != null && !world.entity_counts.isEmpty()) {
                    w.println("### Entity Breakdown");
                    w.println();
                    w.println("| Entity Type | Count |");
                    w.println("|-------------|-------|");
                    world.entity_counts.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .limit(20)
                        .forEach(e -> w.println("| " + e.getKey() + " | " + e.getValue() + " |"));
                    w.println();
                }
            }
        }
    }

    // ---- Activity ----

    private void writeActivity(PrintWriter w, ActivityResult r) {
        w.println("# 📋 Spark Activity Log");
        w.println();
        writeSummaryTable(w, r.summary);
        w.println();

        w.println("## Entries");
        w.println();
        w.println("| Time | User | Type | Data |");
        w.println("|------|------|------|------|");
        for (ActivityResult.ActivityEntry entry : r.activities) {
            String dataLink = "";
            if ("url".equals(entry.data_type)) {
                dataLink = "[link](" + entry.data_value + ")";
            } else if (entry.data_value != null) {
                dataLink = entry.data_value;
            }
            w.println("| " + (entry.time_iso != null ? entry.time_iso : String.valueOf(entry.time_epoch_ms)) +
                " | " + entry.user_name +
                " | " + entry.activity_type +
                " | " + dataLink + " |");
        }
        w.println();
    }

    // ---- helpers ----

    private void row(PrintWriter w, String key, String value) {
        w.println("| " + key + " | " + (value != null ? value : "-") + " |");
    }

    private void writeSummaryTable(PrintWriter w, Map<String, Object> summary) {
        if (summary == null) return;
        w.println("| Key | Value |");
        w.println("|-----|-------|");
        for (var entry : summary.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof java.util.List<?> list) {
                value = list.toString();
            } else if (value instanceof java.util.Map<?, ?> map) {
                value = map.toString();
            }
            w.println("| " + entry.getKey() + " | " + value + " |");
        }
    }

    private void writeRollingAvgTable(PrintWriter w, String label, SamplerResult.RollingAvg ra) {
        if (ra == null) return;
        w.println("| " + label + " | Mean: " + String.format("%.2f", ra.mean) +
            " | Max: " + String.format("%.2f", ra.max) +
            " | Min: " + String.format("%.2f", ra.min) +
            " | Median: " + String.format("%.2f", ra.median) +
            " | P95: " + String.format("%.2f", ra.percentile_95) + " |");
    }

    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}
