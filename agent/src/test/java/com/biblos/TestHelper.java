package com.biblos;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class TestHelper {

    private TestHelper() {
    }

    public static void applySchema(Path dbPath) {
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        jdbi.installPlugin(new SQLitePlugin());
        jdbi.useHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            try (InputStream is = TestHelper.class.getResourceAsStream("/db/migration/V001__initial_schema.sql")) {
                String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty()) {
                        handle.execute(trimmed);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read V001__initial_schema.sql", e);
            }
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT    NOT NULL,
                        applied_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            handle.execute("INSERT INTO schema_version(version, description) VALUES (?, ?)", 1, "V001__initial_schema");
        });
    }
}
