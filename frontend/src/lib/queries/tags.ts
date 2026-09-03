import type {Database, SqlValue} from 'sql.js'
import type {
    Tag,
    TagQueryParams,
    PaginatedResult,
} from '../../types/database'
import {queryRows, queryOne, queryCount, executeStatement, executeStatements} from './queryUtils'

const PAGE_SIZE_DEFAULT = 50

export function getTags(
    db: Database,
    params: TagQueryParams,
): PaginatedResult<Tag & { count: number }> {
    const {
        page = 1,
        pageSize = PAGE_SIZE_DEFAULT,
        sort = 'name',
        order = 'asc',
        search,
    } = params

    const conditions: string[] = []
    const values: SqlValue[] = []

    if (search) {
        conditions.push('t.name LIKE ?')
        values.push(`%${search}%`)
    }

    const whereClause = conditions.length > 0
        ? `WHERE ${conditions.join(' AND ')}`
        : ''

    const sortColumn = sort === 'count' ? 'tag_count' : 't.name'

    const countSql = `
    SELECT COUNT(*) as count
    FROM tags t
    ${whereClause}
  `
    const total = queryCount(db, countSql, values)

    const totalPages = Math.ceil(total / pageSize)
    const safePage = Math.min(Math.max(1, page), Math.max(1, totalPages))
    const offset = (safePage - 1) * pageSize

    const dataSql = `
    SELECT t.*, COUNT(st.source_id) as tag_count
    FROM tags t
    LEFT JOIN source_tags st ON t.id = st.tag_id
    ${whereClause}
    GROUP BY t.id
    ORDER BY ${sortColumn} ${order}
    LIMIT ? OFFSET ?
  `
    const data = queryRows<Tag & { count: number }>(db, dataSql, [...values, pageSize, offset])

    return {
        data,
        total,
        page: safePage,
        pageSize,
        totalPages,
    }
}

export function createTag(db: Database, name: string): Tag {
    executeStatement(
        db,
        'INSERT INTO tags (name) VALUES (?)',
        [name],
    )

    const tag = queryOne<Tag>(
        db,
        'SELECT * FROM tags WHERE name = ?',
        [name],
    )

    if (!tag) {
        throw new Error('Error al crear el tag')
    }

    return tag
}

export function updateTag(db: Database, id: number, name: string): void {
    executeStatement(
        db,
        'UPDATE tags SET name = ? WHERE id = ?',
        [name, id],
    )
}

export function deleteTag(db: Database, id: number): void {
    executeStatements(db, [
        {sql: 'DELETE FROM source_tags WHERE tag_id = ?', params: [id]},
        {sql: 'DELETE FROM tags WHERE id = ?', params: [id]},
    ])
}

export function getTagById(db: Database, id: number): Tag | null {
    return queryOne<Tag>(
        db,
        'SELECT * FROM tags WHERE id = ?',
        [id],
    )
}
