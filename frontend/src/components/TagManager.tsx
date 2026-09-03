import {useState} from 'react'
import type {Database} from 'sql.js'
import {assignTagToSource, getSourceTags, getTags, removeTagFromSource} from '../lib/queries'

interface TagManagerProps {
    db: Database
    sourceId: number
    onTagsChange: () => void
}

export function TagManager({db, sourceId, onTagsChange}: TagManagerProps) {
    const [showAddForm, setShowAddForm] = useState(false)

    const assignedTags = getSourceTags(db, sourceId)
    const allTags = getTags(db, {page: 1, pageSize: 100, sort: 'name', order: 'asc'})

    const availableTags = allTags.data.filter(
        (tag) => !assignedTags.some((assigned) => assigned.id === tag.id),
    )

    function handleAddTag(tagId: number) {
        assignTagToSource(db, sourceId, tagId)
        setShowAddForm(false)
        onTagsChange()
    }

    function handleRemoveTag(tagId: number) {
        removeTagFromSource(db, sourceId, tagId)
        onTagsChange()
    }

    return (
        <div className="tag-manager">
            <h3>Tags</h3>

            <ul className="tag-list">
                {assignedTags.map((tag) => (
                    <li key={tag.id}>
                        <span>{tag.name}</span>
                        <button
                            type="button"
                            onClick={() => handleRemoveTag(tag.id)}
                            aria-label={`Quitar tag ${tag.name}`}
                        >
                            ×
                        </button>
                    </li>
                ))}
            </ul>

            {availableTags.length > 0 && (
                <>
                    <button
                        type="button"
                        onClick={() => setShowAddForm(!showAddForm)}
                    >
                        {showAddForm ? 'Cancelar' : 'Agregar tag'}
                    </button>

                    {showAddForm && (
                        <ul className="tag-add-list">
                            {availableTags.map((tag) => (
                                <li key={tag.id}>
                                    <button
                                        type="button"
                                        onClick={() => handleAddTag(tag.id)}
                                    >
                                        {tag.name}
                                    </button>
                                </li>
                            ))}
                        </ul>
                    )}
                </>
            )}
        </div>
    )
}
