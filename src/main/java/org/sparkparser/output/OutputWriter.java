package org.sparkparser.output;

import org.sparkparser.model.ParsedData;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface for output writers.
 */
public interface OutputWriter {
    void write(ParsedData data, OutputStream out) throws IOException;
}
