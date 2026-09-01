package com.biblos.infrastructure;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlite3.SQLitePlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("SchemaValidator")
class SchemaValidatorTest {

    @TempDir
    Path tempDir;

    private Jdbi jdbi;
    private SchemaValidator validator;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test-" + System.nanoTime() + ".db");
        jdbi = Jdbi.create("jdbc:sqlite:" + dbFile);
        jdbi.installPlugin(new SQLitePlugin());
        validator = new SchemaValidator();
    }

    @Test
    @DisplayName("validateVersion should pass when no schema_version table")
    void validateVersion_shouldPass_when_noSchemaVersionTable() {
        validator.validateVersion(jdbi);
    }

    @Test
    @DisplayName("validateVersion should pass when db version equals agent version")
    void validateVersion_shouldPass_when_dbVersionEqualsAgentVersion() {
        createSchemaVersionWith(1, "initial");

        validator.validateVersion(jdbi);
    }

    @Test
    @DisplayName("validateVersion should pass when db version is less than agent version")
    void validateVersion_shouldPass_when_dbVersionLessThanAgentVersion() {
        createSchemaVersionWith(0, "empty");

        validator.validateVersion(jdbi);
    }

    @Test
    @DisplayName("validateVersion should throw when db version is newer than agent")
    void validateVersion_shouldThrow_when_dbVersionNewerThanAgent() {
        createSchemaVersionWith(99, "future version");

        assertThatThrownBy(() -> validator.validateVersion(jdbi))
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("database version V99")
                .hasMessageContaining("newer than agent version");
    }

    @Test
    @DisplayName("validateIntegrity should pass when db is healthy")
    void validateIntegrity_shouldPass_when_dbIsHealthy() {
        jdbi.useHandle(handle -> handle.execute("CREATE TABLE test (id INTEGER PRIMARY KEY)"));

        validator.validateIntegrity(jdbi);
    }

    @Test
    @DisplayName("validateIntegrity should pass on empty db")
    void validateIntegrity_shouldPass_when_emptyDb() {
        validator.validateIntegrity(jdbi);
    }

    @Test
    @DisplayName("getSchemaVersion should return 0 when no schema_version table")
    void getSchemaVersion_shouldReturn0_when_noTable() {
        int version = validator.getSchemaVersion(jdbi);

        assertThat(version).isEqualTo(0);
    }

    @Test
    @DisplayName("getSchemaVersion should return correct version when table exists")
    void getSchemaVersion_shouldReturnCorrectVersion_when_tableExists() {
        createSchemaVersionWith(3, "add columns");

        int version = validator.getSchemaVersion(jdbi);

        assertThat(version).isEqualTo(3);
    }

    @Test
    @DisplayName("getSchemaVersion should return max when multiple versions exist")
    void getSchemaVersion_shouldReturnMax_when_multipleVersionsExist() {
        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT    NOT NULL,
                        applied_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            handle.execute("INSERT INTO schema_version(version, description) VALUES (?, ?)", 1, "first");
            handle.execute("INSERT INTO schema_version(version, description) VALUES (?, ?)", 2, "second");
            handle.execute("INSERT INTO schema_version(version, description) VALUES (?, ?)", 3, "third");
        });

        int version = validator.getSchemaVersion(jdbi);

        assertThat(version).isEqualTo(3);
    }

    @Test
    @DisplayName("getSchemaVersion static should return 0 when file not found")
    void getSchemaVersion_static_shouldReturn0_when_fileNotFound() {
        Path nonexistent = tempDir.resolve("noexiste.db");

        int version = SchemaValidator.getSchemaVersion(nonexistent);

        assertThat(version).isEqualTo(0);
    }

    @Test
    @DisplayName("getSchemaVersion static should return version from real file")
    void getSchemaVersion_static_shouldReturnVersion_when_fileExists() {
        Path dbFile = tempDir.resolve("static-test.db");
        Jdbi fileJdbi = Jdbi.create("jdbc:sqlite:" + dbFile);
        fileJdbi.installPlugin(new SQLitePlugin());
        fileJdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT    NOT NULL,
                        applied_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            handle.execute("INSERT INTO schema_version(version, description) VALUES (?, ?)", 2, "test");
        });

        int version = SchemaValidator.getSchemaVersion(dbFile);

        assertThat(version).isEqualTo(2);
    }

    private void createSchemaVersionWith(int version, String description) {
        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT    NOT NULL,
                        applied_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            handle.execute("INSERT INTO schema_version(version, description) VALUES (?, ?)",
                    version, description);
        });
    }
}
