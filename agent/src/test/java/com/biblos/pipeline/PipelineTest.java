package com.biblos.pipeline;

import com.biblos.TestHelper;
import com.biblos.config.Config;
import com.biblos.domain.Source;
import com.biblos.infrastructure.Database;
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
        TestHelper.applySchema(dbPath);
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
        TestHelper.applySchema(dbPath);
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

}
