package org.sparkparser.model;

/**
 * Common interface for all parsed data types.
 */
public interface ParsedData {
    String getFileType();
    String getParserVersion();
}
