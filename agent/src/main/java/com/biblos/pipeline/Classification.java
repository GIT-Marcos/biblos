package com.biblos.pipeline;

import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.FileScanner;

public record Classification(
        Operation operation,
        FileScanner.ScannedFile scannedFile,
        Source dbSource,
        String newHash,
        String authorName
) {
}
