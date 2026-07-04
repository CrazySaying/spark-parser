package org.sparkparser.model;

import java.util.List;
import java.util.Map;

/**
 * Flattened activity log result.
 */
public class ActivityResult implements ParsedData {
    public String file_type = "activity";
    public String parser_version = "1.0.0";
    public Map<String, Object> summary;
    public List<ActivityEntry> activities;

    @Override
    public String getFileType() { return file_type; }

    @Override
    public String getParserVersion() { return parser_version; }

    public static class ActivityEntry {
        public String user_name;
        public String user_unique_id;
        public String user_type;
        public long time_epoch_ms;
        public String time_iso;
        public String activity_type;
        public String data_type;
        public String data_value;
    }
}
