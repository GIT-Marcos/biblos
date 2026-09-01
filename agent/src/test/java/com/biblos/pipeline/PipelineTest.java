package com.biblos.pipeline;

import com.biblos.config.Config;
import com.biblos.domain.Source;
import com.biblos.infrastructure.Database;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DisplayName("Pipeline")
class PipelineTest {

    @TempDir
    Path tempDir;

    private Path rootDir;
    private Path dbPath;

    @BeforeEach
    void setUp() throws IOException {
        rootDir = tempDir.resolve("library");
        Files.createDirectories(rootDir);
        dbPath = tempDir.resolve("biblos.db");
    }

    @Test
    @DisplayName("foundation should create db with all sources")
    void foundation_shouldCreateDbWithAllSources() throws IOException {
        Files.createDirectories(rootDir.resolve("Author1"));
        Files.createDirectories(rootDir.resolve("Author2"));
        Files.writeString(rootDir.resolve("Author1/book1.pdf"), "content1");
        Files.writeString(rootDir.resolve("Author2/book2.epub"), "content2");
        Config config = new Config(rootDir, dbPath, Config.Flow.FOUNDATION, 10, 50, 30);
        Pipeline pipeline = new Pipeline(config, () -> false);

        int excluded = pipeline.foundation();

        assertThat(excluded).isEqualTo(0);
        assertThat(dbPath).exists();
        try (Database db = Database.open(dbPath)) {
            List<Source> sources = db.findAll();
            assertThat(sources).hasSize(2);
        }
    }

    @Test
    @DisplayName("foundation should exclude empty files")
    void foundation_shouldExcludeEmptyFiles() throws IOException {
        Files.createDirectories(rootDir.resolve("Author"));
        Files.writeString(rootDir.resolve("Author/valid.pdf"), "content");
        Files.writeString(rootDir.resolve("Author/empty.pdf"), "");
        Config config = new Config(rootDir, dbPath, Config.Flow.FOUNDATION, 10, 50, 30);
        Pipeline pipeline = new Pipeline(config, () -> false);

        int excluded = pipeline.foundation();

        assertThat(excluded).isEqualTo(1);
        try (Database db = Database.open(dbPath)) {
            List<Source> sources = db.findAll();
            assertThat(sources).hasSize(1);
            assertThat(sources.getFirst().name()).isEqualTo("valid.pdf");
        }
    }

    @Test
    @DisplayName("reconciliation should create new files")
    void reconciliation_shouldCreateNewFiles() throws IOException {
        Files.createDirectories(rootDir.resolve("Author"));
        Files.writeString(rootDir.resolve("Author/existing.pdf"), "content1");
        applySchema(dbPath);
        Config config = new Config(rootDir, dbPath, Config.Flow.FOUNDATION, 10, 50, 30);
        Pipeline foundationPipeline = new Pipeline(config, () -> false);
        foundationPipeline.foundation();

        Files.writeString(rootDir.resolve("Author/new.pdf"), "content2");
        Config reconConfig = new Config(rootDir, dbPath, Config.Flow.RECONCILIATION, 10, 50, 30);
        Pipeline reconPipeline = new Pipeline(reconConfig, () -> false);

        reconPipeline.reconciliation();

        try (Database db = Database.open(dbPath)) {
            List<Source> sources = db.findAll();
            assertThat(sources).hasSize(2);
            assertThat(sources).extracting(Source::name)
                    .containsExactlyInAnyOrder("existing.pdf", "new.pdf");
        }
    }

    @Test
    @DisplayName("reconciliation should delete missing files")
    void reconciliation_shouldDeleteMissingFiles() throws IOException {
        Files.createDirectories(rootDir.resolve("Author"));
        Files.writeString(rootDir.resolve("Author/keep.pdf"), "content1");
        Files.writeString(rootDir.resolve("Author/remove.pdf"), "content2");
        applySchema(dbPath);
        Config config = new Config(rootDir, dbPath, Config.Flow.FOUNDATION, 10, 50, 30);
        Pipeline foundationPipeline = new Pipeline(config, () -> false);
        foundationPipeline.foundation();

        Files.delete(rootDir.resolve("Author/remove.pdf"));
        Config reconConfig = new Config(rootDir, dbPath, Config.Flow.RECONCILIATION, 10, 50, 30);
        Pipeline reconPipeline = new Pipeline(reconConfig, () -> false);

        reconPipeline.reconciliation();

        try (Database db = Database.open(dbPath)) {
            List<Source> sources = db.findAll();
            assertThat(sources).hasSize(2);
            Source keep = sources.stream().filter(s -> s.name().equals("keep.pdf")).findFirst().orElseThrow();
            Source remove = sources.stream().filter(s -> s.name().equals("remove.pdf")).findFirst().orElseThrow();
            assertThat(keep.deletedAt()).isNull();
            assertThat(remove.deletedAt()).isNotNull();
        }
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
