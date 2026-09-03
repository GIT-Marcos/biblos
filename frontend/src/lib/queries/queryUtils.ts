import type {Database, SqlValue} from 'sql.js'
import {getCountCache, setCountCache} from '../queryCache'

type BindParams = SqlValue[]

export function queryRows<T>(
    db: Database,
    sql: string,
    params?: BindParams,
): T[] {
    try {
        const stmt = db.prepare(sql)
        if (params) {
            stmt.bind(params)
        }

        const results: T[] = []
        while (stmt.step()) {
            results.push(stmt.getAsObject() as T)
        }
        stmt.free()
        return results
    } catch (error) {
        throw new Error(
            `Error al ejecutar query: ${error instanceof Error ? error.message : String(error)}`,
        )
    }
}

export function queryOne<T>(
    db: Database,
    sql: string,
    params?: BindParams,
): T | null {
    const rows = queryRows<T>(db, sql, params)
    return rows.length > 0 ? rows[0] : null
}

export function queryCount(
    db: Database,
    sql: string,
    params?: BindParams,
): number {
    const cached = getCountCache(sql, params)
    if (cached !== null) return cached

    const result = queryRows<{ count: number }>(db, sql, params)
    const count = result.length > 0 ? result[0].count : 0
    setCountCache(sql, params, count)
    return count
}

export function executeStatement(
    db: Database,
    sql: string,
    params?: BindParams,
): void {
    try {
        db.run('BEGIN TRANSACTION')
        db.run(sql, params)
        db.run('COMMIT')
    } catch (error) {
        db.run('ROLLBACK')
        throw new Error(
            `Error al ejecutar statement: ${error instanceof Error ? error.message : String(error)}`,
        )
    }
}

export function executeStatements(
    db: Database,
    statements: Array<{ sql: string; params?: BindParams }>,
): void {
    try {
        db.run('BEGIN TRANSACTION')
        for (const {sql, params} of statements) {
            db.run(sql, params)
        }
        db.run('COMMIT')
    } catch (error) {
        db.run('ROLLBACK')
        throw new Error(
            `Error al ejecutar statements: ${error instanceof Error ? error.message : String(error)}`,
        )
    }
}
