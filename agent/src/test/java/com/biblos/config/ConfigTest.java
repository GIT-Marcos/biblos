package com.biblos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Config")
class ConfigTest {

    @TempDir
    Path tempDir;

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
    @DisplayName("fromArgs should throw ConfigException when db-path missing")
    void fromArgs_shouldThrowConfigException_when_dbPathMissing() {
        String[] args = {"--root-dir", tempDir.toString()};

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
                .hasMessageContaining("--root-dir is required")
                .hasMessageContaining("--db-path is required");
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
    @DisplayName("fromArgs should throw ConfigException when db-path is a directory")
    void fromArgs_shouldThrowConfigException_when_dbPathIsDirectory() {
        String[] args = {
                "--root-dir", tempDir.toString(),
                "--db-path", tempDir.toString()
        };

        assertThatThrownBy(() -> Config.fromArgs(args))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("points to a directory");
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
}
