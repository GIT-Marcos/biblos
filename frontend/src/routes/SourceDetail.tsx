import {useParams, Link} from 'react-router-dom'
import {useState} from 'react'
import {useDatabase} from '../hooks/useDatabase'
import {getSourceById} from '../lib/queries'
import {SourceMetadata} from '../components/SourceMetadata'
import {TagManager} from '../components/TagManager'
import {SourceEditForm} from '../components/SourceEditForm'
import '../components/SourceMetadata.css'
import '../components/TagManager.css'
import '../components/SourceEditForm.css'
import './SourceDetail.css'

export function SourceDetail() {
    const {id} = useParams<{ id: string }>()
    const {db} = useDatabase()
    const [, setRefreshKey] = useState(0)

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
        </div>
    )
}
