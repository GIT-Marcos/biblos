import {useDatabase} from '../hooks/useDatabase'
import {downloadDatabase} from '../lib/dbExporter'

export function DownloadButton() {
    const {db, fileName} = useDatabase()

    function handleDownload() {
        if (db && fileName) {
            downloadDatabase(db, fileName)
        }
    }

    return (
        <button
            type="button"
            onClick={handleDownload}
            disabled={!db}
            aria-label="Descargar base de datos"
        >
            Descargar DB
        </button>
    )
}
