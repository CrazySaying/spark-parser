package org.sparkparser.parser;

import org.sparkparser.model.ParsedData;
import org.sparkparser.util.FileTypeDetector;
import org.sparkparser.util.FileTypeDetector.FileType;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory that detects file type and dispatches to the correct parser.
 */
public class ParserFactory {

    public static ParsedData parse(Path path) throws IOException {
        FileType type = FileTypeDetector.detect(path);
        FileParser parser = switch (type) {
            case SAMPLER  -> new SamplerParser();
            case HEAP     -> new HeapParser();
            case HEALTH   -> new HealthParser();
            case ACTIVITY -> new ActivityParser();
        };
        return parser.parse(path);
    }
}
