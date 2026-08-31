package com.biblos.pipeline;

import com.biblos.infrastructure.ScannedFile;

public record ScannedFileWithMeta(ScannedFile file, String hash, String authorName) {
}
