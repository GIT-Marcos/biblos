import type {Tag} from '../types/database'

interface TagTableProps {
    tags: (Tag & { count: number })[]
    sort: string
    order: string
    onSortChange: (field: string) => void
    onRename: (tag: Tag) => void
    onDelete: (tag: Tag) => void
}

export function TagTable({tags, sort, order, onSortChange, onRename, onDelete}: TagTableProps) {
    if (tags.length === 0) {
        return <p>No se encontraron tags.</p>
    }

    function getSortIndicator(field: string) {
        if (sort !== field) return ''
        return order === 'asc' ? ' ↑' : ' ↓'
    }

    return (
        <table className="tag-table">
            <thead>
            <tr>
                <th>
                    <button type="button" onClick={() => onSortChange('name')}>
                        Nombre{getSortIndicator('name')}
                    </button>
                </th>
                <th>
                    <button type="button" onClick={() => onSortChange('count')}>
                        Sources{getSortIndicator('count')}
                    </button>
                </th>
                <th>Acciones</th>
            </tr>
            </thead>
            <tbody>
            {tags.map((tag) => (
                <tr key={tag.id}>
                    <td>{tag.name}</td>
                    <td>{tag.count}</td>
                    <td>
                        <button type="button" onClick={() => onRename(tag)}>
                            Renombrar
                        </button>
                        <button type="button" onClick={() => onDelete(tag)}>
                            Eliminar
                        </button>
                    </td>
                </tr>
            ))}
            </tbody>
        </table>
    )
}
