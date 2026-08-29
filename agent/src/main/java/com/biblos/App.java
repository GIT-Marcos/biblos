package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    private static final Set<String> VALID_FLOWS = Set.of("foundation", "reconciliation", "migration");

    private static final Map<String, String> DEFAULTS = Map.of(
            "--flow", "reconciliation",
            "--max-depth", "10",
            "--batch-size", "50",
            "--timeout", "30"
    );

    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);

        if (parsed.containsKey("--help") || parsed.containsKey("-h")) {
            printUsage(System.out);
            System.exit(0);
        }

        String error = validateArgs(parsed);
        if (error != null) {
            System.err.println("Error: " + error);
            printUsage(System.err);
            System.exit(1);
        }

        String dbPath = parsed.get("--db-path");
        Path logDir = Path.of(dbPath).getParent().resolve("logs");
        System.setProperty("log.dir", logDir.toString());

        logger.info("Biblos Agent starting");
        logger.debug("root-dir={}, db-path={}, flow={}", parsed.get("--root-dir"), dbPath, parsed.get("--flow"));
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new java.util.HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-h") || arg.equals("--help")) {
                result.put("--help", "true");
                return result;
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

    private static String validateArgs(Map<String, String> parsed) {
        if (!parsed.containsKey("--root-dir")) {
            return "--root-dir is required";
        }
        if (!parsed.containsKey("--db-path")) {
            return "--db-path is required";
        }

        Path rootDir = Path.of(parsed.get("--root-dir"));
        if (!Files.isDirectory(rootDir)) {
            return "root directory not found: " + rootDir;
        }

        Path dbFile = Path.of(parsed.get("--db-path"));
        if (Files.exists(dbFile) && !Files.isRegularFile(dbFile)) {
            return "--db-path points to a directory, not a file: " + dbFile;
        }

        String flow = parsed.getOrDefault("--flow", DEFAULTS.get("--flow"));
        if (!VALID_FLOWS.contains(flow)) {
            return "invalid flow: " + flow + " (valid: foundation, reconciliation, migration)";
        }

        return null;
    }

    private static void printUsage(PrintStream out) {
        out.println("Usage: biblos-agent scan [options]");
        out.println();
        out.println("Required:");
        out.println("  --root-dir <path>    Root directory of the library");
        out.println("  --db-path <path>     Path to the .db file");
        out.println();
        out.println("Optional:");
        out.println("  --flow <flow>        foundation | reconciliation | migration (default: reconciliation)");
        out.println("  --max-depth <n>      Max scan depth (default: 10)");
        out.println("  --batch-size <n>     Batch size for operations (default: 50)");
        out.println("  --timeout <seconds>  Per-file timeout in seconds (default: 30)");
        out.println("  -h, --help           Show this help message");
    }
}
