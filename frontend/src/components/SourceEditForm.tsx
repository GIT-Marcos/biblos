import {useState} from 'react'
import type {Database} from 'sql.js'
import type {Source} from '../types/database'
import {executeStatement} from '../lib/queries/queryUtils'

interface SourceEditFormProps {
    db: Database
    source: Source
    onSave: () => void
}

export function SourceEditForm({db, source, onSave}: SourceEditFormProps) {
    const [isEditing, setIsEditing] = useState(false)
    const [year, setYear] = useState(source.year?.toString() ?? '')
    const [edition, setEdition] = useState(source.edition ?? '')
    const [url, setUrl] = useState(source.url ?? '')

    function handleSubmit(e: React.FormEvent) {
        e.preventDefault()

        executeStatement(
            db,
            `UPDATE sources
       SET year = ?, edition = ?, url = ?, updated_at = datetime('now')
       WHERE id = ?`,
            [
                year ? Number(year) : null,
                edition || null,
                url || null,
                source.id,
            ],
        )

        setIsEditing(false)
        onSave()
    }

    function handleCancel() {
        setYear(source.year?.toString() ?? '')
        setEdition(source.edition ?? '')
        setUrl(source.url ?? '')
        setIsEditing(false)
    }

    if (!isEditing) {
        return (
            <button type="button" onClick={() => setIsEditing(true)}>
                Editar metadata
            </button>
        )
    }

    return (
        <form className="source-edit-form" onSubmit={handleSubmit}>
            <h3>Editar metadata</h3>

            <label>
                Año
                <input
                    type="number"
                    value={year}
                    onChange={(e) => setYear(e.target.value)}
                    placeholder="Ej: 2024"
                />
            </label>

            <label>
                Edición
                <input
                    type="text"
                    value={edition}
                    onChange={(e) => setEdition(e.target.value)}
                    placeholder="Ej: 1ra edición"
                />
            </label>

            <label>
                URL
                <input
                    type="url"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://..."
                />
            </label>

            <div className="form-actions">
                <button type="submit">Guardar</button>
                <button type="button" onClick={handleCancel}>
                    Cancelar
                </button>
            </div>
        </form>
    )
}
