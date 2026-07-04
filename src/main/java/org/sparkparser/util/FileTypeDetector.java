package org.sparkparser.util;

import java.nio.file.Path;

/**
 * Detects file type from file extension.
 */
public class FileTypeDetector {

    public enum FileType {
        SAMPLER,
        HEAP,
        HEALTH,
        ACTIVITY
    }

    public static FileType detect(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".sparkprofile")) return FileType.SAMPLER;
        if (name.endsWith(".sparkheap")) return FileType.HEAP;
        if (name.endsWith(".sparkhealth")) return FileType.HEALTH;
        if (name.equals("activity.json") || (name.endsWith(".json") && name.contains("activity"))) return FileType.ACTIVITY;

        throw new IllegalArgumentException(
            "Cannot determine file type from: " + name + "\n" +
            "Expected: .sparkprofile, .sparkheap, .sparkhealth, or activity.json"
        );
    }
}
