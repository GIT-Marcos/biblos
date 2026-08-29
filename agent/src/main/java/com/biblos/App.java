package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        if (hasHelpFlag(args)) {
            printUsage(System.out);
            System.exit(0);
        }

        Config config;
        try {
            config = Config.fromArgs(args);
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage(System.err);
            System.exit(1);
            return;
        }

        System.setProperty("log.dir", config.dbPath().getParent().resolve("logs").toString());

        logger.info("Biblos Agent starting");
        logger.debug("root-dir={}, db-path={}, flow={}", config.rootDir(), config.dbPath(), config.flow());
    }

    private static boolean hasHelpFlag(String[] args) {
        for (String arg : args) {
            if (arg.equals("-h") || arg.equals("--help")) {
                return true;
            }
        }
        return false;
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
