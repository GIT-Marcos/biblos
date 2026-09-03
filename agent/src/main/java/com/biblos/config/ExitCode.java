package com.biblos.config;

public enum ExitCode {
    SUCCESS(0, "Success"),
    CONFIG_ERROR(1, "Configuration error"),
    DIRECTORY_NOT_FOUND(2, "Directory not found"),
    SCAN_ERROR(3, "Scan error"),
    HASH_ERROR(4, "Hash error"),
    DATABASE_ERROR(5, "Database error"),
    CANCELLED(6, "Cancelled by user");

    private final int code;
    private final String description;

    ExitCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
