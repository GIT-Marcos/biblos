import type {Database, SqlJsStatic} from 'sql.js'
import initSqlJs from 'sql.js'
import type {SchemaValidation} from '../types/database'

let sqlInstance: SqlJsStatic | null = null

export async function initDatabase(wasmUrl = '/sql-wasm.wasm'): Promise<void> {
    if (sqlInstance) return

    try {
        sqlInstance = await initSqlJs({
            locateFile: (file: string) => wasmUrl.replace('sql-wasm.wasm', file),
        })
    } catch (error) {
        throw new Error(
            `No se pudo cargar el motor SQLite (WebAssembly). ` +
            `Verifique que su navegador soporta WASM o desactive bloqueadores de anuncios. ` +
            `Detalle: ${error instanceof Error ? error.message : String(error)}`,
        )
    }
}

export function createDatabase(data: Uint8Array): Database {
    if (!sqlInstance) {
        throw new Error('sql.js no está inicializado. Llame a initDatabase() primero.')
    }

    try {
        return new sqlInstance.Database(data)
    } catch (error) {
        throw new Error(
            `El archivo no es una base de datos SQLite válida. ` +
            `Detalle: ${error instanceof Error ? error.message : String(error)}`,
        )
    }
}

export function validateSchema(db: Database): SchemaValidation {
    const requiredTables = ['sources', 'authors', 'tags', 'source_tags']
    const requiredColumns: Record<string, string[]> = {
        sources: ['id', 'name', 'path', 'path_lower', 'content_hash', 'file_format', 'author_id', 'created_at', 'updated_at'],
        authors: ['id', 'name'],
        tags: ['id', 'name'],
        source_tags: ['source_id', 'tag_id'],
    }

    const missingTables: string[] = []
    const missingColumns: Record<string, string[]> = {}

    const tablesResult = db.exec("SELECT name FROM sqlite_master WHERE type='table'")
    const existingTables = new Set(
        tablesResult.length > 0
            ? tablesResult[0].values.map((row) => String(row[0]))
            : [],
    )

    for (const table of requiredTables) {
        if (!existingTables.has(table)) {
            missingTables.push(table)
            continue
        }

        const columnsResult = db.exec(`PRAGMA table_info(${table})`)
        const existingColumns = new Set(
            columnsResult.length > 0
                ? columnsResult[0].values.map((row) => String(row[1]))
                : [],
        )

        const tableMissing: string[] = []
        for (const col of requiredColumns[table] ?? []) {
            if (!existingColumns.has(col)) {
                tableMissing.push(col)
            }
        }

        if (tableMissing.length > 0) {
            missingColumns[table] = tableMissing
        }
    }

    return {
        valid: missingTables.length === 0 && Object.keys(missingColumns).length === 0,
        missingTables,
        missingColumns,
    }
}

export type {Database}
