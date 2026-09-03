import {Link} from 'react-router-dom'
import type {Author} from '../types/database'

interface AuthorTableProps {
    authors: (Author & { count: number })[]
    sort: string
    order: string
    onSortChange: (field: string) => void
}

export function AuthorTable({authors, sort, order, onSortChange}: AuthorTableProps) {
    if (authors.length === 0) {
        return <p>No se encontraron autores.</p>
    }

    function getSortIndicator(field: string) {
        if (sort !== field) return ''
        return order === 'asc' ? ' ↑' : ' ↓'
    }

    return (
        <table className="author-table">
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
            </tr>
            </thead>
            <tbody>
            {authors.map((author) => (
                <tr key={author.id}>
                    <td>
                        <Link to={`/authors/${author.id}`}>{author.name}</Link>
                    </td>
                    <td>{author.count}</td>
                </tr>
            ))}
            </tbody>
        </table>
    )
}
