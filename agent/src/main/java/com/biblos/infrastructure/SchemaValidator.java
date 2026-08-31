package com.biblos.infrastructure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Jdbi;

import java.nio.file.Files;
import java.nio.file.Path;

public class SchemaValidator {

    private static final Logger logger = LogManager.getLogger(SchemaValidator.class);

    static final int AGENT_VERSION = 1;

    public void validateVersion(Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute("PRAGMA busy_timeout = 5000");

            boolean hasVersionTable = handle.createQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")
                    .mapTo(String.class)
                    .findOne()
                    .isPresent();

            if (!hasVersionTable) {
                logger.debug("No schema_version table found, assuming compatible version");
                return;
            }

            int dbVersion = handle.createQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")
                    .mapTo(Integer.class)
                    .one();

            if (dbVersion > AGENT_VERSION) {
                throw new DatabaseException(
                        "database version V" + dbVersion + " is newer than agent version V" +
                                AGENT_VERSION + ". Please upgrade the agent before opening this database.");
            }

            logger.debug("Database version V{} is compatible with agent V{}", dbVersion, AGENT_VERSION);
        });
    }

    public void validateIntegrity(Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute("PRAGMA busy_timeout = 5000");

            String result = handle.createQuery("PRAGMA quick_check")
                    .mapTo(String.class)
                    .one();
            if (!"ok".equals(result)) {
                throw new DatabaseException("database integrity check failed: " + result);
            }
            logger.debug("Database integrity check passed");
        });
    }

    public int getSchemaVersion(Jdbi jdbi) {
        return jdbi.withHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute("PRAGMA busy_timeout = 5000");

            boolean hasVersionTable = handle.createQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")
                    .mapTo(String.class)
                    .findOne()
                    .isPresent();

            if (!hasVersionTable) {
                return 0;
            }

            return handle.createQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")
                    .mapTo(Integer.class)
                    .one();
        });
    }

    public static int getSchemaVersion(Path dbPath) {
        if (!Files.exists(dbPath)) {
            return 0;
        }
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        jdbi.installPlugin(new org.jdbi.v3.sqlite3.SQLitePlugin());
        return jdbi.withHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute("PRAGMA busy_timeout = 5000");

            boolean hasVersionTable = handle.createQuery(
                            "SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")
                    .mapTo(String.class)
                    .findOne()
                    .isPresent();

            if (!hasVersionTable) {
                return 0;
            }

            return handle.createQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")
                    .mapTo(Integer.class)
                    .one();
        });
    }
}
