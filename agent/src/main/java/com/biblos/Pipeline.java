package com.biblos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

            for (FileScanner.ScannedFile file : files) {
                HashService.HashResult hashResult = hasher.computeHashWithResult(file.originalPath());
                if (hashResult.excluded()) {
                    excluded++;
                    continue;
                }

                String authorName = AuthorInferrer.infer(config.rootDir(), file.originalPath());
                long authorId = db.findOrCreateAuthor(authorName);
                db.insertSource(
                        file.originalPath().getFileName().toString(),
                        file.normalizedPath(),
                        hashResult.hash(),
                        file.format().name(),
                        authorId
                );
                created++;
            }

            logger.info("Foundation complete: {} sources created, {} excluded", created, excluded);
        } catch (IOException e) {
            throw new DatabaseException("failed to create database", e);
        }
    }

    public void reconciliation() {
        logger.info("Starting reconciliation flow");

        backup();

        try (Database db = Database.open(config.dbPath())) {
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

    private void mergeSources(Database db, Source renamed, Source target,
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
        db.updatePath(renamed.id(), newPath, newPathLower);
        db.updateAuthor(renamed.id(), authorId);
        db.updateHash(renamed.id(), renamed.contentHash());
        db.updateMetadata(renamed.id(), year, edition, url);

        db.deleteSource(target.id());

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
        int renames = 0, updates = 0, reactivates = 0, creates = 0, deletes = 0, skipped = 0;

        for (Classification c : classifications.stream()
                .filter(c -> c.operation() == Operation.RENAME).toList()) {
            if (App.isCancelled()) {
                logger.warn("Pipeline cancelled, stopping after {} renames", renames);
                break;
            }
            Source src = c.dbSource();
            FileScanner.ScannedFile file = c.scannedFile();
            String newPath = file.normalizedPath();
            String newPathLower = newPath.toLowerCase(Locale.ROOT);

            Source targetConflict = db.findByPathLower(newPathLower);
            if (targetConflict != null && targetConflict.id() != src.id()) {
                mergeSources(db, src, targetConflict, newPath, newPathLower, c.authorName());
            } else {
                long authorId = db.findOrCreateAuthor(c.authorName());
                db.updatePath(src.id(), newPath, newPathLower);
                db.updateAuthor(src.id(), authorId);
                db.updateHash(src.id(), c.newHash());
                db.updateMetadata(src.id(), src.year(), src.edition(), src.url());
            }
            renames++;
        }

        for (Classification c : classifications.stream()
                .filter(c -> c.operation() == Operation.UPDATE).toList()) {
            if (App.isCancelled()) {
                logger.warn("Pipeline cancelled, stopping after {} updates", updates);
                break;
            }
            db.updateHash(c.dbSource().id(), c.newHash());
            updates++;
        }

        for (Classification c : classifications.stream()
                .filter(c -> c.operation() == Operation.REACTIVATE
                        || c.operation() == Operation.REACTIVATE_UPDATE).toList()) {
            if (App.isCancelled()) {
                logger.warn("Pipeline cancelled, stopping after {} reactivates", reactivates);
                break;
            }
            db.reactivate(c.dbSource().id());
            if (c.operation() == Operation.REACTIVATE_UPDATE) {
                db.updateHash(c.dbSource().id(), c.newHash());
            }
            reactivates++;
        }

        for (Classification c : classifications.stream()
                .filter(c -> c.operation() == Operation.CREATE).toList()) {
            if (App.isCancelled()) {
                logger.warn("Pipeline cancelled, stopping after {} creates", creates);
                break;
            }
            FileScanner.ScannedFile file = c.scannedFile();
            long authorId = db.findOrCreateAuthor(c.authorName());
            db.insertSource(
                    file.originalPath().getFileName().toString(),
                    file.normalizedPath(),
                    c.newHash(),
                    file.format().name(),
                    authorId
            );
            creates++;
        }

        for (Classification c : classifications.stream()
                .filter(c -> c.operation() == Operation.DELETE).toList()) {
            if (App.isCancelled()) {
                logger.warn("Pipeline cancelled, stopping after {} deletes", deletes);
                break;
            }
            db.softDelete(c.dbSource().id());
            deletes++;
        }

        skipped = (int) classifications.stream()
                .filter(c -> c.operation() == Operation.SKIP).count();

        logger.info("Reconciliation complete: {} renames, {} updates, {} reactivates, {} creates, {} deletes, {} skipped",
                renames, updates, reactivates, creates, deletes, skipped);
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
