package org.sparkparser.parser;

import com.google.gson.*;
import org.sparkparser.model.ActivityResult;
import org.sparkparser.model.ParsedData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Parses activity.json files into ActivityResult.
 */
public class ActivityParser implements FileParser {

    private static final DateTimeFormatter ISO_FORMAT =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("UTC"));

    @Override
    public ParsedData parse(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();

        List<ActivityResult.ActivityEntry> entries = new ArrayList<>();
        Set<String> users = new LinkedHashSet<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;

        for (JsonElement el : array) {
            JsonObject obj = el.getAsJsonObject();
            ActivityResult.ActivityEntry entry = new ActivityResult.ActivityEntry();

            // user
            JsonObject user = obj.getAsJsonObject("user");
            entry.user_name = str(user, "name");
            entry.user_unique_id = str(user, "uniqueId");
            entry.user_type = str(user, "type");
            users.add(entry.user_name);

            // time
            entry.time_epoch_ms = obj.get("time").getAsLong();
            entry.time_iso = Instant.ofEpochMilli(entry.time_epoch_ms).toString();
            if (entry.time_epoch_ms < minTime) minTime = entry.time_epoch_ms;
            if (entry.time_epoch_ms > maxTime) maxTime = entry.time_epoch_ms;

            // activity type
            entry.activity_type = str(obj, "type");
            typeCounts.merge(entry.activity_type, 1, Integer::sum);

            // data
            JsonObject data = obj.getAsJsonObject("data");
            entry.data_type = str(data, "type");
            entry.data_value = str(data, "value");

            entries.add(entry);
        }

        // sort by time descending (most recent first)
        entries.sort((a, b) -> Long.compare(b.time_epoch_ms, a.time_epoch_ms));

        ActivityResult result = new ActivityResult();
        result.summary = new LinkedHashMap<>();
        result.summary.put("total_activities", entries.size());
        result.summary.put("unique_users", users.size());
        result.summary.put("user_names", new ArrayList<>(users));
        result.summary.put("type_counts", typeCounts);
        if (minTime != Long.MAX_VALUE) {
            result.summary.put("first_activity", Instant.ofEpochMilli(minTime).toString());
            result.summary.put("last_activity", Instant.ofEpochMilli(maxTime).toString());
        }
        result.activities = entries;
        return result;
    }

    private static String str(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : null;
    }
}
