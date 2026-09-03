import {useState} from 'react'
import type {Database} from 'sql.js'
import {createTag, updateTag} from '../lib/queries'

interface TagFormProps {
    db: Database
    mode: 'create' | 'rename'
    tagId?: number
    initialName?: string
    onSave: () => void
    onCancel: () => void
}

export function TagForm({db, mode, tagId, initialName = '', onSave, onCancel}: TagFormProps) {
    const [name, setName] = useState(initialName)

    function handleSubmit(e: React.FormEvent) {
        e.preventDefault()

        const trimmedName = name.trim()
        if (!trimmedName) return

        if (mode === 'create') {
            createTag(db, trimmedName)
        } else if (mode === 'rename' && tagId) {
            updateTag(db, tagId, trimmedName)
        }

        onSave()
    }

    return (
        <form className="tag-form" onSubmit={handleSubmit}>
            <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Nombre del tag"
                autoFocus
            />
            <div className="form-actions">
                <button type="submit" disabled={!name.trim()}>
                    {mode === 'create' ? 'Crear' : 'Guardar'}
                </button>
                <button type="button" onClick={onCancel}>
                    Cancelar
                </button>
            </div>
        </form>
    )
}
