package com.biblos.config;

import java.nio.file.Path;

public class LogConfig {

    private LogConfig() {
    }

    public static Path logDirFor(Path dbPath) {
        Path dbParent = dbPath.getParent();
        return (dbParent != null) ? dbParent.resolve("logs") : Path.of("logs");
    }

    public static void configure(Path dbPath) {
        System.setProperty("log.dir", logDirFor(dbPath).toString());
    }
}
