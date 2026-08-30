package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FileScanner {

    private static final Logger logger = LogManager.getLogger(FileScanner.class);

    public List<ScannedFile> scan(Path rootDir, int maxDepth) {
        if (!Files.isDirectory(rootDir)) {
            throw new IllegalArgumentException("root directory not found: " + rootDir);
        }

        ScanVisitor visitor = new ScanVisitor();
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

        final List<ScannedFile> results = new ArrayList<>();
        int processed = 0;
        int excluded = 0;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            processed++;
            String ext = getExtension(file);
            if (ext == null) {
                excluded++;
                return FileVisitResult.CONTINUE;
            }
            FileFormat format = resolveFormat(ext);
            if (format == null) {
                excluded++;
                logger.debug("Unsupported extension (.{}), excluded: {}", ext, file);
                return FileVisitResult.CONTINUE;
            }
            String normalized = normalizePath(file);
            results.add(new ScannedFile(file, normalized, format));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
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

    static String normalizePath(Path file) {
        String raw = file.toString().replace('\\', '/');
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        return Path.of(nfc).normalize().toString().replace('\\', '/');
    }

    public record ScannedFile(Path originalPath, String normalizedPath, FileFormat format) {
    }

    public enum FileFormat {
        PDF, EPUB, MHTML
    }
}
