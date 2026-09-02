package com.biblos;

import com.biblos.config.Config;
import com.biblos.config.ExitCode;
import com.biblos.config.LogConfig;
import com.biblos.infrastructure.DatabaseException;
import com.biblos.infrastructure.ScanException;
import com.biblos.pipeline.Pipeline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Signal;

public class ApplicationRunner {

    private static final Logger logger = LogManager.getLogger(ApplicationRunner.class);

    private volatile boolean cancelled = false;

    public boolean isCancelled() {
        return cancelled;
    }

    public ExitCode run(Config config) {
        LogConfig.configure(config.dbPath());
        registerShutdownHook();

        logger.info("Biblos Agent starting");
        logger.debug("root-dir={}, db-path={}, flow={}", config.rootDir(), config.dbPath(), config.flow());

        try {
            return executePipeline(config);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            return ExitCode.CONFIG_ERROR;
        }
    }

    private ExitCode executePipeline(Config config) {
        Pipeline pipeline = new Pipeline(config, this::isCancelled);
        int excluded = 0;

        try {
            switch (config.flow()) {
                case FOUNDATION -> excluded = pipeline.foundation();
                case RECONCILIATION -> excluded = pipeline.reconciliation();
                case MIGRATION -> pipeline.migration();
            }
        } catch (DatabaseException e) {
            logger.error("Database error: {}", e.getMessage());
            return ExitCode.DATABASE_ERROR;
        } catch (ScanException e) {
            logger.error("Scan error: {}", e.getMessage());
            return ExitCode.SCAN_ERROR;
        }

        if (excluded > 0) {
            logger.warn("{} files excluded due to hash errors", excluded);
            return ExitCode.HASH_ERROR;
        }

        return ExitCode.SUCCESS;
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cancelled = true;
        }));
    }
}
