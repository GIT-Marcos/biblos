import {createContext} from 'react'
import type {Database} from 'sql.js'

export type DatabaseStatus =
    | 'idle'
    | 'loading'
    | 'ready'
    | 'error'
    | 'warning'

export interface DatabaseContextValue {
    db: Database | null
    status: DatabaseStatus
    error: string | null
    fileName: string | null
    loadProgress: number
    otherTabsActive: boolean
    loadDatabase: (file: File) => Promise<void>
    closeDatabase: () => void
    clearError: () => void
    confirmLoad: () => Promise<void>
    cancelLoad: () => void
    pendingFileName: string | null
    invalidateCountCache: () => void
}

export const DatabaseContext = createContext<DatabaseContextValue | null>(null)
