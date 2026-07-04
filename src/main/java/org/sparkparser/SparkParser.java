package org.sparkparser;

import org.sparkparser.model.ParsedData;
import org.sparkparser.output.JsonOutputWriter;
import org.sparkparser.output.MarkdownOutputWriter;
import org.sparkparser.output.OutputWriter;
import org.sparkparser.output.TextOutputWriter;
import org.sparkparser.parser.ParserFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * spark-parser — converts Spark profiler output files into AI-friendly formats.
 *
 * Usage: java -jar spark-parser.jar <input-file> [--format json|markdown|text] [--output <file>]
 */
public class SparkParser {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            printUsage();
            System.exit(0);
        }

        // Parse arguments
        Path inputFile = null;
        String format = "json";
        Path outputFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--format":
                    if (i + 1 < args.length) {
                        format = args[++i].toLowerCase();
                        if (!format.equals("json") && !format.equals("markdown") && !format.equals("text")) {
                            System.err.println("Error: Unknown format '" + format + "'. Valid: json, markdown, text");
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: --format requires a value");
                        System.exit(1);
                    }
                    break;
                case "--output":
                    if (i + 1 < args.length) {
                        outputFile = Paths.get(args[++i]);
                    } else {
                        System.err.println("Error: --output requires a file path");
                        System.exit(1);
                    }
                    break;
                default:
                    if (inputFile == null) {
                        inputFile = Paths.get(args[i]);
                    } else {
                        System.err.println("Error: Unexpected argument: " + args[i]);
                        System.exit(1);
                    }
                    break;
            }
        }

        if (inputFile == null) {
            System.err.println("Error: No input file specified");
            printUsage();
            System.exit(1);
        }

        if (!Files.exists(inputFile)) {
            System.err.println("Error: File not found: " + inputFile);
            System.exit(1);
        }

        if (!Files.isReadable(inputFile)) {
            System.err.println("Error: Cannot read file: " + inputFile);
            System.exit(1);
        }

        // Parse
        ParsedData data;
        try {
            data = ParserFactory.parse(inputFile);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(3);
            return; // unreachable but keeps compiler happy
        } catch (IOException e) {
            System.err.println("Error: Failed to read file: " + e.getMessage());
            System.exit(2);
            return;
        } catch (Exception e) {
            System.err.println("Error: Failed to parse as " + guessType(inputFile) + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        // Write output
        OutputWriter writer = switch (format) {
            case "markdown" -> new MarkdownOutputWriter();
            case "text" -> new TextOutputWriter();
            default -> new JsonOutputWriter();
        };

        try {
            if (outputFile != null) {
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
                    writer.write(data, out);
                }
                System.err.println("Output written to: " + outputFile);
            } else {
                writer.write(data, System.out);
                System.out.flush();
            }
        } catch (IOException e) {
            System.err.println("Error: Failed to write output: " + e.getMessage());
            System.exit(4);
        }

        // Print a brief summary to stderr
        printSummary(data);
    }

    private static void printUsage() {
        System.out.println("spark-parser — Parse Spark profiler output files into AI-friendly formats");
        System.out.println();
        System.out.println("Usage: java -jar spark-parser.jar <input-file> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <input-file>         Path to a Spark output file");
        System.out.println("                       (.sparkprofile, .sparkheap, .sparkhealth, activity.json)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --format <fmt>       Output format: json (default), markdown, text");
        System.out.println("  --output <path>      Write output to file instead of stdout");
        System.out.println("  --help, -h           Show this help");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar spark-parser.jar profile.sparkprofile");
        System.out.println("  java -jar spark-parser.jar profile.sparkprofile --format markdown --output report.md");
        System.out.println("  java -jar spark-parser.jar heap.sparkheap --format text");
    }

    private static String guessType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".sparkprofile")) return "sampler profile";
        if (name.endsWith(".sparkheap")) return "heap summary";
        if (name.endsWith(".sparkhealth")) return "health report";
        if (name.contains("activity")) return "activity log";
        return "Spark data";
    }

    private static void printSummary(ParsedData data) {
        String type = data.getFileType();
        switch (type) {
            case "sampler" -> {
                org.sparkparser.model.SamplerResult r = (org.sparkparser.model.SamplerResult) data;
                int threads = r.threads != null ? r.threads.size() : 0;
                int nodes = 0;
                if (r.summary != null && r.summary.get("total_stack_nodes") instanceof Number n) {
                    nodes = n.intValue();
                }
                System.err.println("Parsed sampler profile: " + threads + " threads, " + nodes + " stack nodes.");
            }
            case "heap" -> {
                org.sparkparser.model.HeapResult r = (org.sparkparser.model.HeapResult) data;
                int entryCount = r.entries != null ? r.entries.size() : 0;
                long totalSize = 0;
                if (r.summary != null && r.summary.get("total_size_bytes") instanceof Number n) {
                    totalSize = n.longValue();
                }
                System.err.println("Parsed heap summary: " + entryCount + " types, " + formatBytes(totalSize) + " total.");
            }
            case "health" -> {
                org.sparkparser.model.HealthResult r = (org.sparkparser.model.HealthResult) data;
                int windows = r.time_windows != null ? r.time_windows.size() : 0;
                System.err.println("Parsed health report: " + windows + " time windows.");
            }
            case "activity" -> {
                org.sparkparser.model.ActivityResult r = (org.sparkparser.model.ActivityResult) data;
                int count = r.activities != null ? r.activities.size() : 0;
                System.err.println("Parsed activity log: " + count + " entries.");
            }
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }
}
