package com.biblos.infrastructure;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@DisplayName("FileScanner")
class FileScannerTest {

    @TempDir
    Path tempDir;

    private FileScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new FileScanner();
    }

    // ── Core Scan ───────────────────────────────────────────────────────

    @Test
    @DisplayName("scan should return PDF files")
    void scan_shouldReturnPdfFiles() throws IOException {
        Files.writeString(tempDir.resolve("book.pdf"), "pdf content");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().format()).isEqualTo(FileScanner.FileFormat.PDF);
    }

    @Test
    @DisplayName("scan should return EPUB files")
    void scan_shouldReturnEpupFiles() throws IOException {
        Files.writeString(tempDir.resolve("book.epub"), "epub content");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().format()).isEqualTo(FileScanner.FileFormat.EPUB);
    }

    @Test
    @DisplayName("scan should return MHTML files")
    void scan_shouldReturnMhtmlFiles() throws IOException {
        Files.writeString(tempDir.resolve("page.mhtml"), "mhtml content");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().format()).isEqualTo(FileScanner.FileFormat.MHTML);
    }

    @Test
    @DisplayName("scan should exclude unsupported extensions")
    void scan_shouldExcludeUnsupportedExtensions() throws IOException {
        Files.writeString(tempDir.resolve("notes.txt"), "text");
        Files.writeString(tempDir.resolve("doc.docx"), "docx");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("scan should exclude files with no extension")
    void scan_shouldExcludeFilesWithNoExtension() throws IOException {
        Files.writeString(tempDir.resolve("sin_extension"), "no ext");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("scan should throw when root dir not found")
    void scan_shouldThrow_when_rootDirNotFound() {
        Path nonexistent = tempDir.resolve("noexiste");

        assertThatThrownBy(() -> scanner.scan(nonexistent, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("scan should respect max depth")
    void scan_shouldRespectMaxDepth() throws IOException {
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(tempDir.resolve("root.pdf"), "root");
        Files.writeString(sub.resolve("deep.pdf"), "deep");

        List<ScannedFile> result = scanner.scan(tempDir, 1);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().normalizedPath()).contains("root.pdf");
    }

    @Test
    @DisplayName("scan should find files in subdirectories")
    void scan_shouldFindFilesInSubdirectories() throws IOException {
        Path sub = tempDir.resolve("Author");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("book.pdf"), "content");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().normalizedPath()).contains("Author/book.pdf");
    }

    // ── Edge Cases ──────────────────────────────────────────────────────

    @Test
    @DisplayName("scan should handle case insensitive extensions")
    void scan_shouldHandleCaseInsensitiveExtensions() throws IOException {
        Files.writeString(tempDir.resolve("UPPER.PDF"), "upper");
        Files.writeString(tempDir.resolve("Mixed.Epub"), "mixed");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("scan should warn on similar extension mht")
    void scan_shouldWarnOnSimilarExtension() throws IOException {
        Files.writeString(tempDir.resolve("page.mht"), "mht content");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("scan should warn on multiple extensions using last")
    void scan_shouldWarnOnMultipleExtensions() throws IOException {
        Files.writeString(tempDir.resolve("backup.pdf.bak"), "bak content");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("scan should handle files with unicode names")
    void scan_shouldHandleUnicodeNames() throws IOException {
        Files.writeString(tempDir.resolve("archivo.pdf"), "unicode");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(1);
    }

    // ── normalizePath ───────────────────────────────────────────────────

    @Test
    @DisplayName("normalizePath should replace backslashes with forward slashes")
    void normalizePath_shouldReplaceBackslashes() {
        Path file = Path.of("C:\\Users\\test\\file.pdf");

        String result = FileScanner.normalizePath(file);

        assertThat(result).doesNotContain("\\");
        assertThat(result).contains("/");
    }

    // ── getExtension ────────────────────────────────────────────────────

    @Test
    @DisplayName("getExtension should return last extension in lowercase")
    void getExtension_shouldReturnLastExtension() {
        assertThat(FileScanner.getExtension(Path.of("file.PDF"))).isEqualTo("pdf");
        assertThat(FileScanner.getExtension(Path.of("archive.tar.gz"))).isEqualTo("gz");
    }

    @Test
    @DisplayName("getExtension should return null when no extension")
    void getExtension_shouldReturnNull_when_noExtension() {
        assertThat(FileScanner.getExtension(Path.of("noext"))).isNull();
        assertThat(FileScanner.getExtension(Path.of("trailing."))).isNull();
    }

    @Test
    @DisplayName("scan should handle multiple files with mixed formats")
    void scan_shouldHandleMultipleFiles() throws IOException {
        Files.writeString(tempDir.resolve("a.pdf"), "pdf");
        Files.writeString(tempDir.resolve("b.epub"), "epub");
        Files.writeString(tempDir.resolve("c.mhtml"), "mhtml");
        Files.writeString(tempDir.resolve("d.txt"), "txt");

        List<ScannedFile> result = scanner.scan(tempDir, 10);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(ScannedFile::format)
                .containsExactlyInAnyOrder(
                        FileScanner.FileFormat.PDF,
                        FileScanner.FileFormat.EPUB,
                        FileScanner.FileFormat.MHTML
                );
    }
}
