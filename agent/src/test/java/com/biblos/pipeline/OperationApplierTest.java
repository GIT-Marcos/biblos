package com.biblos.pipeline;

import com.biblos.TestHelper;
import com.biblos.config.Config;
import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.Database;
import com.biblos.infrastructure.FileScanner;
import com.biblos.infrastructure.ScannedFile;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        TestHelper.applySchema(dbPath);
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
    @Tag("edge-case")
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
    @DisplayName("apply should throw PipelineCancelledException and rollback when cancelled")
    void apply_shouldRespectCancellation() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.UPDATE, null, source, "new_hash", "Author")
        ));

        assertThatThrownBy(() -> applier.apply(db, classifications, config, () -> true))
                .isInstanceOf(PipelineCancelledException.class);

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.contentHash()).isEqualTo("abc123");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static ScannedFile scannedFile(String normalizedPath) {
        Path original = Path.of("/library/" + normalizedPath);
        return new ScannedFile(original, normalizedPath, FileScanner.FileFormat.PDF);
    }
}
