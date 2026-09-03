import {Link, useParams, useSearchParams} from 'react-router-dom'
import {useDatabase} from '../hooks/useDatabase'
import {getAuthorById, getSources} from '../lib/queries'
import type {SourceQueryParams} from '../types/database'
import {SourceTable} from '../components/SourceTable'
import {Pagination} from '../components/Pagination'
import '../components/SourceTable.css'
import '../components/Pagination.css'
import './AuthorDetail.css'

export function AuthorDetail() {
    const {id} = useParams<{ id: string }>()
    const {db} = useDatabase()
    const [searchParams, setSearchParams] = useSearchParams()

    if (!db) return null

    const authorId = Number(id)
    if (isNaN(authorId)) {
        return <p>ID de autor inválido.</p>
    }

    const author = getAuthorById(db, authorId)

    if (!author) {
        return <p>Autor no encontrado.</p>
    }

    const page = Number(searchParams.get('page') ?? 1)
    const sort = (searchParams.get('sort') ?? 'name') as SourceQueryParams['sort']
    const order = (searchParams.get('order') ?? 'asc') as SourceQueryParams['order']

    const sourcesResult = getSources(db, {
        page,
        pageSize: 50,
        sort,
        order,
        authorId,
    })

    function updateParams(updates: Record<string, string | undefined>) {
        setSearchParams((prev) => {
            const next = new URLSearchParams(prev)
            for (const [key, value] of Object.entries(updates)) {
                if (value === undefined || value === '') {
                    next.delete(key)
                } else {
                    next.set(key, value)
                }
            }
            if (!('page' in updates)) {
                next.set('page', '1')
            }
            return next
        })
    }

    function handleSortChange(field: string) {
        const newOrder = sort === field && order === 'asc' ? 'desc' : 'asc'
        updateParams({sort: field, order: newOrder})
    }

    function handlePageChange(newPage: number) {
        updateParams({page: String(newPage)})
    }

    return (
        <div className="author-detail">
            <Link to="/authors">← Volver a Autores</Link>

            <h2>{author.name}</h2>
            <p>{author.count} sources</p>

            <h3>Sources de este autor</h3>

            <SourceTable
                sources={sourcesResult.data}
                sort={sort}
                order={order}
                onSortChange={handleSortChange}
            />

            <Pagination
                page={sourcesResult.page}
                totalPages={sourcesResult.totalPages}
                onPageChange={handlePageChange}
            />
        </div>
    )
}
