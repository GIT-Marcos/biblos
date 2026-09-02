package com.biblos.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Config(
        Path rootDir,
        Path dbPath,
        Flow flow,
        int maxDepth,
        int batchSize,
        int timeout
) {

    public enum Flow {
        FOUNDATION,
        RECONCILIATION,
        MIGRATION;

        public static Flow fromString(String value) {
            return valueOf(value.toUpperCase());
        }
    }

    public static Config fromArgs(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        return fromMap(parsed);
    }

    static Config fromMap(Map<String, String> parsed) {
        List<String> errors = new ArrayList<>();

        if (!parsed.containsKey("--root-dir")) {
            errors.add("--root-dir is required");
        }

        String rootDirRaw = parsed.get("--root-dir");
        if (rootDirRaw != null && rootDirRaw.isBlank()) {
            errors.add("--root-dir cannot be empty");
        }

        Flow flow;
        try {
            flow = Flow.fromString(parsed.getOrDefault("--flow", "reconciliation"));
        } catch (IllegalArgumentException e) {
            errors.add("invalid flow: " + parsed.get("--flow") + " (valid: foundation, reconciliation, migration)");
            flow = null;
        }

        String dbPathRaw = parsed.get("--db-path");

        if (flow == Flow.FOUNDATION) {
            if (dbPathRaw == null || dbPathRaw.isBlank()) {
                if (rootDirRaw != null && !rootDirRaw.isBlank()) {
                    dbPathRaw = Path.of(rootDirRaw).resolve("biblos.db").toString();
                }
            }
        } else {
            if (!parsed.containsKey("--db-path")) {
                errors.add("--db-path is required");
            }
        }

        if (dbPathRaw != null && dbPathRaw.isBlank()) {
            errors.add("--db-path cannot be empty");
        }

        int maxDepth = parsePositiveInt(parsed, "--max-depth", 10, errors);
        int batchSize = parsePositiveInt(parsed, "--batch-size", 50, errors);
        int timeout = parsePositiveInt(parsed, "--timeout", 30, errors);

        if (!errors.isEmpty()) {
            throw new ConfigException(String.join("; ", errors));
        }

        Path rootDir = Path.of(rootDirRaw);
        if (!Files.isDirectory(rootDir)) {
            throw new DirectoryNotFoundException("root directory not found: " + rootDir);
        }

        Path dbPath = Path.of(dbPathRaw);
        if (Files.exists(dbPath) && !Files.isRegularFile(dbPath)) {
            errors.add("--db-path points to a directory, not a file: " + dbPath);
        }
        if (flow != Flow.FOUNDATION && !Files.exists(dbPath)) {
            errors.add("--db-path does not exist: " + dbPath + " (required for " + flow.name().toLowerCase() + ")");
        }

        if (!errors.isEmpty()) {
            throw new ConfigException(String.join("; ", errors));
        }

        return new Config(rootDir, dbPath, flow, maxDepth, batchSize, timeout);
    }

    private static int parsePositiveInt(Map<String, String> parsed, String key, int defaultValue, List<String> errors) {
        String raw = parsed.get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                errors.add(key + " must be > 0, got: " + value);
                return defaultValue;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(key + " must be a number, got: " + raw);
            return defaultValue;
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        var result = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            int eqIndex = arg.indexOf('=');
            if (eqIndex > 0 && arg.startsWith("--")) {
                String key = arg.substring(0, eqIndex);
                String value = arg.substring(eqIndex + 1);
                result.put(key, value);
                continue;
            }

            if (arg.startsWith("--")) {
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    result.put(arg, args[++i]);
                } else {
                    result.put(arg, "true");
                }
            }
        }
        return result;
    }
}
