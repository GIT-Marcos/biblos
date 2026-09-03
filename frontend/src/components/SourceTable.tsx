import {Link} from 'react-router-dom'
import type {Source} from '../types/database'

interface SourceTableProps {
    sources: Source[]
    sort: string
    order: string
    onSortChange: (field: string) => void
}

export function SourceTable({sources, sort, order, onSortChange}: SourceTableProps) {
    if (sources.length === 0) {
        return <p>No se encontraron sources.</p>
    }

    function getSortIndicator(field: string) {
        if (sort !== field) return ''
        return order === 'asc' ? ' ↑' : ' ↓'
    }

    return (
        <table className="source-table">
            <thead>
            <tr>
                <th>
                    <button type="button" onClick={() => onSortChange('name')}>
                        Nombre{getSortIndicator('name')}
                    </button>
                </th>
                <th>
                    <button type="button" onClick={() => onSortChange('author')}>
                        Autor{getSortIndicator('author')}
                    </button>
                </th>
                <th>
                    <button type="button" onClick={() => onSortChange('format')}>
                        Formato{getSortIndicator('format')}
                    </button>
                </th>
                <th>
                    <button type="button" onClick={() => onSortChange('year')}>
                        Año{getSortIndicator('year')}
                    </button>
                </th>
            </tr>
            </thead>
            <tbody>
            {sources.map((source) => (
                <tr
                    key={source.id}
                    className={source.deleted_at ? 'source-orphan' : undefined}
                >
                    <td>
                        <Link to={`/sources/${source.id}`}>{source.name}</Link>
                    </td>
                    <td>{source.author_name ?? '—'}</td>
                    <td>{source.file_format}</td>
                    <td>{source.year ?? '—'}</td>
                </tr>
            ))}
            </tbody>
        </table>
    )
}
