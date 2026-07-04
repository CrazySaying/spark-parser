package org.sparkparser.parser;

import org.sparkparser.model.ParsedData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Common interface for all file parsers.
 */
public interface FileParser {
    ParsedData parse(Path path) throws IOException;
}
