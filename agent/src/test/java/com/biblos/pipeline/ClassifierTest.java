package com.biblos.pipeline;

import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.FileScanner;
import com.biblos.infrastructure.ScannedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("Classifier")
class ClassifierTest {

    private Classifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new Classifier();
    }

    // ── classifyExisting ────────────────────────────────────────────────

    @Test
    @DisplayName("classifyExisting should SKIP when same hash and active (case A)")
    void classifyExisting_shouldSkip_when_sameHashAndActive() {
        Source source = activeSource(1, "abc123");
        List<Classification> result = new ArrayList<>();

        classifier.classifyExisting(source, "abc123", "Author", result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.SKIP);
        assertThat(result.getFirst().dbSource()).isEqualTo(source);
    }

    @Test
    @DisplayName("classifyExisting should REACTIVATE when same hash and deleted (case B)")
    void classifyExisting_shouldReactivate_when_sameHashAndDeleted() {
        Source source = deletedSource(1, "abc123");
        List<Classification> result = new ArrayList<>();

        classifier.classifyExisting(source, "abc123", "Author", result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.REACTIVATE);
    }

    @Test
    @DisplayName("classifyExisting should UPDATE when different hash and active (case C)")
    void classifyExisting_shouldUpdate_when_differentHashAndActive() {
        Source source = activeSource(1, "abc123");
        List<Classification> result = new ArrayList<>();

        classifier.classifyExisting(source, "def456", "Author", result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.UPDATE);
        assertThat(result.getFirst().newHash()).isEqualTo("def456");
    }

    @Test
    @DisplayName("classifyExisting should REACTIVATE_UPDATE when different hash and deleted (case H)")
    void classifyExisting_shouldReactivateUpdate_when_differentHashAndDeleted() {
        Source source = deletedSource(1, "abc123");
        List<Classification> result = new ArrayList<>();

        classifier.classifyExisting(source, "def456", "Author", result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.REACTIVATE_UPDATE);
        assertThat(result.getFirst().newHash()).isEqualTo("def456");
    }

    // ── selectBestMatch ─────────────────────────────────────────────────

    @Test
    @DisplayName("selectBestMatch should return null when no candidates")
    void selectBestMatch_shouldReturnNull_when_noCandidates() {
        Source result = classifier.selectBestMatch(
                "abc123", "Author/file.pdf", List.of(), new HashSet<>());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("selectBestMatch should prefer active over orphan")
    void selectBestMatch_shouldPreferActive_when_activeAndOrphanExist() {
        Source orphan = deletedSource(1, "abc123");
        Source active = activeSource(2, "abc123");
        Set<Long> claimed = new HashSet<>();

        Source result = classifier.selectBestMatch(
                "abc123", "Author/file.pdf", List.of(orphan, active), claimed);

        assertThat(result).isEqualTo(active);
    }

    @Test
    @DisplayName("selectBestMatch should prefer same directory prefix")
    void selectBestMatch_shouldPreferSameDir_when_multipleCandidates() {
        Source otherDir = activeSource(1, "abc123");
        // pathLower must match the expected dir prefix
        Source sameDir = new Source(2, "book.pdf", "Author/book.pdf",
                "author/book.pdf", "abc123", "PDF", null, null, null, null,
                "2024-01-01", "2024-01-01", null);
        Set<Long> claimed = new HashSet<>();

        Source result = classifier.selectBestMatch(
                "abc123", "Author/file.pdf", List.of(otherDir, sameDir), claimed);

        assertThat(result).isEqualTo(sameDir);
    }

    @Test
    @DisplayName("selectBestMatch should skip claimed candidates")
    void selectBestMatch_shouldSkipClaimed_when_candidateAlreadyClaimed() {
        Source claimed = activeSource(1, "abc123");
        Source available = activeSource(2, "abc123");
        Set<Long> claimedIds = new HashSet<>(Set.of(1L));

        Source result = classifier.selectBestMatch(
                "abc123", "Author/file.pdf", List.of(claimed, available), claimedIds);

        assertThat(result).isEqualTo(available);
    }

    @Test
    @DisplayName("selectBestMatch should return null when all candidates claimed")
    void selectBestMatch_shouldReturnNull_when_allCandidatesClaimed() {
        Source s1 = activeSource(1, "abc123");
        Source s2 = activeSource(2, "abc123");
        Set<Long> claimedIds = new HashSet<>(Set.of(1L, 2L));

        Source result = classifier.selectBestMatch(
                "abc123", "Author/file.pdf", List.of(s1, s2), claimedIds);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("selectBestMatch should fallback to lexicographic when no dir match")
    void selectBestMatch_shouldFallbackToLexicographic_when_noDirMatch() {
        Source b = new Source(2, "b.pdf", "Zebra/b.pdf",
                "zebra/b.pdf", "abc123", "PDF", null, null, null, null,
                "2024-01-01", "2024-01-01", null);
        Source a = new Source(1, "a.pdf", "Alpha/a.pdf",
                "alpha/a.pdf", "abc123", "PDF", null, null, null, null,
                "2024-01-01", "2024-01-01", null);
        Set<Long> claimed = new HashSet<>();

        Source result = classifier.selectBestMatch(
                "abc123", "DifferentDir/file.pdf", List.of(b, a), claimed);

        assertThat(result).isEqualTo(a);
    }

    // ── classifyNew ─────────────────────────────────────────────────────

    @Test
    @DisplayName("classifyNew should CREATE when no match by hash")
    void classifyNew_shouldCreate_when_noMatchByHash() {
        ScannedFileWithMeta entry = scannedEntry("New/book.pdf", "aaa111", "Author");
        List<Classification> result = new ArrayList<>();

        classifier.classifyNew(entry, Map.of(), new HashSet<>(), result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.CREATE);
        assertThat(result.getFirst().scannedFile()).isEqualTo(entry.file());
    }

    @Test
    @DisplayName("classifyNew should RENAME when match by hash found")
    void classifyNew_shouldRename_when_matchByHashFound() {
        Source orphan = deletedSource(1, "aaa111");
        ScannedFileWithMeta entry = scannedEntry("New/book.pdf", "aaa111", "Author");
        List<Classification> result = new ArrayList<>();

        classifier.classifyNew(entry, Map.of("aaa111", List.of(orphan)), new HashSet<>(), result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.RENAME);
        assertThat(result.getFirst().dbSource()).isEqualTo(orphan);
    }

    @Test
    @DisplayName("classifyNew should claim selected match in claimedIds")
    void classifyNew_shouldClaimSelectedMatch_when_multipleCandidates() {
        Source s1 = activeSource(1, "aaa111");
        Source s2 = activeSource(2, "aaa111");
        ScannedFileWithMeta entry = scannedEntry("New/book.pdf", "aaa111", "Author");
        Set<Long> claimedIds = new HashSet<>();
        List<Classification> result = new ArrayList<>();

        classifier.classifyNew(entry, Map.of("aaa111", List.of(s1, s2)), claimedIds, result);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().operation()).isEqualTo(Operation.RENAME);
        assertThat(claimedIds).isNotEmpty();
    }

    // ── reconcileDeleteCreatePairs ──────────────────────────────────────

    @Test
    @DisplayName("reconcileDeleteCreatePairs should merge DELETE+CREATE with same hash into RENAME")
    void reconcileDeleteCreatePairs_shouldMerge_when_deleteAndCreateSameHash() {
        Source oldSource = deletedSource(1, "abc123");
        ScannedFile scanned = scannedFile("New/book.pdf");
        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.DELETE, null, oldSource, null, "Author"),
                new Classification(Operation.CREATE, scanned, null, "abc123", "Author")
        ));

        classifier.reconcileDeleteCreatePairs(classifications);

        assertThat(classifications).hasSize(1);
        assertThat(classifications.getFirst().operation()).isEqualTo(Operation.RENAME);
        assertThat(classifications.getFirst().dbSource()).isEqualTo(oldSource);
        assertThat(classifications.getFirst().scannedFile()).isEqualTo(scanned);
    }

    @Test
    @DisplayName("reconcileDeleteCreatePairs should not merge when different hash")
    void reconcileDeleteCreatePairs_shouldNotMerge_when_differentHash() {
        Source oldSource = deletedSource(1, "abc123");
        ScannedFile scanned = scannedFile("New/book.pdf");
        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.DELETE, null, oldSource, null, "Author"),
                new Classification(Operation.CREATE, scanned, null, "def456", "Author")
        ));

        classifier.reconcileDeleteCreatePairs(classifications);

        assertThat(classifications).hasSize(2);
        assertThat(classifications.get(0).operation()).isEqualTo(Operation.DELETE);
        assertThat(classifications.get(1).operation()).isEqualTo(Operation.CREATE);
    }

    @Test
    @DisplayName("reconcileDeleteCreatePairs should not merge when CREATE has null scannedFile")
    void reconcileDeleteCreatePairs_shouldNotMerge_when_createHasNoScannedFile() {
        Source oldSource = deletedSource(1, "abc123");
        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.DELETE, null, oldSource, null, "Author"),
                new Classification(Operation.CREATE, null, null, "abc123", "Author")
        ));

        classifier.reconcileDeleteCreatePairs(classifications);

        assertThat(classifications).hasSize(2);
    }

    @Test
    @DisplayName("reconcileDeleteCreatePairs should handle empty list")
    void reconcileDeleteCreatePairs_shouldHandleEmptyList() {
        List<Classification> classifications = new ArrayList<>();

        classifier.reconcileDeleteCreatePairs(classifications);

        assertThat(classifications).isEmpty();
    }

    @Test
    @DisplayName("reconcileDeleteCreatePairs should only pair first matching CREATE")
    void reconcileDeleteCreatePairs_shouldOnlyPairFirstMatch_when_multipleCreateSameHash() {
        Source oldSource = deletedSource(1, "abc123");
        ScannedFile scanned1 = scannedFile("New/book1.pdf");
        ScannedFile scanned2 = scannedFile("New/book2.pdf");
        List<Classification> classifications = new ArrayList<>(List.of(
                new Classification(Operation.DELETE, null, oldSource, null, "Author"),
                new Classification(Operation.CREATE, scanned1, null, "abc123", "Author"),
                new Classification(Operation.CREATE, scanned2, null, "abc123", "Author")
        ));

        classifier.reconcileDeleteCreatePairs(classifications);

        assertThat(classifications).hasSize(2);
        assertThat(classifications).extracting(Classification::operation)
                .containsExactlyInAnyOrder(Operation.RENAME, Operation.CREATE);
        assertThat(classifications).filteredOn(c -> c.operation() == Operation.RENAME)
                .extracting(Classification::scannedFile)
                .containsExactly(scanned1);
        assertThat(classifications).filteredOn(c -> c.operation() == Operation.CREATE)
                .extracting(Classification::scannedFile)
                .containsExactly(scanned2);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Source activeSource(long id, String hash) {
        return new Source(id, "book" + id + ".pdf", "Author/book" + id + ".pdf",
                "author/book" + id + ".pdf", hash, "PDF", null, null, null, null,
                "2024-01-01", "2024-01-01", null);
    }

    private static Source deletedSource(long id, String hash) {
        return new Source(id, "book" + id + ".pdf", "Author/book" + id + ".pdf",
                "author/book" + id + ".pdf", hash, "PDF", null, null, null, null,
                "2024-01-01", "2024-01-01", "2024-06-01");
    }

    private static ScannedFile scannedFile(String normalizedPath) {
        Path original = Paths.get("/library/" + normalizedPath);
        return new ScannedFile(original, normalizedPath, FileScanner.FileFormat.PDF);
    }

    private static ScannedFileWithMeta scannedEntry(String normalizedPath, String hash, String author) {
        return new ScannedFileWithMeta(scannedFile(normalizedPath), hash, author);
    }
}
