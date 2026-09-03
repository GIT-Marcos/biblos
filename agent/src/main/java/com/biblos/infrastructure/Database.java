package com.biblos.infrastructure;

import com.biblos.domain.Source;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.io.IOException;
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

    // --- Lifecycle ---

    public static Database open(Path dbPath) {
        if (!Files.exists(dbPath)) {
            throw new DatabaseException("database file not found: " + dbPath);
        }
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        configureJdbi(jdbi);
        Database db = new Database(jdbi);
        new MigrationService().applyMigrations(jdbi);
        SchemaValidator validator = new SchemaValidator();
        validator.validateVersion(jdbi);
        validator.validateIntegrity(jdbi);
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
        new MigrationService().applyMigrations(jdbi);
        return db;
    }

    private static void configureJdbi(Jdbi jdbi) {
        jdbi.installPlugin(new SQLitePlugin());
    }

    public void validateIntegrity() {
        new SchemaValidator().validateIntegrity(jdbi);
    }

    // --- Handle access ---

    public <T, X extends Exception> T withHandle(HandleCallback<T, X> callback) throws X {
        return jdbi.withHandle(handle -> {
            executePragmas(handle);
            return callback.withHandle(handle);
        });
    }

    public <T, X extends Exception> T withTransaction(HandleCallback<T, X> callback) throws X {
        return jdbi.inTransaction(handle -> {
            executePragmas(handle);
            return callback.withHandle(handle);
        });
    }

    private static void executePragmas(Handle handle) {
        handle.execute("PRAGMA foreign_keys = ON");
        handle.execute("PRAGMA busy_timeout = 5000");
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

    public Source findByPathLower(String pathLower) {
        return withHandle(handle ->
                handle.createQuery("SELECT * FROM sources WHERE path_lower = ?")
                        .bind(0, pathLower)
                        .map(SOURCE_MAPPER)
                        .findOne()
                        .orElse(null)
        );
    }

    public long findOrCreateAuthor(String name) {
        if (name == null) {
            return 0;
        }
        return withHandle(handle -> findOrCreateAuthor(handle, name));
    }

    private long findOrCreateAuthor(Handle handle, String name) {
        handle.execute("INSERT OR IGNORE INTO authors(name) VALUES (?)", name);
        return handle.createQuery("SELECT id FROM authors WHERE name = ?")
                .bind(0, name)
                .mapTo(Long.class)
                .one();
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

    public void insertSourceBatch(List<SourceRecord> sources) {
        jdbi.useHandle(handle -> {
            executePragmas(handle);
            handle.useTransaction(tx -> {
                for (SourceRecord s : sources) {
                    tx.execute(
                            "INSERT INTO sources(name, path, path_lower, content_hash, file_format, author_id) " +
                                    "VALUES (?, ?, ?, ?, ?, ?)",
                            s.name(), s.path(), s.pathLower(), s.contentHash(),
                            s.fileFormat(), s.authorId() > 0 ? s.authorId() : null
                    );
                }
            });
        });
    }

    public void insertSourceBatch(Handle handle, List<SourceRecord> sources) {
        for (SourceRecord s : sources) {
            handle.execute(
                    "INSERT INTO sources(name, path, path_lower, content_hash, file_format, author_id) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    s.name(), s.path(), s.pathLower(), s.contentHash(),
                    s.fileFormat(), s.authorId() > 0 ? s.authorId() : null
            );
        }
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

    public void updateMetadata(long id, Integer year, String edition, String url) {
        withHandle(handle -> handle.execute(
                "UPDATE sources SET year = ?, edition = ?, url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                year, edition, url, id
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

    public void deleteSource(long id) {
        withHandle(handle -> handle.execute("DELETE FROM sources WHERE id = ?", id));
    }

    public List<String> findSourceTags(long sourceId) {
        return withHandle(handle ->
                handle.createQuery(
                                "SELECT t.name FROM tags t " +
                                        "JOIN source_tags st ON st.tag_id = t.id " +
                                        "WHERE st.source_id = ?")
                        .bind(0, sourceId)
                        .mapTo(String.class)
                        .list()
        );
    }

    public void addSourceTag(long sourceId, String tagName) {
        withHandle(handle -> {
            handle.execute("INSERT OR IGNORE INTO tags(name) VALUES (?)", tagName);
            long tagId = handle.createQuery("SELECT id FROM tags WHERE name = ?")
                    .bind(0, tagName)
                    .mapTo(Long.class)
                    .one();
            return handle.execute(
                    "INSERT OR IGNORE INTO source_tags(source_id, tag_id) VALUES (?, ?)",
                    sourceId, tagId
            );
        });
    }

    // --- Handle-level methods (for use inside transactions) ---

    public void updateHashBatch(Handle handle, List<Long> ids, List<String> hashes) {
        for (int i = 0; i < ids.size(); i++) {
            handle.execute(
                    "UPDATE sources SET content_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    hashes.get(i), ids.get(i)
            );
        }
    }

    public void softDeleteBatch(Handle handle, List<Long> ids) {
        for (long id : ids) {
            handle.execute(
                    "UPDATE sources SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    id
            );
        }
    }

    public void reactivateBatch(Handle handle, List<Long> ids) {
        for (long id : ids) {
            handle.execute(
                    "UPDATE sources SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    id
            );
        }
    }

    public Jdbi getJdbi() {
        return jdbi;
    }

    @Override
    public void close() {
        // Jdbi 3.x no implementa Closeable; el pool se gestiona internamente
    }

    @FunctionalInterface
    public interface HandleCallback<T, X extends Exception> {
        T withHandle(Handle handle) throws X;
    }
}
