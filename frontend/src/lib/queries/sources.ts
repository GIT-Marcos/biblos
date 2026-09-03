import type {Database, SqlValue} from 'sql.js'
import type {
    Source,
    SourceQueryParams,
    PaginatedResult,
} from '../../types/database'
import {queryRows, queryOne, queryCount} from './queryUtils'

const PAGE_SIZE_DEFAULT = 50

export function getSources(
    db: Database,
    params: SourceQueryParams,
): PaginatedResult<Source> {
    const {
        page = 1,
        pageSize = PAGE_SIZE_DEFAULT,
        sort = 'name',
        order = 'asc',
        search,
        format,
        authorId,
        tagId,
    } = params

    const conditions: string[] = []
    const values: SqlValue[] = []

    if (search) {
        conditions.push('(s.name LIKE ? OR a.name LIKE ?)')
        values.push(`%${search}%`, `%${search}%`)
    }

    if (format) {
        conditions.push('s.file_format = ?')
        values.push(format)
    }

    if (authorId) {
        conditions.push('s.author_id = ?')
        values.push(authorId)
    }

    if (tagId) {
        conditions.push('s.id IN (SELECT source_id FROM source_tags WHERE tag_id = ?)')
        values.push(tagId)
    }

    const whereClause = conditions.length > 0
        ? `WHERE ${conditions.join(' AND ')}`
        : ''

    const sortColumn = {
        name: 's.name',
        author: 'a.name',
        year: 's.year',
        format: 's.file_format',
    }[sort]

    const countSql = `
    SELECT COUNT(*) as count
    FROM sources s
    LEFT JOIN authors a ON s.author_id = a.id
    ${whereClause}
  `
    const total = queryCount(db, countSql, values)

    const totalPages = Math.ceil(total / pageSize)
    const safePage = Math.min(Math.max(1, page), Math.max(1, totalPages))
    const offset = (safePage - 1) * pageSize

    const dataSql = `
    SELECT s.id, s.name, s.path, s.path_lower, s.content_hash,
           s.file_format, s.author_id, s.year, s.edition, s.url,
           s.deleted_at, s.created_at, s.updated_at,
           a.name as author_name
    FROM sources s
    LEFT JOIN authors a ON s.author_id = a.id
    ${whereClause}
    ORDER BY ${sortColumn} ${order}
    LIMIT ? OFFSET ?
  `
    const data = queryRows<Source>(db, dataSql, [...values, pageSize, offset])

    return {
        data,
        total,
        page: safePage,
        pageSize,
        totalPages,
    }
}

export function getSourceById(db: Database, id: number): Source | null {
    return queryOne<Source>(
        db,
        'SELECT * FROM sources WHERE id = ?',
        [id],
    )
}

export function getSourceTags(
    db: Database,
    sourceId: number,
): { id: number; name: string }[] {
    return queryRows<{ id: number; name: string }>(
        db,
        `SELECT t.id, t.name
     FROM tags t
     INNER JOIN source_tags st ON t.id = st.tag_id
     WHERE st.source_id = ?
     ORDER BY t.name`,
        [sourceId],
    )
}
