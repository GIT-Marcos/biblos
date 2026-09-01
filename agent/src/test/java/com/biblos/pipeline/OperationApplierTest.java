package com.biblos.pipeline;

import com.biblos.config.Config;
import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.Database;
import com.biblos.infrastructure.FileScanner;
import com.biblos.infrastructure.ScannedFile;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DisplayName("OperationApplier")
class OperationApplierTest {

    @TempDir
    Path tempDir;

    private Database db;
    private OperationApplier applier;
    private Config config;

    @BeforeEach
    void setUp() throws IOException {
        Path dbPath = tempDir.resolve("test-" + System.nanoTime() + ".db");
        Files.createFile(dbPath);
        applySchema(dbPath);
        db = Database.open(dbPath);

        Path rootDir = tempDir.resolve("library");
        Files.createDirectories(rootDir);
        config = new Config(rootDir, dbPath, Config.Flow.RECONCILIATION, 10, 50, 30);

        applier = new OperationApplier();
    }

    @Test
    @DisplayName("apply should create new sources")
    void apply_shouldCreateNewSources() {
        ScannedFile file = scannedFile("Author/new.pdf");
        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.CREATE, file, null, "abc123", "Author")
        ));

        applier.apply(db, classifications, config, () -> false);

        List<Source> all = db.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().name()).isEqualTo("new.pdf");
        assertThat(all.getFirst().contentHash()).isEqualTo("abc123");
        assertThat(all.getFirst().fileFormat()).isEqualTo("PDF");
    }

    @Test
    @DisplayName("apply should update hash")
    void apply_shouldUpdateHash() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "old_hash", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.UPDATE, null, source, "new_hash", "Author")
        ));

        applier.apply(db, classifications, config, () -> false);

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.contentHash()).isEqualTo("new_hash");
    }

    @Test
    @DisplayName("apply should soft delete sources")
    void apply_shouldSoftDeleteSources() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.DELETE, null, source, null, null)
        ));

        applier.apply(db, classifications, config, () -> false);

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("apply should reactivate deleted sources")
    void apply_shouldReactivateDeletedSources() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");
        db.softDelete(source.id());

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.REACTIVATE, null, source, null, "Author")
        ));

        applier.apply(db, classifications, config, () -> false);

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.deletedAt()).isNull();
    }

    @Test
    @DisplayName("apply should rename source")
    void apply_shouldRenameSource() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");
        ScannedFile file = scannedFile("Author/renamed.pdf");

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.RENAME, file, source, "abc123", "Author")
        ));

        applier.apply(db, classifications, config, () -> false);

        assertThat(db.findByPathLower("author/book.pdf")).isNull();
        Source renamed = db.findByPathLower("author/renamed.pdf");
        assertThat(renamed).isNotNull();
        assertThat(renamed.path()).isEqualTo("Author/renamed.pdf");
    }

    @Test
    @DisplayName("apply should merge when rename conflicts with existing path")
    void apply_shouldMergeWhenRenameConflicts() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("old.pdf", "Author/old.pdf", "hash_old", "PDF", authorId);
        db.insertSource("existing.pdf", "Author/existing.pdf", "hash_existing", "PDF", authorId);
        db.addSourceTag(db.findByPathLower("author/existing.pdf").id(), "classic");

        Source oldSource = db.findByPathLower("author/old.pdf");
        ScannedFile file = scannedFile("Author/existing.pdf");

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.RENAME, file, oldSource, "hash_old", "Author")
        ));

        applier.apply(db, classifications, config, () -> false);

        assertThat(db.findByPathLower("author/old.pdf")).isNull();
        assertThat(db.findAll()).hasSize(1);
        Source merged = db.findByPathLower("author/existing.pdf");
        assertThat(merged).isNotNull();
        assertThat(db.findSourceTags(merged.id())).contains("classic");
    }

    @Test
    @DisplayName("apply should respect cancellation")
    void apply_shouldRespectCancellation() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.UPDATE, null, source, "new_hash", "Author")
        ));

        applier.apply(db, classifications, config, () -> true);

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.contentHash()).isEqualTo("abc123");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static ScannedFile scannedFile(String normalizedPath) {
        Path original = Path.of("/library/" + normalizedPath);
        return new ScannedFile(original, normalizedPath, FileScanner.FileFormat.PDF);
    }

    private static void applySchema(Path dbPath) {
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + dbPath);
        jdbi.installPlugin(new org.jdbi.v3.sqlite3.SQLitePlugin());
        jdbi.useHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS authors (
                        id    INTEGER PRIMARY KEY AUTOINCREMENT,
                        name  TEXT    NOT NULL UNIQUE
                    )""");
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS sources (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        name         TEXT    NOT NULL,
                        path         TEXT    NOT NULL,
                        path_lower   TEXT    NOT NULL,
                        content_hash TEXT    NOT NULL,
                        file_format  TEXT    NOT NULL CHECK (file_format IN ('PDF', 'EPUB', 'MHTML')),
                        author_id    INTEGER REFERENCES authors(id) ON DELETE SET NULL,
                        year         INTEGER NULL,
                        edition      TEXT    NULL,
                        url          TEXT    NULL,
                        created_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at   TEXT    NULL
                    )""");
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id   INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT    NOT NULL UNIQUE
                    )""");
            handle.execute("""
                    CREATE TABLE IF NOT EXISTS source_tags (
                        source_id INTEGER NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
                        tag_id    INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                        PRIMARY KEY (source_id, tag_id)
                    )""");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_sources_path_lower    ON sources(path_lower)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_sources_content_hash  ON sources(content_hash)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_sources_deleted_at    ON sources(deleted_at)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_sources_author_id     ON sources(author_id)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_source_tags_source_id ON source_tags(source_id)");
            handle.execute("CREATE INDEX IF NOT EXISTS idx_source_tags_tag_id    ON source_tags(tag_id)");
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
