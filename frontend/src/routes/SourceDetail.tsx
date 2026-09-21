import {Link, useParams, useNavigate} from 'react-router-dom'
import {useState} from 'react'
import {useDatabase} from '../hooks/useDatabase'
import {getSourceById, deleteSource} from '../lib/queries'
import {SourceMetadata} from '../components/SourceMetadata'
import {TagManager} from '../components/TagManager'
import {SourceEditForm} from '../components/SourceEditForm'
import {ConfirmDialog} from '../components/ConfirmDialog'
import '../components/SourceMetadata.css'
import '../components/TagManager.css'
import '../components/SourceEditForm.css'
import '../components/ConfirmDialog.css'
import './SourceDetail.css'

export function SourceDetail() {
    const {id} = useParams<{ id: string }>()
    const {db} = useDatabase()
    const navigate = useNavigate()
    const [, setRefreshKey] = useState(0)
    const [showConfirm, setShowConfirm] = useState(false)

    if (!db) return null

    const sourceId = Number(id)
    if (isNaN(sourceId)) {
        return <p>ID de source inválido.</p>
    }

    const source = getSourceById(db, sourceId)

    if (!source) {
        return <p>Source no encontrado.</p>
    }

    function handleRefresh() {
        setRefreshKey((k) => k + 1)
    }

    function handleDeleteClick() {
        setShowConfirm(true)
    }

    function handleConfirmDelete() {
        if (!db) return
        deleteSource(db, sourceId)
        navigate('/sources')
    }

    return (
        <div className="source-detail">
            <Link to="/sources">← Volver a Sources</Link>

            <h2>{source.name}</h2>

            <SourceMetadata source={source}/>

            {!source.deleted_at && (
                <>
                    <SourceEditForm db={db} source={source} onSave={handleRefresh}/>
                    <TagManager db={db} sourceId={sourceId} onTagsChange={handleRefresh}/>
                </>
            )}

            {source.deleted_at && (
                <div className="orphan-actions">
                    <button type="button" onClick={handleDeleteClick}>
                        Eliminar permanentemente
                    </button>
                    {showConfirm && (
                        <ConfirmDialog
                            message="¿Eliminar este source permanentemente? Esta acción no se puede deshacer."
                            onConfirm={handleConfirmDelete}
                            onCancel={() => setShowConfirm(false)}
                        />
                    )}
                </div>
            )}
        </div>
    )
}
