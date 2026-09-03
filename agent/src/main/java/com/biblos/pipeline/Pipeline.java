package com.biblos.pipeline;

import com.biblos.config.Config;
import com.biblos.domain.AuthorInferrer;
import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

public class Pipeline {

    private static final Logger logger = LogManager.getLogger(Pipeline.class);

    private final Config config;
    private final FileScanner scanner;
    private final HashService hasher;
    private final Classifier classifier;
    private final BackupService backupService;
    private final OperationApplier applier;
    private final BooleanSupplier isCancelled;

    public Pipeline(Config config, BooleanSupplier isCancelled) {
        this.config = config;
        this.isCancelled = isCancelled;
        this.scanner = new FileScanner();
        this.hasher = new HashService(config.timeout(), 500L * 1024 * 1024);
        this.classifier = new Classifier();
        this.backupService = new BackupService();
        this.applier = new OperationApplier();
    }

    public int foundation() {
        logger.info("Starting foundation flow");

        List<ScannedFile> files = scanner.scan(config.rootDir(), config.maxDepth());
        logger.info("Scanned {} files", files.size());

        try (Database db = Database.create(config.dbPath())) {
            int created = 0;
            int excluded = 0;

            List<SourceRecord> batch = new ArrayList<>();

            for (ScannedFile file : files) {
                HashResult hashResult = hasher.computeHashWithResult(file.originalPath());
                if (hashResult.excluded()) {
                    excluded++;
                    continue;
                }

                String authorName = AuthorInferrer.infer(config.rootDir(), file.originalPath());
                long authorId = db.findOrCreateAuthor(authorName);
                String pathLower = file.normalizedPath().toLowerCase(Locale.ROOT);

                batch.add(new SourceRecord(
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
            return excluded;
        } catch (IOException e) {
            throw new DatabaseException("failed to create database", e);
        }
    }

    public int reconciliation() {
        logger.info("Starting reconciliation flow");

        backupService.backup(config.dbPath());
        try (Database db = Database.open(config.dbPath())) {
            db.validateIntegrity();

            List<ScannedFile> files = scanner.scan(config.rootDir(), config.maxDepth());
            logger.info("Scanned {} files", files.size());

            List<Source> allSources = db.findAll();
            Map<String, Source> knownByPath = new HashMap<>();
            Map<String, List<Source>> knownByHash = new HashMap<>();
            for (Source s : allSources) {
                knownByPath.put(s.pathLower(), s);
                knownByHash.computeIfAbsent(s.contentHash(), k -> new ArrayList<>()).add(s);
            }

            List<ScannedFileWithMeta> fsEntries = new ArrayList<>();
            int excluded = 0;
            for (ScannedFile file : files) {
                HashResult hashResult = hasher.computeHashWithResult(file.originalPath());
                if (hashResult.excluded()) {
                    excluded++;
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
                Source dbSource = knownByPath.get(normPath.toLowerCase(Locale.ROOT));

                if (dbSource != null) {
                    matchedDbPaths.add(normPath.toLowerCase(Locale.ROOT));
                    classifier.classifyExisting(dbSource, entry.hash(), entry.authorName(), classifications);
                } else {
                    classifier.classifyNew(entry, knownByHash, claimedIds, classifications);
                }
            }

            for (Source source : knownByPath.values()) {
                if (!matchedDbPaths.contains(source.pathLower()) && source.deletedAt() == null) {
                    classifications.add(new Classification(
                            Operation.DELETE, null, source, null, null));
                }
            }

            classifier.reconcileDeleteCreatePairs(classifications);

            applier.apply(db, classifications, config, isCancelled);
            return excluded;
        }
    }

    public void migration() {
        logger.info("Starting migration flow");
        int versionBefore = SchemaValidator.getSchemaVersion(config.dbPath());
        try (Database db = Database.open(config.dbPath())) {
            SchemaValidator validator = new SchemaValidator();
            int versionAfter = validator.getSchemaVersion(db.getJdbi());
            if (versionAfter > versionBefore) {
                logger.info("Migrated from V{} to V{}", versionBefore, versionAfter);
            } else {
                logger.info("Database is already up to date at V{}", versionAfter);
            }
            logger.info("Migration complete: database at V{}", versionAfter);
        }
    }
}
