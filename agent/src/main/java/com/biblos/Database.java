package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Database {

    private static final Logger logger = LogManager.getLogger(Database.class);

    private final Jdbi jdbi;

    private Database(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public static Database open(Path dbPath) {
        if (!Files.exists(dbPath)) {
            throw new DatabaseException("database file not found: " + dbPath);
        }
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        configureJdbi(jdbi);
        Database db = new Database(jdbi);
        db.validateIntegrity();
        return db;
    }

    public static Database create(Path dbPath) throws IOException {
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        configureJdbi(jdbi);
        Database db = new Database(jdbi);
        db.applyMigrations();
        return db;
    }

    private static void configureJdbi(Jdbi jdbi) {
        jdbi.installPlugin(new SQLitePlugin());
    }

    private void executePragmas(Handle handle) {
        handle.execute("PRAGMA foreign_keys = ON");
        handle.execute("PRAGMA busy_timeout = 5000");
    }

    private void validateIntegrity() {
        jdbi.useHandle(handle -> {
            executePragmas(handle);
            String result = handle.createQuery("PRAGMA quick_check")
                    .mapTo(String.class)
                    .one();
            if (!"ok".equals(result)) {
                throw new DatabaseException("database integrity check failed: " + result);
            }
            logger.debug("Database integrity check passed");
        });
    }

    private void applyMigrations() {
        jdbi.useHandle(this::executePragmas);

        try (InputStream is = getClass().getResourceAsStream("/db/migration/V001__initial_schema.sql")) {
            if (is == null) {
                throw new DatabaseException("migration file not found: V001__initial_schema.sql");
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            jdbi.useHandle(handle -> {
                executePragmas(handle);
                handle.execute(sql);
            });
            logger.info("Applied migration: V001__initial_schema.sql");
        } catch (IOException e) {
            throw new DatabaseException("failed to read migration file", e);
        }
    }

    public <T, X extends Exception> T withHandle(HandleCallback<T, X> callback) throws X {
        return jdbi.withHandle(handle -> {
            executePragmas(handle);
            return callback.withHandle(handle);
        });
    }

    @FunctionalInterface
    public interface HandleCallback<T, X extends Exception> {
        T withHandle(Handle handle) throws X;
    }
}
