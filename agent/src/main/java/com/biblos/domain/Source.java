package com.biblos.domain;

public record Source(
    long id,
    String name,
    String path,
    String pathLower,
    String contentHash,
    String fileFormat,
    Long authorId,
    Integer year,
    String edition,
    String url,
    String createdAt,
    String updatedAt,
    String deletedAt
) {}
