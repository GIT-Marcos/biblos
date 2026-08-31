package com.biblos.infrastructure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.*;

public class HashService {

    private static final Logger logger = LogManager.getLogger(HashService.class);

    private static final int BUFFER_SIZE = 8192;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_MAX_SIZE = 500L * 1024 * 1024;
    private static final int UNC_TIMEOUT_SECONDS = 300;

    private final int timeoutSeconds;
    private final long maxSizeBytes;

    public HashService() {
        this(DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_SIZE);
    }

    public HashService(int timeoutSeconds, long maxSizeBytes) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxSizeBytes = maxSizeBytes;
    }

    public String computeHash(Path file) {
        HashResult result = computeHashWithResult(file);
        return result.hash();
    }

    public HashResult computeHashWithResult(Path file) {
        // H7: long path (>260 chars on Windows)
        Path resolved = resolveLongPath(file);

        // H1: empty file
        try {
            long sizeBefore = Files.size(resolved);
            if (sizeBefore == 0) {
                logger.warn("Empty file, excluded from pipeline: {}", file);
                return excluded("empty file");
            }

            // H6: UNC path detection
            int effectiveTimeout = isUncPath(resolved) ? UNC_TIMEOUT_SECONDS : timeoutSeconds;
            if (effectiveTimeout != timeoutSeconds) {
                logger.warn("UNC path detected, using extended timeout ({}s): {}", effectiveTimeout, file);
            }

            // H4: timeout via ExecutorService
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> future = executor.submit(() -> doHash(resolved));
                String hash = future.get(effectiveTimeout, TimeUnit.SECONDS);

                // H5: write-race detection
                long sizeAfter = Files.size(resolved);
                if (sizeAfter != sizeBefore) {
                    logger.warn("Write-race detected for {}: size before={}, after={}",
                            file, sizeBefore, sizeAfter);
                }

                return new HashResult(hash, false, null);
            } catch (TimeoutException e) {
                logger.warn("Timeout computing hash ({}s): {}", effectiveTimeout, file);
                return excluded("timeout");
            } catch (Exception e) {
                logger.warn("Error computing hash: {}", file, e);
                return excluded("error: " + e.getMessage());
            } finally {
                executor.shutdownNow();
            }
        } catch (IOException e) {
            // H2: file locked or unreadable
            logger.warn("I/O error computing hash, excluded: {}", file, e);
            return excluded("I/O error: " + e.getMessage());
        }
    }

    private String doHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(fis, digest)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (dis.read(buffer) != -1) {
                // streaming: buffer reused, file never loaded entirely in memory
            }
        }
        return bytesToHex(digest.digest());
    }

    private static Path resolveLongPath(Path path) {
        String pathStr = path.toString();
        if (pathStr.length() > 260 && !pathStr.startsWith("\\\\?\\")) {
            return Path.of("\\\\?\\" + pathStr);
        }
        return path;
    }

    private static boolean isUncPath(Path path) {
        return path.toString().startsWith("\\\\");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static HashResult excluded(String reason) {
        return new HashResult(null, true, reason);
    }

    public record HashResult(String hash, boolean excluded, String reason) {
    }
}
