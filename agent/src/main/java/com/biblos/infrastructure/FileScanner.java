package com.biblos.infrastructure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.*;

public class FileScanner {

    private static final Logger logger = LogManager.getLogger(FileScanner.class);

    private static final Map<String, String> SIMILAR_EXTENSIONS = Map.of(
            "mht", "mhtml"
    );

    public List<ScannedFile> scan(Path rootDir, int maxDepth) {
        return scan(rootDir, maxDepth, Set.of());
    }

    public List<ScannedFile> scan(Path rootDir, int maxDepth, Set<Path> excludedPaths) {
        if (!Files.isDirectory(rootDir)) {
            throw new IllegalArgumentException("root directory not found: " + rootDir);
        }

        Set<String> excludedNormalized = excludedPaths.stream()
                .map(p -> p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        ScanVisitor visitor = new ScanVisitor(excludedNormalized);
        try {
            Files.walkFileTree(rootDir, Set.of(), maxDepth, visitor);
        } catch (IOException e) {
            throw new ScanException("error scanning directory: " + rootDir, e);
        }

        logger.info("Scan complete: {} files processed, {} excluded, {} found",
                visitor.processed, visitor.excluded, visitor.results.size());

        return visitor.results;
    }

    private class ScanVisitor extends SimpleFileVisitor<Path> {

        private final Set<String> excludedNormalized;
        final List<ScannedFile> results = new ArrayList<>();
        int processed = 0;
        int excluded = 0;

        ScanVisitor(Set<String> excludedNormalized) {
            this.excludedNormalized = excludedNormalized;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            processed++;

            if (Files.isSymbolicLink(file)) {
                logger.warn("Symlink detected, excluded: {}", file);
                excluded++;
                return FileVisitResult.CONTINUE;
            }

            String filename = file.getFileName().toString();
            if (filename.length() > 255) {
                logger.warn("Filename exceeds 255 characters ({}), excluded: {}",
                        filename.length(), file);
                excluded++;
                return FileVisitResult.CONTINUE;
            }

            String normalizedAbs = file.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (excludedNormalized.contains(normalizedAbs)) {
                logger.debug("Excluded by configuration: {}", file);
                excluded++;
                return FileVisitResult.CONTINUE;
            }

            String ext = getExtension(file);
            if (ext == null) {
                logger.warn("No file extension, excluded: {}", file);
                excluded++;
                return FileVisitResult.CONTINUE;
            }
            FileFormat format = resolveFormat(ext);
            if (format == null) {
                warnUnsupportedExtension(file, ext);
                excluded++;
                return FileVisitResult.CONTINUE;
            }
            String normalized = normalizePath(file);
            results.add(new ScannedFile(file, normalized, format));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String normalizedAbs = dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (excludedNormalized.contains(normalizedAbs)) {
                logger.debug("Excluding directory: {}", dir);
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            logger.warn("Inaccessible path, skipped: {}", file);
            excluded++;
            return FileVisitResult.SKIP_SUBTREE;
        }
    }

    static String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private static FileFormat resolveFormat(String ext) {
        return switch (ext) {
            case "pdf" -> FileFormat.PDF;
            case "epub" -> FileFormat.EPUB;
            case "mhtml" -> FileFormat.MHTML;
            default -> null;
        };
    }

    private void warnUnsupportedExtension(Path file, String ext) {
        String suggested = SIMILAR_EXTENSIONS.get(ext);
        if (suggested != null) {
            logger.warn("Unsupported extension .{} (did you mean .{}?): {}",
                    ext, suggested, file);
            return;
        }

        logger.debug("Unsupported extension .{}, excluded: {}", ext, file);
    }

    static String normalizePath(Path file) {
        String raw = file.toString().replace('\\', '/');
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        return Path.of(nfc).normalize().toString().replace('\\', '/');
    }

    public enum FileFormat {
        PDF, EPUB, MHTML
    }
}
