import {useState} from 'react'
import {Navigate} from 'react-router-dom'
import {useDatabase} from '../hooks/useDatabase'
import {FileUpload} from '../components/FileUpload'
import {useAutoSave} from '../lib/dbAutoSave'

const AUTOSAVE_KEY = 'biblos_autosave'
const AUTOSAVE_TIMESTAMP_KEY = 'biblos_autosave_timestamp'

export function HomeRoute() {
    const {status} = useDatabase()
    const {restoreBackup, clearBackup, hasBackup} = useAutoSave(null)
    const [showRestorePrompt, setShowRestorePrompt] = useState(() => {
        return localStorage.getItem(AUTOSAVE_KEY) !== null
    })
    const [lastSaved] = useState(() => {
        const timestamp = localStorage.getItem(AUTOSAVE_TIMESTAMP_KEY)
        return timestamp ? new Date(timestamp) : null
    })

    if (status === 'ready') {
        return <Navigate to="/sources" replace/>
    }

    function handleRestore() {
        const db = restoreBackup()
        if (db) {
            window.location.reload()
        } else {
            clearBackup()
            setShowRestorePrompt(false)
        }
    }

    function handleDiscard() {
        clearBackup()
        setShowRestorePrompt(false)
    }

    if (showRestorePrompt && hasBackup) {
        return (
            <section className="file-upload">
                <h1>Biblos</h1>
                <p>Se encontró un backup anterior{lastSaved ? ` (${lastSaved.toLocaleString()})` : ''}.</p>
                <p>¿Desea restaurarlo o cargar un archivo nuevo?</p>
                <div>
                    <button type="button" onClick={handleRestore}>
                        Restaurar backup
                    </button>
                    <button type="button" onClick={handleDiscard}>
                        Descartar y cargar nuevo
                    </button>
                </div>
            </section>
        )
    }

    return <FileUpload/>
}
