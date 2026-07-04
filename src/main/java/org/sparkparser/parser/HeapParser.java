package org.sparkparser.parser;

import me.lucko.spark.proto.SparkHeapProtos;
import me.lucko.spark.proto.SparkProtos;
import org.sparkparser.model.HeapResult;
import org.sparkparser.model.ParsedData;
import org.sparkparser.model.SamplerResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses .sparkheap protobuf files into HeapResult.
 */
public class HeapParser implements FileParser {

    @Override
    public ParsedData parse(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        SparkHeapProtos.HeapData proto = SparkHeapProtos.HeapData.parseFrom(bytes);

        HeapResult result = new HeapResult();
        result.metadata = buildMetadata(proto.getMetadata());

        List<HeapResult.HeapEntryInfo> entries = new ArrayList<>();
        long totalInstances = 0;
        long totalSize = 0;

        for (SparkHeapProtos.HeapEntry entry : proto.getEntriesList()) {
            totalInstances += entry.getInstances();
            totalSize += entry.getSize();
        }

        for (SparkHeapProtos.HeapEntry entry : proto.getEntriesList()) {
            HeapResult.HeapEntryInfo info = new HeapResult.HeapEntryInfo();
            info.order = entry.getOrder();
            info.type = entry.getType();
            info.instances = entry.getInstances();
            info.size_bytes = entry.getSize();
            info.instance_percent = totalInstances > 0 ? (100.0 * entry.getInstances() / totalInstances) : 0;
            info.size_percent = totalSize > 0 ? (100.0 * entry.getSize() / totalSize) : 0;
            entries.add(info);
        }

        // sort by size descending
        entries.sort((a, b) -> Long.compare(b.size_bytes, a.size_bytes));

        // top 5 by size
        List<Map<String, Object>> top5BySize = new ArrayList<>();
        for (int i = 0; i < Math.min(5, entries.size()); i++) {
            HeapResult.HeapEntryInfo e = entries.get(i);
            top5BySize.add(Map.of("type", e.type, "size_bytes", e.size_bytes, "size_percent", Math.round(e.size_percent * 100.0) / 100.0));
        }

        result.summary = new LinkedHashMap<>();
        result.summary.put("total_types", entries.size());
        result.summary.put("total_instances", totalInstances);
        result.summary.put("total_size_bytes", totalSize);
        result.summary.put("total_size_mb", Math.round(totalSize / 1048576.0 * 100.0) / 100.0);
        result.summary.put("top_5_by_size", top5BySize);
        result.entries = entries;

        return result;
    }

    static HeapResult.HeapMetadata buildMetadata(SparkHeapProtos.HeapMetadata meta) {
        HeapResult.HeapMetadata md = new HeapResult.HeapMetadata();
        md.generated_time_epoch_ms = meta.getGeneratedTime();
        if (meta.hasCreator()) {
            md.creator = buildCreator(meta.getCreator());
        }
        if (meta.hasPlatformMetadata()) {
            md.platform = buildPlatformInfo(meta.getPlatformMetadata());
        }
        if (meta.hasPlatformStatistics()) {
            md.platform_stats = buildPlatformStats(meta.getPlatformStatistics());
        }
        if (meta.hasSystemStatistics()) {
            md.system_stats = buildSystemStats(meta.getSystemStatistics());
        }
        md.server_configurations = new LinkedHashMap<>(meta.getServerConfigurationsMap());
        md.sources = new LinkedHashMap<>();
        for (var entry : meta.getSourcesMap().entrySet()) {
            md.sources.put(entry.getKey(), buildSourceInfo(entry.getValue()));
        }
        return md;
    }

    // ---- shared builders (also used by HealthParser and SamplerParser) ----

    static SamplerResult.Creator buildCreator(SparkProtos.CommandSenderMetadata proto) {
        SamplerResult.Creator c = new SamplerResult.Creator();
        c.name = proto.getName();
        c.unique_id = proto.getUniqueId();
        c.type = proto.getType().name();
        return c;
    }

    static SamplerResult.PlatformInfo buildPlatformInfo(SparkProtos.PlatformMetadata proto) {
        SamplerResult.PlatformInfo p = new SamplerResult.PlatformInfo();
        p.type = proto.getType().name();
        p.name = proto.getName();
        p.version = proto.getVersion();
        p.minecraft_version = proto.getMinecraftVersion();
        p.spark_version = proto.getSparkVersion();
        p.brand = proto.getBrand();
        return p;
    }

