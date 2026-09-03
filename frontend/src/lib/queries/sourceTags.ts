import type {Database} from 'sql.js'
import {executeStatement} from './queryUtils'

export function assignTagToSource(
    db: Database,
    sourceId: number,
    tagId: number,
): void {
    const existing = db.exec(
        'SELECT 1 FROM source_tags WHERE source_id = ? AND tag_id = ?',
        [sourceId, tagId],
    )

    if (existing.length === 0 || existing[0].values.length === 0) {
        executeStatement(
            db,
            'INSERT INTO source_tags (source_id, tag_id) VALUES (?, ?)',
            [sourceId, tagId],
        )
    }
}

export function removeTagFromSource(
    db: Database,
    sourceId: number,
    tagId: number,
): void {
    executeStatement(
        db,
        'DELETE FROM source_tags WHERE source_id = ? AND tag_id = ?',
        [sourceId, tagId],
    )
}
