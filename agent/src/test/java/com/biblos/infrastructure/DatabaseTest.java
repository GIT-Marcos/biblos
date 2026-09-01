package com.biblos.infrastructure;

import com.biblos.TestHelper;
import com.biblos.domain.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@DisplayName("Database")
class DatabaseTest {

    @TempDir
    Path tempDir;

    private Database db;
    private Path dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test-" + System.nanoTime() + ".db");
        TestHelper.applySchema(dbPath);
        db = Database.open(dbPath);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("create should create db file when path is valid")
    void create_shouldCreateDbFile_when_pathValid() throws IOException {
        Path newDb = tempDir.resolve("created-" + System.nanoTime() + ".db");

        Database created = Database.create(newDb);

        assertThat(newDb).exists();
    }

    @Test
    @DisplayName("create should create parent dirs when they do not exist")
    void create_shouldCreateParentDirs_when_theyDoNotExist() {
        Path nested = tempDir.resolve("a/b/c/deep.db");

        assertThat(nested.getParent()).doesNotExist();

        try {
            Database.create(nested);
        } catch (Exception e) {
            // expected to succeed or fail for other reasons, not missing dirs
        }

        assertThat(nested.getParent()).exists();
    }

    @Test
    @DisplayName("open should open existing db when file exists")
    void open_shouldOpenExistingDb_when_fileExists() {
        Database opened = Database.open(dbPath);

        assertThat(opened).isNotNull();
        assertThat(opened.findAll()).isNotNull();
    }

    @Test
    @DisplayName("open should throw when file not found")
    void open_shouldThrow_when_fileNotFound() {
        Path nonexistent = tempDir.resolve("noexiste.db");

        assertThatThrownBy(() -> Database.open(nonexistent))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("not found");
    }

