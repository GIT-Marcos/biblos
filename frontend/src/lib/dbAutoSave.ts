import {useEffect, useRef, useState} from 'react'
import type {Database} from 'sql.js'
import {exportDatabase, toBase64, fromBase64} from './dbExporter'
import {createDatabase} from './sql'

const AUTOSAVE_KEY = 'biblos_autosave'
const AUTOSAVE_TIMESTAMP_KEY = 'biblos_autosave_timestamp'
const AUTOSAVE_INTERVAL_MS = 30_000
const MAX_AUTOSAVE_SIZE_BYTES = 5 * 1024 * 1024

export type AutoSaveStatus = 'idle' | 'saving' | 'saved' | 'error' | 'storage_full'

interface UseAutoSaveResult {
    status: AutoSaveStatus
    lastSaved: Date | null
    hasBackup: boolean
    restoreBackup: () => Database | null
    clearBackup: () => void
}

export function useAutoSave(db: Database | null): UseAutoSaveResult {
    const [status, setStatus] = useState<AutoSaveStatus>('idle')
    const [lastSaved, setLastSaved] = useState<Date | null>(null)
    const [hasBackup, setHasBackup] = useState(false)
    const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

    useEffect(() => {
        const stored = localStorage.getItem(AUTOSAVE_KEY)
        if (stored) {
            setHasBackup(true)
            const timestamp = localStorage.getItem(AUTOSAVE_TIMESTAMP_KEY)
            setLastSaved(timestamp ? new Date(timestamp) : null)
        }
    }, [])

    useEffect(() => {
        if (!db) {
            if (intervalRef.current) {
                clearInterval(intervalRef.current)
                intervalRef.current = null
            }
            return
        }

        intervalRef.current = setInterval(() => {
            try {
                setStatus('saving')
                const data = exportDatabase(db)

                if (data.byteLength > MAX_AUTOSAVE_SIZE_BYTES) {
                    setStatus('error')
                    return
                }

                const base64 = toBase64(data)
                localStorage.setItem(AUTOSAVE_KEY, base64)
                localStorage.setItem(AUTOSAVE_TIMESTAMP_KEY, new Date().toISOString())

                setStatus('saved')
                setLastSaved(new Date())
                setHasBackup(true)

                setTimeout(() => setStatus('idle'), 2000)
            } catch (error) {
                if (error instanceof DOMException && error.name === 'QuotaExceededError') {
                    setStatus('storage_full')
                } else {
                    setStatus('error')
                }
            }
        }, AUTOSAVE_INTERVAL_MS)

        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current)
            }
        }
    }, [db])

    function restoreBackup(): Database | null {
        const stored = localStorage.getItem(AUTOSAVE_KEY)
        if (!stored) return null

        try {
            const data = fromBase64(stored)
            return createDatabase(data)
        } catch {
            return null
        }
    }

    function clearBackup() {
        localStorage.removeItem(AUTOSAVE_KEY)
        localStorage.removeItem(AUTOSAVE_TIMESTAMP_KEY)
        setHasBackup(false)
    }

    return {
        status,
        lastSaved,
        hasBackup,
        restoreBackup,
        clearBackup,
    }
}
