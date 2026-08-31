package com.biblos.pipeline;

import com.biblos.config.Config;
import com.biblos.domain.AuthorInferrer;
import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import com.biblos.infrastructure.Database;
import com.biblos.infrastructure.ScannedFile;
import com.biblos.infrastructure.SourceRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public class OperationApplier {

    private static final Logger logger = LogManager.getLogger(OperationApplier.class);

    void apply(Database db, List<Classification> classifications, Config config,
               BooleanSupplier isCancelled) {
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
                if (isCancelled.getAsBoolean()) {
                    logger.warn("Pipeline cancelled, stopping after {} renames", renames);
                    return null;
                }
                Source src = c.dbSource();
                ScannedFile file = c.scannedFile();
                String newPath = file.normalizedPath();
                String newPathLower = newPath.toLowerCase(Locale.ROOT);

                Source targetConflict = db.findByPathLower(newPathLower);
                if (targetConflict != null && targetConflict.id() != src.id()) {
                    mergeSources(db, handle, src, targetConflict, newPath, newPathLower,
                            c.authorName(), config);
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
                if (isCancelled.getAsBoolean()) {
                    logger.warn("Pipeline cancelled, stopping after {} updates", updates);
                    return null;
                }
                db.updateHash(c.dbSource().id(), c.newHash());
                updates++;
            }

            List<Long> reactivateIds = new ArrayList<>();
            List<Long> reactivateUpdateIds = new ArrayList<>();
            for (Classification c : reactivatesList) {
                if (isCancelled.getAsBoolean()) {
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

            List<SourceRecord> createBatch = new ArrayList<>();
            for (Classification c : createsList) {
                if (isCancelled.getAsBoolean()) {
                    logger.warn("Pipeline cancelled, stopping after {} creates", creates);
                    return null;
                }
                ScannedFile file = c.scannedFile();
                long authorId = db.findOrCreateAuthor(c.authorName());
                createBatch.add(new SourceRecord(
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

    private void mergeSources(Database db, Handle handle, Source renamed, Source target,
                              String newPath, String newPathLower, String authorName,
                              Config config) {
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
}