    // ── Queries ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll should return empty when no sources")
    void findAll_shouldReturnEmpty_when_noSources() {
        List<Source> result = db.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByHash should return empty when hash not found")
    void findByHash_shouldReturnEmpty_when_hashNotFound() {
        List<Source> result = db.findByHash("nonexistent_hash");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByPathLower should return null when path not found")
    void findByPathLower_shouldReturnNull_when_pathNotFound() {
        Source result = db.findByPathLower("nonexistent/path.pdf");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findOrCreateAuthor should return 0 when name is null")
    void findOrCreateAuthor_shouldReturn0_when_nameIsNull() {
        long result = db.findOrCreateAuthor(null);

        assertThat(result).isEqualTo(0);
    }

    // ── Mutations ───────────────────────────────────────────────────────

    @Test
    @DisplayName("insertSource should insert and find back")
    void insertSource_shouldInsertAndFindBack() {
        long authorId = db.findOrCreateAuthor("Test Author");

        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);

        List<Source> all = db.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().name()).isEqualTo("book.pdf");
        assertThat(all.getFirst().path()).isEqualTo("Author/book.pdf");
        assertThat(all.getFirst().contentHash()).isEqualTo("abc123");
        assertThat(all.getFirst().fileFormat()).isEqualTo("PDF");
        assertThat(all.getFirst().authorId()).isEqualTo(authorId);
    }

    @Test
    @DisplayName("insertSource should set pathLower correctly")
    void insertSource_shouldSetPathLowerCorrectly() {
        db.insertSource("Book.PDF", "Author/Book.PDF", "abc123", "PDF", 0);

        Source source = db.findByPathLower("author/book.pdf");

        assertThat(source).isNotNull();
        assertThat(source.pathLower()).isEqualTo("author/book.pdf");
    }

    @Test
    @DisplayName("insertSource should set null author when authorId is zero")
    void insertSource_shouldSetNullAuthor_when_authorIdIsZero() {
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", 0);

        Source source = db.findByPathLower("author/book.pdf");

        assertThat(source).isNotNull();
        assertThat(source.authorId()).isNull();
    }

    @Test
    @DisplayName("insertSourceBatch should insert multiple sources")
    void insertSourceBatch_shouldInsertMultipleSources() {
        long authorId = db.findOrCreateAuthor("Batch Author");
        List<SourceRecord> records = List.of(
                new SourceRecord("a.pdf", "Author/a.pdf", "author/a.pdf", "hash1", "PDF", authorId),
                new SourceRecord("b.epub", "Author/b.epub", "author/b.epub", "hash2", "EPUB", authorId),
                new SourceRecord("c.mhtml", "Author/c.mhtml", "author/c.mhtml", "hash3", "MHTML", authorId)
        );

        db.insertSourceBatch(records);

        assertThat(db.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("insertSourceBatch with handle should work inside transaction")
    void insertSourceBatch_withHandle_shouldWorkInsideTransaction() {
        long authorId = db.findOrCreateAuthor("Tx Author");
        List<SourceRecord> records = List.of(
                new SourceRecord("x.pdf", "Author/x.pdf", "author/x.pdf", "hashX", "PDF", authorId),
                new SourceRecord("y.pdf", "Author/y.pdf", "author/y.pdf", "hashY", "PDF", authorId)
        );

        db.withHandle(handle -> {
            db.insertSourceBatch(handle, records);
            return null;
        });

        assertThat(db.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("updatePath should update path and pathLower")
    void updatePath_shouldUpdatePathAndPathLower() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Old/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("old/book.pdf");

        db.updatePath(source.id(), "New/book.pdf", "new/book.pdf");

        Source updated = db.findByPathLower("new/book.pdf");
        assertThat(updated).isNotNull();
        assertThat(updated.path()).isEqualTo("New/book.pdf");
        assertThat(db.findByPathLower("old/book.pdf")).isNull();
    }

    @Test
    @DisplayName("updateHash should update content hash")
    void updateHash_shouldUpdateContentHash() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "old_hash", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        db.updateHash(source.id(), "new_hash");

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.contentHash()).isEqualTo("new_hash");
    }

    @Test
    @DisplayName("updateAuthor should update author id")
    void updateAuthor_shouldUpdateAuthorId() {
        long author1 = db.findOrCreateAuthor("Author 1");
        long author2 = db.findOrCreateAuthor("Author 2");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", author1);
        Source source = db.findByPathLower("author/book.pdf");

        db.updateAuthor(source.id(), author2);

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.authorId()).isEqualTo(author2);
    }

    @Test
    @DisplayName("updateMetadata should update year edition and url")
    void updateMetadata_shouldUpdateYearEditionUrl() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        db.updateMetadata(source.id(), 2024, "1st", "https://example.com");

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.year()).isEqualTo(2024);
        assertThat(updated.edition()).isEqualTo("1st");
        assertThat(updated.url()).isEqualTo("https://example.com");
    }

    @Test
    @DisplayName("reactivate should clear deletedAt")
    void reactivate_shouldClearDeletedAt() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");
        db.softDelete(source.id());

        db.reactivate(source.id());

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.deletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete should set deletedAt")
    void softDelete_shouldSetDeletedAt() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        db.softDelete(source.id());

        Source updated = db.findByPathLower("author/book.pdf");
        assertThat(updated.deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("deleteSource should remove from db")
    void deleteSource_shouldRemoveFromDb() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        db.deleteSource(source.id());

        assertThat(db.findByPathLower("author/book.pdf")).isNull();
        assertThat(db.findAll()).isEmpty();
    }

    // ── Tags ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addSourceTag should create tag and associate")
    void addSourceTag_shouldCreateTagAndAssociate() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        db.addSourceTag(source.id(), "fiction");

        List<String> tags = db.findSourceTags(source.id());
        assertThat(tags).containsExactly("fiction");
    }

    @Test
    @DisplayName("addSourceTag should not duplicate when tag already exists")
    void addSourceTag_shouldNotDuplicate_when_tagAlreadyExists() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");

        db.addSourceTag(source.id(), "fiction");
        db.addSourceTag(source.id(), "fiction");

        List<String> tags = db.findSourceTags(source.id());
        assertThat(tags).hasSize(1);
    }

    @Test
    @DisplayName("findSourceTags should return tag names for source")
    void findSourceTags_shouldReturnTagNames() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("book.pdf", "Author/book.pdf", "abc123", "PDF", authorId);
        Source source = db.findByPathLower("author/book.pdf");
        db.addSourceTag(source.id(), "fiction");
        db.addSourceTag(source.id(), "classic");

        List<String> tags = db.findSourceTags(source.id());

        assertThat(tags).containsExactlyInAnyOrder("fiction", "classic");
    }

    // ── Handle-level batch methods ──────────────────────────────────────

    @Test
    @DisplayName("updateHashBatch should update all hashes via handle")
    void updateHashBatch_shouldUpdateAllHashes() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("a.pdf", "Author/a.pdf", "a", "PDF", authorId);
        db.insertSource("b.pdf", "Author/b.pdf", "b", "PDF", authorId);
        Source a = db.findByPathLower("author/a.pdf");
        Source b = db.findByPathLower("author/b.pdf");

        db.withHandle(handle -> {
            db.updateHashBatch(handle, List.of(a.id(), b.id()), List.of("newA", "newB"));
            return null;
        });

        assertThat(db.findByPathLower("author/a.pdf").contentHash()).isEqualTo("newA");
        assertThat(db.findByPathLower("author/b.pdf").contentHash()).isEqualTo("newB");
    }

    @Test
    @DisplayName("softDeleteBatch should soft delete all via handle")
    void softDeleteBatch_shouldSoftDeleteAll() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("a.pdf", "Author/a.pdf", "a", "PDF", authorId);
        db.insertSource("b.pdf", "Author/b.pdf", "b", "PDF", authorId);
        Source a = db.findByPathLower("author/a.pdf");
        Source b = db.findByPathLower("author/b.pdf");

        db.withHandle(handle -> {
            db.softDeleteBatch(handle, List.of(a.id(), b.id()));
            return null;
        });

        assertThat(db.findByPathLower("author/a.pdf").deletedAt()).isNotNull();
        assertThat(db.findByPathLower("author/b.pdf").deletedAt()).isNotNull();
    }

    @Test
    @DisplayName("reactivateBatch should reactivate all via handle")
    void reactivateBatch_shouldReactivateAll() {
        long authorId = db.findOrCreateAuthor("Author");
        db.insertSource("a.pdf", "Author/a.pdf", "a", "PDF", authorId);
        db.insertSource("b.pdf", "Author/b.pdf", "b", "PDF", authorId);
        Source a = db.findByPathLower("author/a.pdf");
        Source b = db.findByPathLower("author/b.pdf");
        db.softDelete(a.id());
        db.softDelete(b.id());

        db.withHandle(handle -> {
            db.reactivateBatch(handle, List.of(a.id(), b.id()));
            return null;
        });

        assertThat(db.findByPathLower("author/a.pdf").deletedAt()).isNull();
        assertThat(db.findByPathLower("author/b.pdf").deletedAt()).isNull();
    }

}
