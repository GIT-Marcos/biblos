export function LoadingSpinner() {
    return (
        <div className="loading-spinner" role="status" aria-label="Cargando base de datos">
            <svg viewBox="0 0 24 24" width="48" height="48">
                <circle
                    cx="12"
                    cy="12"
                    r="10"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeDasharray="31.4 31.4"
                    strokeLinecap="round"
                />
            </svg>
            <span>Cargando base de datos...</span>
        </div>
    )
}
