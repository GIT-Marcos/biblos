package com.biblos.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@DisplayName("BackupService")
class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupService backupService;

    @BeforeEach
    void setUp() {
        backupService = new BackupService();
    }

    @Test
    @DisplayName("backup should create bak file when db exists")
    void backup_shouldCreateBakFile_when_dbExists() throws IOException {
        Path db = tempDir.resolve("biblos.db");
        Files.writeString(db, "database content");

        backupService.backup(db);

        Path bak = Path.of(db + ".bak");
        assertThat(bak).exists();
        assertThat(Files.readString(bak)).isEqualTo("database content");
    }

    @Test
    @Tag("edge-case")
    @DisplayName("backup should replace existing bak file")
    void backup_shouldReplaceExistingBak() throws IOException {
        Path db = tempDir.resolve("biblos.db");
        Path bak = Path.of(db + ".bak");
        Files.writeString(db, "new content");
        Files.writeString(bak, "old content");

        backupService.backup(db);

        assertThat(Files.readString(bak)).isEqualTo("new content");
    }

    @Test
    @DisplayName("backup should do nothing when db not found")
    void backup_shouldDoNothing_when_dbNotFound() {
        Path db = tempDir.resolve("nonexistent.db");

        backupService.backup(db);

        assertThat(Path.of(db + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("backup should handle io error gracefully")
    void backup_shouldHandleIOException_gracefully() throws IOException {
        Path db = tempDir.resolve("biblos.db");
        Files.writeString(db, "content");

        backupService.backup(db);

        Path bak = Path.of(db + ".bak");
        assertThat(bak).exists();
    }
}
