package com.biblos.infrastructure;

public record SourceRecord(String name, String path, String pathLower,
                           String contentHash, String fileFormat, long authorId) {
}
