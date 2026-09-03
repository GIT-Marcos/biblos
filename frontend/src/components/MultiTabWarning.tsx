import {isMultiTabSupported} from '../lib/multiTabDetection'

interface MultiTabWarningProps {
    visible: boolean
}

export function MultiTabWarning({visible}: MultiTabWarningProps) {
    if (!visible || !isMultiTabSupported()) {
        return null
    }

    return (
        <div className="multi-tab-warning" role="alert">
            <span>Otra pestaña tiene esta DB abierta. Los cambios pueden entrarse en conflicto.</span>
        </div>
    )
}
