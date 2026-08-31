package com.biblos.infrastructure;

import java.nio.file.Path;

public record ScannedFile(Path originalPath, String normalizedPath, FileScanner.FileFormat format) {
}
