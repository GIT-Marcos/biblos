package com.biblos;

import com.biblos.config.Config;
import com.biblos.config.ConfigException;
import com.biblos.config.DirectoryNotFoundException;
import com.biblos.config.ExitCode;

import java.io.PrintStream;

public class App {

    public static void main(String[] args) {
        if (hasHelpFlag(args)) {
            printUsage(System.out);
            System.exit(ExitCode.SUCCESS.getCode());
        }

        Config config = parseConfig(args);
        if (config == null) {
            return;
        }

        ApplicationRunner runner = new ApplicationRunner();
        ExitCode exitCode = runner.run(config);
        System.exit(exitCode.getCode());
    }

    private static Config parseConfig(String[] args) {
        try {
            return Config.fromArgs(args);
        } catch (DirectoryNotFoundException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(ExitCode.DIRECTORY_NOT_FOUND.getCode());
            return null;
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage(System.err);
            System.exit(ExitCode.CONFIG_ERROR.getCode());
            return null;
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
