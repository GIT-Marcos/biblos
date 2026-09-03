interface ProgressBarProps {
    value: number
    max?: number
}

export function ProgressBar({value, max = 100}: ProgressBarProps) {
    const percentage = Math.min(Math.max(0, value), max)
    const clampedPercentage = max > 0 ? (percentage / max) * 100 : 0

    return (
        <div
            className="progress-bar"
            role="progressbar"
            aria-valuenow={percentage}
            aria-valuemin={0}
            aria-valuemax={max}
            aria-label="Progreso de carga"
        >
            <div className="progress-bar-track">
                <div
                    className="progress-bar-fill"
                    style={{width: `${clampedPercentage}%`}}
                />
            </div>
            <span className="progress-bar-text">
                {Math.round(clampedPercentage)}%
            </span>
        </div>
    )
}
