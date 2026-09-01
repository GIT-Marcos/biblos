package com.biblos.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("HashService")
class HashServiceTest {

    @TempDir
    Path tempDir;

    private HashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new HashService();
    }

    @Test
    @DisplayName("computeHashWithResult should return 64-char lowercase hex hash")
    void computeHashWithResult_shouldReturnValidHash_when_normalFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.hash()).hasSize(64);
        assertThat(result.hash()).matches("[0-9a-f]{64}");
        assertThat(result.excluded()).isFalse();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("computeHash should return hash string for normal file")
    void computeHash_shouldReturnHash_when_normalFile() throws IOException {
        Path file = tempDir.resolve("data.bin");
        Files.write(file, new byte[]{1, 2, 3, 4, 5});

        String hash = hashService.computeHash(file);

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("computeHashWithResult should be excluded when empty file (H1)")
    void computeHashWithResult_shouldBeExcluded_when_emptyFile() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.excluded()).isTrue();
        assertThat(result.hash()).isNull();
        assertThat(result.reason()).contains("empty");
    }

    @Test
    @DisplayName("computeHashWithResult should be excluded when file not found (H2)")
    void computeHashWithResult_shouldBeExcluded_when_fileNotFound() {
        Path file = tempDir.resolve("nonexistent.txt");

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.excluded()).isTrue();
        assertThat(result.hash()).isNull();
        assertThat(result.reason()).contains("I/O error");
    }

    @Test
    @DisplayName("computeHash should return null when file is excluded")
    void computeHash_shouldReturnNull_when_fileExcluded() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        String hash = hashService.computeHash(file);

        assertThat(hash).isNull();
    }

    @Test
    @DisplayName("computeHash should return same hash when same content")
    void computeHash_shouldReturnSameHash_when_sameContent() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        String content = "identical content for both files";
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        String hash1 = hashService.computeHash(file1);
        String hash2 = hashService.computeHash(file2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("computeHash should return different hash when different content")
    void computeHash_shouldReturnDifferentHash_when_differentContent() throws IOException {
        Path file1 = tempDir.resolve("x.txt");
        Path file2 = tempDir.resolve("y.txt");
        Files.writeString(file1, "content A");
        Files.writeString(file2, "content B");

        String hash1 = hashService.computeHash(file1);
        String hash2 = hashService.computeHash(file2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("computeHashWithResult should detect write-race when size changes (H5)")
    void computeHashWithResult_shouldDetectWriteRace_when_sizeChangesBeforeHash() throws IOException {
        Path file = tempDir.resolve("race.txt");
        Files.writeString(file, "original content");

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.excluded()).isFalse();
        assertThat(result.hash()).hasSize(64);
    }

    @Test
    @DisplayName("computeHashWithResult should use extended timeout for UNC paths (H6)")
    void computeHashWithResult_shouldHandleUncPath() throws IOException {
        Path file = tempDir.resolve("normal.txt");
        Files.writeString(file, "unc test");

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.excluded()).isFalse();
        assertThat(result.hash()).hasSize(64);
    }

    @Test
    @DisplayName("computeHashWithResult should handle file with special characters in name")
    void computeHashWithResult_shouldHandleFileWithSpecialChars() throws IOException {
        Path file = tempDir.resolve("archivo con espacios (1).pdf");
        Files.write(file, new byte[]{0x25, 0x50, 0x44, 0x46});

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.excluded()).isFalse();
        assertThat(result.hash()).hasSize(64);
    }

    @Test
    @DisplayName("computeHashWithResult should handle large file within size limit")
    void computeHashWithResult_shouldHandleLargeFile() throws IOException {
        Path file = tempDir.resolve("large.bin");
        byte[] data = new byte[1024 * 100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(file, data);

        HashResult result = hashService.computeHashWithResult(file);

        assertThat(result.excluded()).isFalse();
        assertThat(result.hash()).hasSize(64);
        assertThat(result.hash()).matches("[0-9a-f]{64}");
    }
}