    static SamplerResult.PlatformStats buildPlatformStats(SparkProtos.PlatformStatistics proto) {
        SamplerResult.PlatformStats ps = new SamplerResult.PlatformStats();
        ps.uptime_seconds = proto.getUptime();

        if (proto.hasMemory()) {
            ps.memory = new SamplerResult.MemoryStats();
            ps.memory.heap = buildMemoryUsage(proto.getMemory().getHeap());
            ps.memory.non_heap = buildMemoryUsage(proto.getMemory().getNonHeap());
            ps.memory.pools = new ArrayList<>();
            for (SparkProtos.PlatformStatistics.Memory.MemoryPool pool : proto.getMemory().getPoolsList()) {
                SamplerResult.MemoryPoolInfo pi = new SamplerResult.MemoryPoolInfo();
                pi.name = pool.getName();
                pi.usage = buildMemoryUsage(pool.getUsage());
                pi.collection_usage = buildMemoryUsage(pool.getCollectionUsage());
                ps.memory.pools.add(pi);
            }
        }

        ps.gc = new LinkedHashMap<>();
        for (var entry : proto.getGcMap().entrySet()) {
            ps.gc.put(entry.getKey(), buildPlatformGc(entry.getValue()));
        }

        if (proto.hasTps()) {
            ps.tps = new SamplerResult.TpsStats();
            ps.tps.last_1m = proto.getTps().getLast1M();
            ps.tps.last_5m = proto.getTps().getLast5M();
            ps.tps.last_15m = proto.getTps().getLast15M();
            ps.tps.target_tps = proto.getTps().getGameTargetTps();
        }

        if (proto.hasMspt()) {
            ps.mspt = new SamplerResult.MsptStats();
            ps.mspt.last_1m = buildRollingAvg(proto.getMspt().getLast1M());
            ps.mspt.last_5m = buildRollingAvg(proto.getMspt().getLast5M());
            ps.mspt.max_ideal_mspt = proto.getMspt().getGameMaxIdealMspt();
        }

        if (proto.hasPing()) {
            ps.ping = new SamplerResult.PingStats();
            ps.ping.last_15m = buildRollingAvg(proto.getPing().getLast15M());
        }

        ps.player_count = proto.getPlayerCount();
        ps.online_mode = proto.getOnlineMode().name();

        if (proto.hasWorld()) {
            ps.world = new SamplerResult.WorldStats();
            ps.world.total_entities = proto.getWorld().getTotalEntities();
            ps.world.entity_counts = new LinkedHashMap<>(proto.getWorld().getEntityCountsMap());
            ps.world.worlds = new ArrayList<>();
            for (SparkProtos.WorldStatistics.World world : proto.getWorld().getWorldsList()) {
                SamplerResult.WorldInfo wi = new SamplerResult.WorldInfo();
                wi.name = world.getName();
                wi.total_entities = world.getTotalEntities();
                ps.world.worlds.add(wi);
            }
        }

        return ps;
    }

    static SamplerResult.SystemStats buildSystemStats(SparkProtos.SystemStatistics proto) {
        SamplerResult.SystemStats ss = new SamplerResult.SystemStats();
        ss.uptime_seconds = proto.getUptime();

        if (proto.hasCpu()) {
            ss.cpu = new SamplerResult.CpuStats();
            ss.cpu.threads = proto.getCpu().getThreads();
            ss.cpu.model_name = proto.getCpu().getModelName();
            if (proto.getCpu().hasProcessUsage()) {
                ss.cpu.process = new SamplerResult.CpuUsage();
                ss.cpu.process.last_1m = proto.getCpu().getProcessUsage().getLast1M();
                ss.cpu.process.last_15m = proto.getCpu().getProcessUsage().getLast15M();
            }
            if (proto.getCpu().hasSystemUsage()) {
                ss.cpu.system = new SamplerResult.CpuUsage();
                ss.cpu.system.last_1m = proto.getCpu().getSystemUsage().getLast1M();
                ss.cpu.system.last_15m = proto.getCpu().getSystemUsage().getLast15M();
            }
        }

        if (proto.hasMemory()) {
            ss.memory = new SamplerResult.MemoryPoolStats();
            ss.memory.physical = buildMemPool(proto.getMemory().getPhysical());
            ss.memory.swap = buildMemPool(proto.getMemory().getSwap());
        }

        ss.gc = new LinkedHashMap<>();
        for (var entry : proto.getGcMap().entrySet()) {
            ss.gc.put(entry.getKey(), buildSystemGc(entry.getValue()));
        }

        if (proto.hasDisk()) {
            ss.disk = new SamplerResult.DiskStats();
            ss.disk.used_bytes = proto.getDisk().getUsed();
            ss.disk.total_bytes = proto.getDisk().getTotal();
        }

        if (proto.hasOs()) {
            ss.os = new SamplerResult.OsStats();
            ss.os.arch = proto.getOs().getArch();
            ss.os.name = proto.getOs().getName();
            ss.os.version = proto.getOs().getVersion();
        }

        if (proto.hasJava()) {
            ss.java = new SamplerResult.JavaStats();
            ss.java.vendor = proto.getJava().getVendor();
            ss.java.version = proto.getJava().getVersion();
            ss.java.vendor_version = proto.getJava().getVendorVersion();
            ss.java.vm_args = proto.getJava().getVmArgs();
        }

        if (proto.hasJvm()) {
            ss.jvm = new SamplerResult.JvmStats();
            ss.jvm.name = proto.getJvm().getName();
            ss.jvm.vendor = proto.getJvm().getVendor();
            ss.jvm.version = proto.getJvm().getVersion();
        }

        return ss;
    }

