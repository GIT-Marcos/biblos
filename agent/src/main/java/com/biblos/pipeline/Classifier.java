package com.biblos.pipeline;

import com.biblos.domain.Operation;
import com.biblos.domain.Source;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class Classifier {

    private static final Logger logger = LogManager.getLogger(Classifier.class);

    void classifyExisting(Source dbSource, String currentHash, String authorName,
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

    void classifyNew(ScannedFileWithMeta entry, Map<String, List<Source>> knownByHash,
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

    void reconcileDeleteCreatePairs(List<Classification> classifications) {
        Map<String, List<Classification>> pendingDeletesByHash = new HashMap<>();
        for (Classification c : classifications) {
            if (c.operation() == Operation.DELETE) {
                pendingDeletesByHash
                        .computeIfAbsent(c.dbSource().contentHash(), k -> new ArrayList<>())
                        .add(c);
            }
        }

        List<Classification> toRemove = new ArrayList<>();
        List<Classification> toAdd = new ArrayList<>();

        for (Classification c : classifications) {
            if (c.operation() != Operation.CREATE || c.scannedFile() == null) {
                continue;
            }

            List<Classification> deleteList = pendingDeletesByHash.get(c.newHash());
            if (deleteList == null || deleteList.isEmpty()) {
                continue;
            }

            Classification deleteClass = deleteList.remove(0);
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

    Source selectBestMatch(String contentHash, String expectedPath,
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
}
