package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.List;
import java.util.Locale;

public class Database implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(Database.class);

    private static final RowMapper<Source> SOURCE_MAPPER = (ResultSet rs, StatementContext ctx) -> new Source(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("path"),
            rs.getString("path_lower"),
            rs.getString("content_hash"),
            rs.getString("file_format"),
            rs.getObject("author_id") != null ? rs.getLong("author_id") : null,
            rs.getObject("year") != null ? rs.getInt("year") : null,
            rs.getString("edition"),
            rs.getString("url"),
            rs.getString("created_at"),
            rs.getString("updated_at"),
            rs.getString("deleted_at")
    );

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

    // --- Queries ---

    public List<Source> findAll() {
        return withHandle(handle ->
                handle.createQuery("SELECT * FROM sources")
                        .map(SOURCE_MAPPER)
                        .list()
        );
    }

    public List<Source> findByHash(String contentHash) {
        return withHandle(handle ->
                handle.createQuery("SELECT * FROM sources WHERE content_hash = ?")
                        .bind(0, contentHash)
                        .map(SOURCE_MAPPER)
                        .list()
        );
    }

    public long findOrCreateAuthor(String name) {
        if (name == null) {
            return 0;
        }
        return withHandle(handle -> {
            handle.execute("INSERT OR IGNORE INTO authors(name) VALUES (?)", name);
            return handle.createQuery("SELECT id FROM authors WHERE name = ?")
                    .bind(0, name)
                    .mapTo(Long.class)
                    .one();
        });
    }

    public void insertSource(String name, String path, String contentHash, String fileFormat, long authorId) {
        String pathLower = path.toLowerCase(Locale.ROOT);
        withHandle(handle -> handle.execute(
                "INSERT INTO sources(name, path, path_lower, content_hash, file_format, author_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                name, path, pathLower, contentHash, fileFormat,
                authorId > 0 ? authorId : null
        ));
    }

    public void updatePath(long id, String newPath, String newPathLower) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET path = ?, path_lower = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                newPath, newPathLower, id
        ));
    }

    public void updateHash(long id, String newHash) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET content_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                newHash, id
        ));
    }

    public void updateAuthor(long id, long authorId) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET author_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                authorId > 0 ? authorId : null, id
        ));
    }

    public void reactivate(long id) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                id
        ));
    }

    public void softDelete(long id) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                id
        ));
    }

    public Source findByPathLower(String pathLower) {
        return withHandle(handle ->
                handle.createQuery("SELECT * FROM sources WHERE path_lower = ?")
                        .bind(0, pathLower)
                        .map(SOURCE_MAPPER)
                        .findOne()
                        .orElse(null)
        );
    }

    public void updateMetadata(long id, Integer year, String edition, String url) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET year = ?, edition = ?, url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                year, edition, url, id
        ));
    }

    public void deleteSource(long id) {
        withHandle(handle -> handle.execute("DELETE FROM sources WHERE id = ?", id));
    }

    @Override
    public void close() {
        // JDBI manages its own connection pool; JVM exits after CLI run
    }

    @FunctionalInterface
    public interface HandleCallback<T, X extends Exception> {
        T withHandle(Handle handle) throws X;
    }
}
