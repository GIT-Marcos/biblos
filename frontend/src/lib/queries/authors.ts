import type {Database, SqlValue} from 'sql.js'
import type {Author, AuthorQueryParams, PaginatedResult,} from '../../types/database'
import {queryCount, queryOne, queryRows} from './queryUtils'

const PAGE_SIZE_DEFAULT = 50

export function getAuthors(
    db: Database,
    params: AuthorQueryParams,
): PaginatedResult<Author & { count: number }> {
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
        conditions.push('a.name LIKE ?')
        values.push(`%${search}%`)
    }

    const whereClause = conditions.length > 0
        ? `WHERE ${conditions.join(' AND ')}`
        : ''

    const sortColumn = sort === 'count' ? 'source_count' : 'a.name'

    const countSql = `
        SELECT COUNT(*) as count
        FROM authors a
            ${whereClause}
    `
    const total = queryCount(db, countSql, values)

    const totalPages = Math.ceil(total / pageSize)
    const safePage = Math.min(Math.max(1, page), Math.max(1, totalPages))
    const offset = (safePage - 1) * pageSize

    const dataSql = `
        SELECT a.*, COUNT(s.id) as source_count
        FROM authors a
                 LEFT JOIN sources s ON a.id = s.author_id
            ${whereClause}
        GROUP BY a.id
        ORDER BY ${sortColumn} ${order}
            LIMIT ?
        OFFSET ?
    `
    const data = queryRows<Author & { count: number }>(db, dataSql, [...values, pageSize, offset])

    return {
        data,
        total,
        page: safePage,
        pageSize,
        totalPages,
    }
}

export function getAuthorById(db: Database, id: number): (Author & { count: number }) | null {
    return queryOne<Author & { count: number }>(
        db,
        `SELECT a.*, COUNT(s.id) as source_count
         FROM authors a
                  LEFT JOIN sources s ON a.id = s.author_id
         WHERE a.id = ?
         GROUP BY a.id`,
        [id],
    )
}
