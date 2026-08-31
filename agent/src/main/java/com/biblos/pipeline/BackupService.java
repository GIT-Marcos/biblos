package com.biblos.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class BackupService {

    private static final Logger logger = LogManager.getLogger(BackupService.class);

    void backup(Path dbPath) {
        if (!Files.exists(dbPath)) {
            return;
        }

        try {
            long dbSize = Files.size(dbPath);
            long usableSpace = dbPath.toFile().getUsableSpace();
            if (usableSpace < dbSize) {
                logger.warn("Insufficient disk space for backup (need {} bytes, {} available), skipping backup",
                        dbSize, usableSpace);
                return;
            }
        } catch (IOException e) {
            logger.warn("Could not verify disk space, attempting backup anyway: {}", e.getMessage());
        }

        Path bak = Path.of(dbPath + ".bak");
        try {
            Files.copy(dbPath, bak, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backup created: {}", bak);
        } catch (IOException e) {
            logger.warn("Backup failed, continuing without backup: {}", e.getMessage());
        }
    }
}
