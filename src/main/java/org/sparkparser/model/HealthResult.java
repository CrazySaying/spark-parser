package org.sparkparser.model;

import java.util.List;
import java.util.Map;

/**
 * Flattened server health report result.
 */
public class HealthResult implements ParsedData {
    public String file_type = "health";
    public String parser_version = "1.0.0";
    public Map<String, Object> summary;
    public HealthMetadata metadata;
    public List<SamplerResult.TimeWindowInfo> time_windows;

    @Override
    public String getFileType() { return file_type; }

    @Override
    public String getParserVersion() { return parser_version; }

    public static class HealthMetadata {
        public SamplerResult.Creator creator;
        public SamplerResult.PlatformInfo platform;
        public SamplerResult.PlatformStats platform_stats;
        public SamplerResult.SystemStats system_stats;
        public long generated_time_epoch_ms;
        public Map<String, String> server_configurations;
        public Map<String, SamplerResult.SourceInfo> sources;
    }
}
