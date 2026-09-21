package com.biblos.pipeline;

import com.biblos.domain.Source;
import com.biblos.infrastructure.Database;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

class DuplicateResolver {

    private static final Logger logger = LogManager.getLogger(DuplicateResolver.class);

    int resolve(Database db) {
        return db.withTransaction(handle -> {
            int orphans = handle.execute(
                    "DELETE FROM source_tags WHERE source_id NOT IN (SELECT id FROM sources)");
            if (orphans > 0) {
                logger.warn("Removed {} orphaned source_tags rows", orphans);
            }

            List<Source> all = db.findAll(handle);
            Map<String, List<Source>> byPath = new HashMap<>();
            for (Source s : all) {
                byPath.computeIfAbsent(s.pathLower(), k -> new ArrayList<>()).add(s);
            }

            int merged = 0;
            for (List<Source> group : byPath.values()) {
                if (group.size() < 2) {
                    continue;
                }
                Source canonical = group.stream()
                        .filter(s -> s.deletedAt() == null)
                        .min(Comparator.comparingLong(Source::id))
                        .orElseGet(() -> group.stream()
                                .min(Comparator.comparingLong(Source::id))
                                .orElseThrow());

                Integer year = canonical.year();
                String edition = canonical.edition();
                String url = canonical.url();

                for (Source dup : group) {
                    if (dup.id() == canonical.id()) {
                        continue;
                    }
                    db.transferSourceTags(handle, dup.id(), canonical.id());
                    if (year == null) year = dup.year();
                    if (edition == null) edition = dup.edition();
                    if (url == null) url = dup.url();
                    db.deleteSource(handle, dup.id());
                }

                handle.execute(
                        "UPDATE sources SET year = ?, edition = ?, url = ?, " +
                                "updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                        year, edition, url, canonical.id()
                );
                merged++;
            }

            if (merged > 0) {
                logger.warn("Resolved {} duplicate path groups", merged);
            }
            return merged;
        });
    }
}
