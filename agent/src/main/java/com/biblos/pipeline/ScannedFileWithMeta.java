package com.biblos.pipeline;

import com.biblos.infrastructure.FileScanner;

public record ScannedFileWithMeta(FileScanner.ScannedFile file, String hash, String authorName) {
}
