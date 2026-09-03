import {useRef} from 'react'
import {useDatabase} from '../hooks/useDatabase'
import './FileUpload.css'

export function FileUpload() {
    const {
        loadDatabase,
        status,
        error,
        clearError,
        confirmLoad,
        cancelLoad,
        pendingFileName,
    } = useDatabase()
    const inputRef = useRef<HTMLInputElement>(null)

    const isLoading = status === 'loading'
    const isWarning = status === 'warning'

    async function handleChange(event: React.ChangeEvent<HTMLInputElement>) {
        const file = event.target.files?.[0]
        if (!file) return

        clearError()
        await loadDatabase(file)

        if (inputRef.current) {
            inputRef.current.value = ''
        }
    }

    function handleClick() {
        inputRef.current?.click()
    }

    function handleKeyDown(event: React.KeyboardEvent) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault()
            handleClick()
        }
    }

    if (isWarning && pendingFileName) {
        return (
            <section className="file-upload">
                <h1>Biblos</h1>
                <div role="alert" className="file-upload-error">
                    <p>{error}</p>
                    <p>¿Desea continuar con la carga de &quot;{pendingFileName}&quot;?</p>
                    <div>
                        <button type="button" onClick={confirmLoad}>
                            Continuar
                        </button>
                        <button type="button" onClick={cancelLoad}>
                            Cancelar
                        </button>
                    </div>
                </div>
            </section>
        )
    }

    return (
        <section className="file-upload">
            <h1>Biblos</h1>
            <p>Cargue su base de datos para comenzar</p>

            <input
                ref={inputRef}
                type="file"
                accept=".db,.sqlite"
                onChange={handleChange}
                disabled={isLoading}
                style={{display: 'none'}}
                aria-label="Seleccionar archivo de base de datos"
            />

            <button
                type="button"
                onClick={handleClick}
                onKeyDown={handleKeyDown}
                disabled={isLoading}
                aria-busy={isLoading}
            >
                {isLoading ? 'Cargando...' : 'Seleccionar archivo .db'}
            </button>

            {error && (
                <div role="alert" className="file-upload-error">
                    <p>{error}</p>
                    <button type="button" onClick={clearError}>
                        Cerrar
                    </button>
                </div>
            )}
        </section>
    )
}
