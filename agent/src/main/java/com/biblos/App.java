package com.biblos;

import com.biblos.config.Config;
import com.biblos.config.ConfigException;
import com.biblos.config.DirectoryNotFoundException;
import com.biblos.infrastructure.DatabaseException;
import com.biblos.infrastructure.ScanException;
import com.biblos.pipeline.Pipeline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.PrintStream;
import java.nio.file.Path;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    private static volatile boolean cancelled = false;

    public static boolean isCancelled() {
        return cancelled;
    }

    public static void main(String[] args) {
        if (hasHelpFlag(args)) {
            printUsage(System.out);
            System.exit(0);
        }

        Config config;
        try {
            config = Config.fromArgs(args);
        } catch (DirectoryNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
            return;
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage(System.err);
            System.exit(1);
            return;
        }

        Path dbParent = config.dbPath().getParent();
        Path logDir = (dbParent != null) ? dbParent.resolve("logs") : Path.of("logs");
        System.setProperty("log.dir", logDir.toString());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cancelled = true;
            System.err.println("Cancelled by user");
        }));

        logger.info("Biblos Agent starting");
        logger.debug("root-dir={}, db-path={}, flow={}", config.rootDir(), config.dbPath(), config.flow());

        Pipeline pipeline = new Pipeline(config);
        int excluded = 0;
        try {
            switch (config.flow()) {
                case FOUNDATION -> excluded = pipeline.foundation();
                case RECONCILIATION -> excluded = pipeline.reconciliation();
                case MIGRATION -> pipeline.migration();
            }
        } catch (DatabaseException e) {
            logger.error("Database error: {}", e.getMessage());
            System.exit(5);
        } catch (ScanException e) {
            logger.error("Scan error: {}", e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }

        if (excluded > 0) {
            logger.warn("{} files excluded due to hash errors", excluded);
            System.exit(4);
        }
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
