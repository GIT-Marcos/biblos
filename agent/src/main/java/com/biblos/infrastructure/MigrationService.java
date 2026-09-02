package com.biblos.infrastructure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MigrationService {

    private static final Logger logger = LogManager.getLogger(MigrationService.class);

    private static final Pattern MIGRATION_PATTERN = Pattern.compile("V(\\d{3})__(.+)\\.sql");

    public void applyMigrations(Jdbi jdbi) {
        jdbi.useHandle(handle -> {
            handle.execute("PRAGMA foreign_keys = ON");
            handle.execute("PRAGMA busy_timeout = 5000");

            handle.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT    NOT NULL,
                        applied_at  TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            int currentVersion = handle.createQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")
                    .mapTo(Integer.class)
                    .one();

            List<MigrationFile> migrations = scanMigrationFiles();
            migrations.sort(Comparator.comparing(MigrationFile::version));

            for (MigrationFile m : migrations) {
                if (m.version() <= currentVersion) {
                    continue;
                }
                applySingleMigration(handle, m);
            }
        });
    }

    private void applySingleMigration(Handle handle, MigrationFile m) {
        handle.begin();
        try {
            logger.info("Applying migration: {}", m.filename());
            String sql = readMigrationResource(m.filename());
            for (String statement : sql.split(";")) {
                String trimmed = statement.strip();
                if (!trimmed.isEmpty()) {
                    handle.execute(trimmed);
                }
            }
            handle.execute(
                    "INSERT INTO schema_version(version, description) VALUES (?, ?)",
                    m.version(), m.description()
            );
            handle.commit();
            logger.info("Applied migration V{}", m.version());
        } catch (Exception e) {
            handle.rollback();
            throw new DatabaseException("Migration failed: " + m.filename(), e);
        }
    }

    private record MigrationFile(int version, String description, String filename) {
    }

    // Desde un fat JAR los resources están dentro del JAR y la URI es
    // "jar:file:..." que no se puede convertir a filesystem Path.
    // Esto detecta el protocolo y usa la estrategia correcta.
    private List<MigrationFile> scanMigrationFiles() {
        List<MigrationFile> result = new ArrayList<>();
        try {
            var dir = getClass().getResource("/db/migration");
            if (dir == null) return result;

            String protocol = dir.getProtocol();
            if ("file".equals(protocol)) {
                scanFilesystem(Path.of(dir.toURI()), result);
            } else if ("jar".equals(protocol)) {
                scanJar(dir, result);
            }
        } catch (Exception e) {
            throw new DatabaseException("failed to scan migration files", e);
        }
        result.sort(Comparator.comparing(MigrationFile::version));
        return result;
    }

    private void scanFilesystem(Path fsPath, List<MigrationFile> result) throws IOException {
        try (var stream = Files.list(fsPath)) {
            stream.forEach(p -> {
                Matcher m = MIGRATION_PATTERN.matcher(p.getFileName().toString());
                if (m.matches()) {
                    int version = Integer.parseInt(m.group(1));
                    String description = m.group(2);
                    result.add(new MigrationFile(version, description, p.getFileName().toString()));
                }
            });
        }
    }

    private void scanJar(URL jarUrl, List<MigrationFile> result) throws IOException {
        String path = jarUrl.getPath();
        String jarPath = path.substring(5, path.indexOf("!"));
        try (JarFile jar = new JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("db/migration/") && name.endsWith(".sql")) {
                    String filename = name.substring("db/migration/".length());
                    Matcher m = MIGRATION_PATTERN.matcher(filename);
                    if (m.matches()) {
                        int version = Integer.parseInt(m.group(1));
                        String description = m.group(2);
                        result.add(new MigrationFile(version, description, filename));
                    }
                }
            }
        }
    }

    private String readMigrationResource(String filename) {
        try (InputStream is = getClass().getResourceAsStream("/db/migration/" + filename)) {
            if (is == null) {
                throw new DatabaseException("migration file not found: " + filename);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DatabaseException("failed to read migration: " + filename, e);
        }
    }
}
