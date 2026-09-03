import type {AutoSaveStatus} from '../lib/dbAutoSave'

interface AutoSaveIndicatorProps {
    status: AutoSaveStatus
    lastSaved: Date | null
}

export function AutoSaveIndicator({status, lastSaved}: AutoSaveIndicatorProps) {
    function getStatusText(): string {
        switch (status) {
            case 'saving':
                return 'Guardando...'
            case 'saved':
                return 'Guardado'
            case 'storage_full':
                return 'No se puede guardar automáticamente. localStorage lleno. Descargue manualmente.'
            case 'error':
                return 'Error al guardar'
            default:
                return lastSaved
                    ? `Último guardado: ${lastSaved.toLocaleTimeString()}`
                    : 'Sin guardar'
        }
    }

    return (
        <div className="auto-save-indicator" aria-live="polite">
            <span>{getStatusText()}</span>
        </div>
    )
}