    static SamplerResult.TimeWindowInfo buildTimeWindow(int tickIndex, SparkProtos.WindowStatistics ws) {
        SamplerResult.TimeWindowInfo tw = new SamplerResult.TimeWindowInfo();
        tw.tick_index = tickIndex;
        tw.ticks = ws.getTicks();
        tw.cpu_process = ws.getCpuProcess();
        tw.cpu_system = ws.getCpuSystem();
        tw.tps = ws.getTps();
        tw.mspt_median = ws.getMsptMedian();
        tw.mspt_max = ws.getMsptMax();
        tw.players = ws.getPlayers();
        tw.entities = ws.getEntities();
        tw.tile_entities = ws.getTileEntities();
        tw.chunks = ws.getChunks();
        tw.start_time_epoch_ms = ws.getStartTime();
        tw.end_time_epoch_ms = ws.getEndTime();
        tw.duration_ms = ws.getDuration();
        return tw;
    }

    static SamplerResult.SourceInfo buildSourceInfo(SparkProtos.PluginOrModMetadata proto) {
        SamplerResult.SourceInfo si = new SamplerResult.SourceInfo();
        si.name = proto.getName();
        si.version = proto.getVersion();
        si.author = proto.getAuthor();
        si.description = proto.getDescription();
        si.builtin = proto.getBuiltin();
        return si;
    }

    // ---- private helpers ----

    private static SamplerResult.MemoryUsageInfo buildMemoryUsage(SparkProtos.PlatformStatistics.Memory.MemoryUsage proto) {
        SamplerResult.MemoryUsageInfo u = new SamplerResult.MemoryUsageInfo();
        u.used_bytes = proto.getUsed();
        u.committed_bytes = proto.getCommitted();
        u.init_bytes = proto.getInit();
        u.max_bytes = proto.getMax();
        return u;
    }

    private static SamplerResult.GcStats buildPlatformGc(SparkProtos.PlatformStatistics.Gc proto) {
        SamplerResult.GcStats g = new SamplerResult.GcStats();
        g.total_collections = proto.getTotal();
        g.avg_time_ms = proto.getAvgTime();
        g.avg_frequency_per_sec = proto.getAvgFrequency();
        return g;
    }

    private static SamplerResult.GcStats buildSystemGc(SparkProtos.SystemStatistics.Gc proto) {
        SamplerResult.GcStats g = new SamplerResult.GcStats();
        g.total_collections = proto.getTotal();
        g.avg_time_ms = proto.getAvgTime();
        g.avg_frequency_per_sec = proto.getAvgFrequency();
        return g;
    }

    private static SamplerResult.RollingAvg buildRollingAvg(SparkProtos.RollingAverageValues proto) {
        SamplerResult.RollingAvg ra = new SamplerResult.RollingAvg();
        ra.mean = proto.getMean();
        ra.max = proto.getMax();
        ra.min = proto.getMin();
        ra.median = proto.getMedian();
        ra.percentile_95 = proto.getPercentile95();
        return ra;
    }

    private static SamplerResult.MemPool buildMemPool(SparkProtos.SystemStatistics.Memory.MemoryPool proto) {
        SamplerResult.MemPool mp = new SamplerResult.MemPool();
        mp.used_bytes = proto.getUsed();
        mp.total_bytes = proto.getTotal();
        return mp;
    }
}
