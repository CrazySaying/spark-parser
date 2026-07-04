package org.sparkparser.output;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sparkparser.model.ParsedData;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Writes parsed data as pretty-printed JSON - the primary AI-friendly format.
 */
public class JsonOutputWriter implements OutputWriter {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeSpecialFloatingPointValues()
            .create();

    @Override
    public void write(ParsedData data, OutputStream out) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
            writer.flush();
        }
    }
}
