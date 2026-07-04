package org.sparkparser.model;

import java.util.List;
import java.util.Map;

/**
 * Flattened heap summary result.
 */
public class HeapResult implements ParsedData {
    public String file_type = "heap";
    public String parser_version = "1.0.0";
    public Map<String, Object> summary;
    public HeapMetadata metadata;
    public List<HeapEntryInfo> entries;

    @Override
    public String getFileType() { return file_type; }

    @Override
    public String getParserVersion() { return parser_version; }

    public static class HeapMetadata {
        public SamplerResult.Creator creator;
        public SamplerResult.PlatformInfo platform;
        public SamplerResult.PlatformStats platform_stats;
        public SamplerResult.SystemStats system_stats;
        public long generated_time_epoch_ms;
        public Map<String, String> server_configurations;
        public Map<String, SamplerResult.SourceInfo> sources;
    }

    public static class HeapEntryInfo {
        public int order;
        public String type;
        public int instances;
        public long size_bytes;
        public double instance_percent;
        public double size_percent;
    }
}
