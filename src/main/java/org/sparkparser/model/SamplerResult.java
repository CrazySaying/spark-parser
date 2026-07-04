package org.sparkparser.model;

import java.util.List;
import java.util.Map;

/**
 * Flattened CPU sampler (profiler) result, AI-friendly structure.
 */
public class SamplerResult implements ParsedData {
    public String file_type = "sampler";
    public String parser_version = "1.0.0";
    public Map<String, Object> summary;
    public SamplerMetadata metadata;
    public List<ThreadInfo> threads;
    public List<TimeWindowInfo> time_windows;
    public Map<String, String> class_sources;
    public Map<String, String> method_sources;
    public Map<String, String> line_sources;

    @Override
    public String getFileType() { return file_type; }

    @Override
    public String getParserVersion() { return parser_version; }

    // ---- inner POJOs ----

    public static class SamplerMetadata {
        public Creator creator;
        public long start_time_epoch_ms;
        public long end_time_epoch_ms;
        public int interval_ms;
        public String sampler_mode;
        public String sampler_engine;
        public String sampler_engine_version;
        public String comment;
        public String thread_dumper_type;
        public String data_aggregator_type;
        public String thread_grouper;
        public PlatformInfo platform;
        public PlatformStats platform_stats;
        public SystemStats system_stats;
        public Map<String, String> server_configurations;
        public Map<String, SourceInfo> sources;
    }

    public static class Creator {
        public String name;
        public String unique_id;
        public String type;
    }

    public static class PlatformInfo {
        public String type;
        public String name;
        public String version;
        public String minecraft_version;
        public int spark_version;
        public String brand;
    }

    public static class PlatformStats {
        public MemoryStats memory;
        public Map<String, GcStats> gc;
        public TpsStats tps;
        public MsptStats mspt;
        public PingStats ping;
        public long player_count;
        public String online_mode;
        public long uptime_seconds;
        public WorldStats world;
    }

    public static class SystemStats {
        public CpuStats cpu;
        public MemoryPoolStats memory;
        public Map<String, GcStats> gc;
        public DiskStats disk;
        public OsStats os;
        public JavaStats java;
        public JvmStats jvm;
        public long uptime_seconds;
    }

    public static class MemoryStats {
        public MemoryUsageInfo heap;
        public MemoryUsageInfo non_heap;
        public List<MemoryPoolInfo> pools;
    }

    public static class MemoryUsageInfo {
        public long used_bytes;
        public long committed_bytes;
        public long init_bytes;
        public long max_bytes;
    }

    public static class MemoryPoolInfo {
        public String name;
        public MemoryUsageInfo usage;
        public MemoryUsageInfo collection_usage;
    }

    public static class GcStats {
        public long total_collections;
        public double avg_time_ms;
        public double avg_frequency_per_sec;
    }

    public static class TpsStats {
        public double last_1m;
        public double last_5m;
        public double last_15m;
        public int target_tps;
    }

    public static class MsptStats {
        public RollingAvg last_1m;
        public RollingAvg last_5m;
        public int max_ideal_mspt;
    }

    public static class PingStats {
        public RollingAvg last_15m;
    }

    public static class RollingAvg {
        public double mean;
        public double max;
        public double min;
        public double median;
        public double percentile_95;
    }

    public static class CpuStats {
        public int threads;
        public CpuUsage process;
        public CpuUsage system;
        public String model_name;
    }

    public static class CpuUsage {
        public double last_1m;
        public double last_15m;
    }

    public static class MemoryPoolStats {
        public MemPool physical;
        public MemPool swap;
    }

    public static class MemPool {
        public long used_bytes;
        public long total_bytes;
    }

    public static class DiskStats {
        public long used_bytes;
        public long total_bytes;
    }

    public static class OsStats {
        public String arch;
        public String name;
        public String version;
    }

    public static class JavaStats {
        public String vendor;
        public String version;
        public String vendor_version;
        public String vm_args;
    }

    public static class JvmStats {
        public String name;
        public String vendor;
        public String version;
    }

    public static class WorldStats {
        public int total_entities;
        public Map<String, Integer> entity_counts;
        public List<WorldInfo> worlds;
    }

    public static class WorldInfo {
        public String name;
        public int total_entities;
    }

    public static class SourceInfo {
        public String name;
        public String version;
        public String author;
        public String description;
        public boolean builtin;
    }

    // ---- tree nodes ----

    public static class ThreadInfo {
        public String name;
        public List<Double> times;
        public List<TreeNode> root_nodes = new java.util.ArrayList<>();
    }

    public static class TreeNode {
        public String class_name;
        public String method_name;
        public String method_desc;
        public int line_number;
        public int parent_line_number;
        public List<Double> times;
        public double total_time_percent;
        public List<TreeNode> children;
    }

    // ---- time windows ----

    public static class TimeWindowInfo {
        public int tick_index;
        public int ticks;
        public double cpu_process;
        public double cpu_system;
        public double tps;
        public double mspt_median;
        public double mspt_max;
        public int players;
        public int entities;
        public int tile_entities;
        public int chunks;
        public long start_time_epoch_ms;
        public long end_time_epoch_ms;
        public int duration_ms;
    }
}
