interface ConfirmDialogProps {
    message: string
    onConfirm: () => void
    onCancel: () => void
}

export function ConfirmDialog({message, onConfirm, onCancel}: ConfirmDialogProps) {
    return (
        <div className="confirm-dialog" role="alertdialog">
            <p>{message}</p>
            <div className="confirm-actions">
                <button type="button" onClick={onConfirm}>
                    Confirmar
                </button>
                <button type="button" onClick={onCancel}>
                    Cancelar
                </button>
            </div>
        </div>
    )
}
