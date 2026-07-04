package org.sparkparser.parser;

import me.lucko.spark.proto.SparkProtos;
import org.sparkparser.model.HealthResult;
import org.sparkparser.model.ParsedData;
import org.sparkparser.model.SamplerResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Parses health report protobuf files into HealthResult.
 * Health reports contain the same HealthData message whether from a saved file or upload.
 */
public class HealthParser implements FileParser {

    @Override
    public ParsedData parse(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        SparkProtos.HealthData proto = SparkProtos.HealthData.parseFrom(bytes);

        HealthResult result = new HealthResult();
        result.metadata = buildHealthMetadata(proto.getMetadata());

        // time window statistics
        List<SamplerResult.TimeWindowInfo> windows = new ArrayList<>();
        for (var entry : proto.getTimeWindowStatisticsMap().entrySet()) {
            windows.add(HeapParser.buildTimeWindow(entry.getKey(), entry.getValue()));
        }
        windows.sort(Comparator.comparingInt(w -> w.tick_index));
        result.time_windows = windows;

        // summary
        HealthResult.HealthMetadata meta = result.metadata;
        Map<String, Object> summary = new LinkedHashMap<>();
        if (meta.platform != null) {
            summary.put("platform_type", meta.platform.type);
            summary.put("server_name", meta.platform.name);
            summary.put("server_version", meta.platform.version);
            summary.put("minecraft_version", meta.platform.minecraft_version);
        }
        if (meta.platform_stats != null) {
            if (meta.platform_stats.tps != null) {
                summary.put("tps_last_1m", meta.platform_stats.tps.last_1m);
                summary.put("tps_last_5m", meta.platform_stats.tps.last_5m);
                summary.put("tps_last_15m", meta.platform_stats.tps.last_15m);
            }
            if (meta.platform_stats.mspt != null) {
                summary.put("mspt_median_last_1m", meta.platform_stats.mspt.last_1m.median);
                summary.put("mspt_max_last_1m", meta.platform_stats.mspt.last_1m.max);
            }
            summary.put("player_count", meta.platform_stats.player_count);
            summary.put("online_mode", meta.platform_stats.online_mode);
        }
        if (meta.system_stats != null && meta.system_stats.cpu != null && meta.system_stats.cpu.process != null) {
            summary.put("cpu_process_last_1m", meta.system_stats.cpu.process.last_1m);
        }
        summary.put("generated_time", Instant.ofEpochMilli(meta.generated_time_epoch_ms).toString());
        summary.put("time_windows_count", windows.size());
        result.summary = summary;

        return result;
    }

    private static HealthResult.HealthMetadata buildHealthMetadata(SparkProtos.HealthMetadata proto) {
        HealthResult.HealthMetadata md = new HealthResult.HealthMetadata();
        md.generated_time_epoch_ms = proto.getGeneratedTime();
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
        md.server_configurations = new LinkedHashMap<>(proto.getServerConfigurationsMap());
        md.sources = new LinkedHashMap<>();
        for (var entry : proto.getSourcesMap().entrySet()) {
            md.sources.put(entry.getKey(), HeapParser.buildSourceInfo(entry.getValue()));
        }
        return md;
    }
}
