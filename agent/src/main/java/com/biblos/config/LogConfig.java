package com.biblos.config;

import java.nio.file.Path;

public class LogConfig {

    private LogConfig() {
    }

    public static void configure(Path dbPath) {
        Path dbParent = dbPath.getParent();
        Path logDir = (dbParent != null) ? dbParent.resolve("logs") : Path.of("logs");
        System.setProperty("log.dir", logDir.toString());
    }
}
