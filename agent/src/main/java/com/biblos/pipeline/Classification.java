package com.biblos.pipeline;

import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.ScannedFile;

public record Classification(
        Operation operation,
        ScannedFile scannedFile,
        Source dbSource,
        String newHash,
        String authorName
) {
}
