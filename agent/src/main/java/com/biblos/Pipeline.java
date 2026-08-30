package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Pipeline {

    private static final Logger logger = LogManager.getLogger(Pipeline.class);

    private final Config config;
    private final FileScanner scanner;
    private final HashService hasher;

    public Pipeline(Config config) {
        this.config = config;
        this.scanner = new FileScanner();
        this.hasher = new HashService(config.timeout(), 500L * 1024 * 1024);
    }

    public void foundation() {
        logger.info("Starting foundation flow");

        List<FileScanner.ScannedFile> files = scanner.scan(config.rootDir(), config.maxDepth());
        logger.info("Scanned {} files", files.size());

        try (Database db = Database.create(config.dbPath())) {
            int created = 0;
            int excluded = 0;

            List<Database.SourceRecord> batch = new ArrayList<>();

            for (FileScanner.ScannedFile file : files) {
                HashService.HashResult hashResult = hasher.computeHashWithResult(file.originalPath());
                if (hashResult.excluded()) {
                    excluded++;
                    continue;
                }

                String authorName = AuthorInferrer.infer(config.rootDir(), file.originalPath());
                long authorId = db.findOrCreateAuthor(authorName);
                String pathLower = file.normalizedPath().toLowerCase(Locale.ROOT);

                batch.add(new Database.SourceRecord(
                        file.originalPath().getFileName().toString(),
                        file.normalizedPath(),
                        pathLower,
                        hashResult.hash(),
                        file.format().name(),
                        authorId
                ));

                if (batch.size() >= config.batchSize()) {
                    db.insertSourceBatch(batch);
                    created += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                db.insertSourceBatch(batch);
                created += batch.size();
            }

            logger.info("Foundation complete: {} sources created, {} excluded", created, excluded);
        } catch (IOException e) {
            throw new DatabaseException("failed to create database", e);
        }
    }

    public void reconciliation() {
        logger.info("Starting reconciliation flow");

        try (Database db = Database.open(config.dbPath())) {
            db.validateIntegrity();

            backup();
            List<FileScanner.ScannedFile> files = scanner.scan(config.rootDir(), config.maxDepth());
            logger.info("Scanned {} files", files.size());

            List<Source> allSources = db.findAll();
            Map<String, Source> knownByPath = new HashMap<>();
            Map<String, List<Source>> knownByHash = new HashMap<>();
            for (Source s : allSources) {
                knownByPath.put(s.pathLower(), s);
                knownByHash.computeIfAbsent(s.contentHash(), k -> new ArrayList<>()).add(s);
            }

            List<ScannedFileWithMeta> fsEntries = new ArrayList<>();
            for (FileScanner.ScannedFile file : files) {
                HashService.HashResult hashResult = hasher.computeHashWithResult(file.originalPath());
                if (hashResult.excluded()) {
                    continue;
                }
                String authorName = AuthorInferrer.infer(config.rootDir(), file.originalPath());
                fsEntries.add(new ScannedFileWithMeta(file, hashResult.hash(), authorName));
            }

            Set<String> matchedDbPaths = new HashSet<>();
            Set<Long> claimedIds = new HashSet<>();
            List<Classification> classifications = new ArrayList<>();

            for (ScannedFileWithMeta entry : fsEntries) {
                String normPath = entry.file().normalizedPath();
                Source dbSource = knownByPath.get(normPath);

                if (dbSource != null) {
                    matchedDbPaths.add(normPath);
                    classifyExisting(dbSource, entry.hash(), entry.authorName(), classifications);
                } else {
                    classifyNew(entry, knownByHash, claimedIds, classifications);
                }
            }

            for (Source source : knownByPath.values()) {
                if (!matchedDbPaths.contains(source.pathLower()) && source.deletedAt() == null) {
                    classifications.add(new Classification(
                            Operation.DELETE, null, source, null, null));
                }
            }

            reconcileDeleteCreatePairs(classifications);

            applyOperations(db, classifications);
        }
    }

    public void migration() {
        logger.info("Starting migration flow");
        try (Database db = Database.open(config.dbPath())) {
            logger.info("Migration complete (no structural changes in current version)");
        }
    }

    private void backup() {
        Path dbPath = config.dbPath();
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

        Path bak = Path.of(dbPath.toString() + ".bak");
        try {
            Files.copy(dbPath, bak, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Backup created: {}", bak);
        } catch (IOException e) {
            logger.warn("Backup failed, continuing without backup: {}", e.getMessage());
        }
    }

    private void classifyExisting(Source dbSource, String currentHash, String authorName,
                                  List<Classification> result) {
        if (dbSource.contentHash().equals(currentHash)) {
            if (dbSource.deletedAt() != null) {
                result.add(new Classification(Operation.REACTIVATE, null, dbSource,
                        null, authorName));
            } else {
                result.add(new Classification(Operation.SKIP, null, dbSource,
                        null, authorName));
            }
        } else {
            if (dbSource.deletedAt() != null) {
                result.add(new Classification(Operation.REACTIVATE_UPDATE, null, dbSource,
                        currentHash, authorName));
            } else {
                result.add(new Classification(Operation.UPDATE, null, dbSource,
                        currentHash, authorName));
            }
        }
    }

    private void classifyNew(ScannedFileWithMeta entry, Map<String, List<Source>> knownByHash,
                             Set<Long> claimedIds, List<Classification> result) {
        String currentHash = entry.hash();
        List<Source> candidates = knownByHash.getOrDefault(currentHash, List.of());

        Source match = selectBestMatch(currentHash, entry.file().normalizedPath(), candidates, claimedIds);

        if (match != null) {
            claimedIds.add(match.id());
            result.add(new Classification(Operation.RENAME, entry.file(), match,
                    currentHash, entry.authorName()));
        } else {
            result.add(new Classification(Operation.CREATE, entry.file(), null,
                    currentHash, entry.authorName()));
        }
    }

    private void reconcileDeleteCreatePairs(List<Classification> classifications) {
        Map<String, Classification> pendingDeletesByHash = new HashMap<>();
        for (Classification c : classifications) {
            if (c.operation() == Operation.DELETE) {
                pendingDeletesByHash.put(c.dbSource().contentHash(), c);
            }
        }

        List<Classification> toRemove = new ArrayList<>();
        List<Classification> toAdd = new ArrayList<>();

        for (Classification c : classifications) {
            if (c.operation() != Operation.CREATE || c.scannedFile() == null) {
                continue;
            }

            Classification deleteClass = pendingDeletesByHash.remove(c.newHash());
            if (deleteClass == null) {
                continue;
            }

            Source oldSource = deleteClass.dbSource();
            toRemove.add(deleteClass);
            toRemove.add(c);
            toAdd.add(new Classification(
                    Operation.RENAME,
                    c.scannedFile(),
                    oldSource,
                    c.newHash(),
                    c.authorName()
            ));
        }

        classifications.removeAll(toRemove);
        classifications.addAll(toAdd);
    }

    private void mergeSources(Database db, Handle handle, Source renamed, Source target,
                              String newPath, String newPathLower, String authorName) {
        List<String> targetTags = db.findSourceTags(target.id());
        for (String tag : targetTags) {
            db.addSourceTag(renamed.id(), tag);
        }

        Integer year = renamed.year() != null ? renamed.year() : target.year();
        String edition = renamed.edition() != null ? renamed.edition() : target.edition();
        String url = renamed.url() != null ? renamed.url() : target.url();

        String inferredAuthor = AuthorInferrer.infer(config.rootDir(),
                Path.of(config.rootDir().toString(), newPath.replace("/", File.separator)));

        long authorId = db.findOrCreateAuthor(inferredAuthor);
        handle.execute(
                "UPDATE sources SET path = ?, path_lower = ?, author_id = ?, content_hash = ?, " +
                        "year = ?, edition = ?, url = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                newPath, newPathLower,
                authorId > 0 ? authorId : null,
                renamed.contentHash(),
                year, edition, url,
                renamed.id()
        );

        handle.execute("DELETE FROM sources WHERE id = ?", target.id());

        logger.debug("R10 merge: source {} merged with target {}, tags transferred",
                renamed.id(), target.id());
    }

    private Source selectBestMatch(String contentHash, String expectedPath,
                                   List<Source> candidates, Set<Long> claimedIds) {
        if (candidates.isEmpty()) return null;

        List<Source> active = candidates.stream()
                .filter(s -> s.deletedAt() == null && !claimedIds.contains(s.id()))
                .toList();
        List<Source> orphan = candidates.stream()
                .filter(s -> s.deletedAt() != null && !claimedIds.contains(s.id()))
                .toList();

        List<Source> pool = active.isEmpty() ? orphan : active;
        if (pool.isEmpty()) return null;

        String expectedDir = expectedPath.contains("/")
                ? expectedPath.substring(0, expectedPath.lastIndexOf('/'))
                : "";

        Source best = pool.stream()
                .filter(s -> s.pathLower().startsWith(expectedDir) && !expectedDir.isEmpty())
                .findFirst()
                .orElse(null);

        if (best != null) return best;

        return pool.stream()
                .min(Comparator.comparing(Source::pathLower))
                .orElse(null);
    }

    private void applyOperations(Database db, List<Classification> classifications) {
        List<Classification> renamesList = classifications.stream()
                .filter(c -> c.operation() == Operation.RENAME).toList();
        List<Classification> updatesList = classifications.stream()
                .filter(c -> c.operation() == Operation.UPDATE).toList();
        List<Classification> reactivatesList = classifications.stream()
                .filter(c -> c.operation() == Operation.REACTIVATE
                        || c.operation() == Operation.REACTIVATE_UPDATE).toList();
        List<Classification> createsList = classifications.stream()
                .filter(c -> c.operation() == Operation.CREATE).toList();
        List<Classification> deletesList = classifications.stream()
                .filter(c -> c.operation() == Operation.DELETE).toList();
        int skipped = (int) classifications.stream()
                .filter(c -> c.operation() == Operation.SKIP).count();

        db.withTransaction(handle -> {
            int renames = 0, updates = 0, reactivates = 0, creates = 0, deletes = 0;

            for (Classification c : renamesList) {
                if (App.isCancelled()) {
                    logger.warn("Pipeline cancelled, stopping after {} renames", renames);
                    return null;
                }
                Source src = c.dbSource();
                FileScanner.ScannedFile file = c.scannedFile();
                String newPath = file.normalizedPath();
                String newPathLower = newPath.toLowerCase(Locale.ROOT);

                Source targetConflict = db.findByPathLower(newPathLower);
                if (targetConflict != null && targetConflict.id() != src.id()) {
                    mergeSources(db, handle, src, targetConflict, newPath, newPathLower, c.authorName());
                } else {
                    long authorId = db.findOrCreateAuthor(c.authorName());
                    handle.execute(
                            "UPDATE sources SET path = ?, path_lower = ?, author_id = ?, " +
                                    "content_hash = ?, year = ?, edition = ?, url = ?, " +
                                    "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                            newPath, newPathLower,
                            authorId > 0 ? authorId : null,
                            c.newHash(),
                            src.year(), src.edition(), src.url(),
                            src.id()
                    );
                }
                renames++;
            }

            for (Classification c : updatesList) {
                if (App.isCancelled()) {
                    logger.warn("Pipeline cancelled, stopping after {} updates", updates);
                    return null;
                }
                db.updateHash(c.dbSource().id(), c.newHash());
                updates++;
            }

            List<Long> reactivateIds = new ArrayList<>();
            List<Long> reactivateUpdateIds = new ArrayList<>();
            for (Classification c : reactivatesList) {
                if (App.isCancelled()) {
                    logger.warn("Pipeline cancelled, stopping after {} reactivates", reactivates);
                    return null;
                }
                if (c.operation() == Operation.REACTIVATE_UPDATE) {
                    reactivateUpdateIds.add(c.dbSource().id());
                } else {
                    reactivateIds.add(c.dbSource().id());
                }
                reactivates++;
            }
            if (!reactivateIds.isEmpty()) {
                db.reactivateBatch(handle, reactivateIds);
            }
            for (Classification c : reactivatesList) {
                if (c.operation() == Operation.REACTIVATE_UPDATE) {
                    db.updateHash(c.dbSource().id(), c.newHash());
                }
            }

            List<Database.SourceRecord> createBatch = new ArrayList<>();
            for (Classification c : createsList) {
                if (App.isCancelled()) {
                    logger.warn("Pipeline cancelled, stopping after {} creates", creates);
                    return null;
                }
                FileScanner.ScannedFile file = c.scannedFile();
                long authorId = db.findOrCreateAuthor(c.authorName());
                createBatch.add(new Database.SourceRecord(
                        file.originalPath().getFileName().toString(),
                        file.normalizedPath(),
                        file.normalizedPath().toLowerCase(Locale.ROOT),
                        c.newHash(),
                        file.format().name(),
                        authorId
                ));
                creates++;
            }
            if (!createBatch.isEmpty()) {
                db.insertSourceBatch(handle, createBatch);
            }

            List<Long> deleteIds = deletesList.stream()
                    .map(c -> c.dbSource().id())
                    .toList();
            if (!deleteIds.isEmpty()) {
                db.softDeleteBatch(handle, deleteIds);
                deletes = deleteIds.size();
            }

            logger.info("Reconciliation complete: {} renames, {} updates, {} reactivates, {} creates, {} deletes, {} skipped",
                    renames, updates, reactivates, creates, deletes, skipped);
            return null;
        });
    }

    private record ScannedFileWithMeta(FileScanner.ScannedFile file, String hash, String authorName) {
    }

    private record Classification(
            Operation operation,
            FileScanner.ScannedFile scannedFile,
            Source dbSource,
            String newHash,
            String authorName
    ) {
    }

    enum Operation {
        SKIP, REACTIVATE, UPDATE, RENAME, CREATE, DELETE, REACTIVATE_UPDATE
    }
}
