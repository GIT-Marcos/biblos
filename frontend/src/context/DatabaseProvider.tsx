import {useCallback, useMemo, useState, type ReactNode} from 'react'
import type {Database} from 'sql.js'
import {DatabaseContext, type DatabaseContextValue, type DatabaseStatus} from './DatabaseContext'
import {initDatabase, createDatabase, validateSchema} from '../lib/sql'

const WARNING_DB_SIZE_BYTES = 50 * 1024 * 1024
const MAX_DB_SIZE_BYTES = 100 * 1024 * 1024
const MIN_DB_SIZE_BYTES = 100
const SQLITE_MAGIC = 'SQLite format 3\0'

interface DatabaseProviderProps {
    children: ReactNode
}

export function DatabaseProvider({children}: DatabaseProviderProps) {
    const [db, setDb] = useState<Database | null>(null)
    const [status, setStatus] = useState<DatabaseStatus>('idle')
    const [error, setError] = useState<string | null>(null)
    const [fileName, setFileName] = useState<string | null>(null)
    const [pendingFile, setPendingFile] = useState<File | null>(null)

    const loadDatabase = useCallback(async (file: File) => {
        if (!file.name.endsWith('.db') && !file.name.endsWith('.sqlite')) {
            setError('El archivo debe tener extensión .db o .sqlite')
            return
        }

        if (file.size < MIN_DB_SIZE_BYTES) {
            setError('El archivo está vacío o es demasiado pequeño')
            return
        }

        if (file.size > MAX_DB_SIZE_BYTES) {
            setError(`El archivo supera 100MB (${Math.round(file.size / 1024 / 1024)}MB). No se puede cargar.`)
            setStatus('error')
            return
        }

        if (file.size > WARNING_DB_SIZE_BYTES) {
            setPendingFile(file)
            setError(`Advertencia: El archivo es muy grande (${Math.round(file.size / 1024 / 1024)}MB). Puede causar problemas de memoria.`)
            setStatus('warning')
            return
        }

        setStatus('loading')
        setError(null)

        try {
            await initDatabase()

            const arrayBuffer = await file.arrayBuffer()
            const uint8Array = new Uint8Array(arrayBuffer)

            const header = new TextDecoder().decode(uint8Array.slice(0, 16))
            if (!header.startsWith(SQLITE_MAGIC)) {
                setError('El archivo no es una base de datos SQLite válida')
                setStatus('idle')
                return
            }

            const database = createDatabase(uint8Array)

            const validation = validateSchema(database)
            if (!validation.valid) {
                const missing = validation.missingTables.length > 0
                    ? `Tablas faltantes: ${validation.missingTables.join(', ')}`
                    : `Columnas faltantes: ${Object.entries(validation.missingColumns)
                        .map(([table, cols]) => `${table}(${cols.join(', ')})`)
                        .join(', ')}`

                setError(`Esquema incompatible. ${missing}. Actualice su DB desde el último agent.`)
                database.close()
                setStatus('idle')
                return
            }

            setDb(database)
            setFileName(file.name)
            setStatus('ready')
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err)
            setError(`Error al cargar la base de datos: ${message}`)
            setStatus('error')
        }
    }, [])

    const confirmLoad = useCallback(async () => {
        if (!pendingFile) return

        const file = pendingFile
        setPendingFile(null)
        setError(null)
        setStatus('loading')

        try {
            await initDatabase()

            const arrayBuffer = await file.arrayBuffer()
            const uint8Array = new Uint8Array(arrayBuffer)

            const header = new TextDecoder().decode(uint8Array.slice(0, 16))
            if (!header.startsWith(SQLITE_MAGIC)) {
                setError('El archivo no es una base de datos SQLite válida')
                setStatus('idle')
                return
            }

            const database = createDatabase(uint8Array)

            const validation = validateSchema(database)
            if (!validation.valid) {
                const missing = validation.missingTables.length > 0
                    ? `Tablas faltantes: ${validation.missingTables.join(', ')}`
                    : `Columnas faltantes: ${Object.entries(validation.missingColumns)
                        .map(([table, cols]) => `${table}(${cols.join(', ')})`)
                        .join(', ')}`

                setError(`Esquema incompatible. ${missing}. Actualice su DB desde el último agent.`)
                database.close()
                setStatus('idle')
                return
            }

            setDb(database)
            setFileName(file.name)
            setStatus('ready')
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err)
            setError(`Error al cargar la base de datos: ${message}`)
            setStatus('error')
        }
    }, [pendingFile])

    const cancelLoad = useCallback(() => {
        setPendingFile(null)
        setError(null)
        setStatus('idle')
    }, [])

    const closeDatabase = useCallback(() => {
        if (db) {
            db.close()
            setDb(null)
            setFileName(null)
            setStatus('idle')
            setError(null)
        }
    }, [db])

    const clearError = useCallback(() => {
        setError(null)
    }, [])

    const value: DatabaseContextValue = useMemo(
        () => ({
            db,
            status,
            error,
            fileName,
            loadDatabase,
            closeDatabase,
            clearError,
            confirmLoad,
            cancelLoad,
            pendingFileName: pendingFile?.name ?? null,
        }),
        [db, status, error, fileName, loadDatabase, closeDatabase, clearError, confirmLoad, cancelLoad, pendingFile],
    )

    return (
        <DatabaseContext value={value}>
            {children}
        </DatabaseContext>
    )
}
