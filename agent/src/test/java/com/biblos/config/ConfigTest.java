package com.biblos.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Config")
class ConfigTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        Files.createFile(tempDir.resolve("test.db"));
    }

    @Test
    @DisplayName("fromArgs should create config when valid args provided")
    void fromArgs_shouldCreateConfig_when_validArgs() {
        String[] args = {"--root-dir", tempDir.toString(), "--db-path", tempDir.resolve("test.db").toString()};

        Config config = Config.fromArgs(args);

        assertThat(config.rootDir()).isEqualTo(tempDir);
        assertThat(config.dbPath()).isEqualTo(tempDir.resolve("test.db"));
    }

    @Test
    @DisplayName("fromArgs should use defaults when optional args missing")
    void fromArgs_shouldUseDefaults_when_optionalArgsMissing() {
        String[] args = {"--root-dir", tempDir.toString(), "--db-path", tempDir.resolve("test.db").toString()};

        Config config = Config.fromArgs(args);

        assertThat(config.flow()).isEqualTo(Config.Flow.RECONCILIATION);
        assertThat(config.maxDepth()).isEqualTo(10);
        assertThat(config.batchSize()).isEqualTo(50);
        assertThat(config.timeout()).isEqualTo(30);
    }

    @Test
    @DisplayName("fromArgs should parse all explicit args")
    void fromArgs_shouldParseAllArgs_when_allProvided() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", tempDir.resolve("out.db").toString(),
                "--flow", "foundation",
                "--max-depth", "5",
                "--batch-size", "100",
                "--timeout", "60"
        };

        Config config = Config.fromArgs(args);

        assertThat(config.flow()).isEqualTo(Config.Flow.FOUNDATION);
        assertThat(config.maxDepth()).isEqualTo(5);
        assertThat(config.batchSize()).isEqualTo(100);
        assertThat(config.timeout()).isEqualTo(60);
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when root-dir missing")
    void fromArgs_shouldThrowConfigException_when_rootDirMissing() {
        String[] args = {"--db-path", tempDir.resolve("test.db").toString()};

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--root-dir is required");
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when db-path missing for reconciliation")
    void fromArgs_shouldThrowConfigException_when_dbPathMissingForReconciliation() {
        String[] args = {"--root-dir", tempDir.toString(), "--flow", "reconciliation"};

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--db-path is required");
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when db-path missing for migration")
    void fromArgs_shouldThrowConfigException_when_dbPathMissingForMigration() {
        String[] args = {"--root-dir", tempDir.toString(), "--flow", "migration"};

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--db-path is required");
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when both required args missing")
    void fromArgs_shouldThrowConfigException_when_bothRequiredArgsMissing() {
        String[] args = {};

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--root-dir is required");
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when flow is invalid")
    void fromArgs_shouldThrowConfigException_when_invalidFlow() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", tempDir.resolve("test.db").toString(),
                "--flow", "invalido"
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("invalid flow")
                .hasMessageContaining("invalido");
    }

    @Test
    @DisplayName("fromArgs should throw DirectoryNotFoundException when root-dir does not exist")
    void fromArgs_shouldThrowDirectoryNotFound_when_rootDirNotExists() {
        String[] args = {
                "--root-dir", "/nonexistent_dir_12345",
                "--db-path", tempDir.resolve("test.db").toString()
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(DirectoryNotFoundException.class)
                .hasMessageContaining("root directory not found");
    }

    @Test
    @DisplayName("fromArgs should parse --key=value syntax")
    void fromArgs_shouldParseEqualSyntax_when_keyEqualsValue() {
        Path dbPath = tempDir.resolve("test.db");
        String[] args = {
                "--root-dir=" + tempDir,
                "--db-path=" + dbPath,
                "--flow=migration"
        };

        Config config = Config.fromArgs(args);

        assertThat(config.rootDir()).isEqualTo(tempDir);
        assertThat(config.dbPath()).isEqualTo(dbPath);
        assertThat(config.flow()).isEqualTo(Config.Flow.MIGRATION);
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when db-path is a directory for reconciliation")
    void fromArgs_shouldThrowConfigException_when_dbPathIsDirectoryForReconciliation() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", tempDir.toString(),
                "--flow", "reconciliation"
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("points to a directory");
    }

    @Test
    @DisplayName("fromArgs should accept db-path as directory when foundation flow")
    void fromArgs_shouldAcceptDbPath_when_foundationFlow() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--flow", "foundation"
        };

        Config config = Config.fromArgs(args);

        assertThat(config.flow()).isEqualTo(Config.Flow.FOUNDATION);
        assertThat(config.dbPath()).isEqualTo(tempDir.resolve("biblos.db"));
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when max-depth is not a number")
    void fromArgs_shouldThrowConfigException_when_maxDepthNotNumber() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", tempDir.resolve("test.db").toString(),
                "--max-depth", "abc"
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--max-depth must be a number");
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when max-depth is zero")
    void fromArgs_shouldThrowConfigException_when_maxDepthIsZero() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", tempDir.resolve("test.db").toString(),
                "--max-depth", "0"
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--max-depth must be > 0");
    }

    @Test
    @DisplayName("fromArgs should accept all valid flow values")
    void fromArgs_shouldAcceptAllValidFlows() {
        for (Config.Flow flow : Config.Flow.values()) {
            String[] args = {
                    "--root-dir", tempDir.toString(),
                    "--db-path", tempDir.resolve("test.db").toString(),
                    "--flow", flow.name().toLowerCase()
            };

            Config config = Config.fromArgs(args);

            assertThat(config.flow()).isEqualTo(flow);
        }
    }

    @Test
    @DisplayName("fromArgs should use explicit db-path when foundation flow and db-path provided")
    void fromArgs_shouldUseExplicitDbPath_when_foundationFlowAndDbPathProvided() {
        Path explicitPath = tempDir.resolve("custom.db");
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", explicitPath.toString(),
                "--flow", "foundation"
        };

        Config config = Config.fromArgs(args);

        assertThat(config.flow()).isEqualTo(Config.Flow.FOUNDATION);
        assertThat(config.dbPath()).isEqualTo(explicitPath);
    }

    @Test
    @DisplayName("fromArgs should default db-path to root-dir/biblos.db when foundation flow and db-path missing")
    void fromArgs_shouldDefaultDbPath_when_foundationFlowAndDbPathMissing() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--flow", "foundation"
        };

        Config config = Config.fromArgs(args);

        assertThat(config.flow()).isEqualTo(Config.Flow.FOUNDATION);
        assertThat(config.dbPath()).isEqualTo(tempDir.resolve("biblos.db"));
    }

    @Test
    @DisplayName("fromArgs should accept non-existent db-path when foundation flow")
    void fromArgs_shouldAcceptNonExistentDbPath_when_foundationFlow() {
        Path nonExistentPath = tempDir.resolve("not-exists-yet.db");
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", nonExistentPath.toString(),
                "--flow", "foundation"
        };

        Config config = Config.fromArgs(args);

        assertThat(config.dbPath()).isEqualTo(nonExistentPath);
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when db-path does not exist for reconciliation")
    void fromArgs_shouldThrowConfigException_when_dbPathDoesNotExistForReconciliation() {
        Path nonExistentPath = tempDir.resolve("not-exists.db");
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", nonExistentPath.toString(),
                "--flow", "reconciliation"
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--db-path does not exist");
    }

    @Test
    @DisplayName("fromArgs should throw ConfigException when db-path does not exist for migration")
    void fromArgs_shouldThrowConfigException_when_dbPathDoesNotExistForMigration() {
        Path nonExistentPath = tempDir.resolve("not-exists.db");
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", nonExistentPath.toString(),
                "--flow", "migration"
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("--db-path does not exist");
    }
}
