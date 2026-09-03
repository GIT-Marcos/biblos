import {useSearchParams} from 'react-router-dom'
import {useDatabase} from '../hooks/useDatabase'
import {getAuthors} from '../lib/queries'
import type {AuthorQueryParams} from '../types/database'
import {AuthorTable} from '../components/AuthorTable'
import {Pagination} from '../components/Pagination'
import '../components/AuthorTable.css'
import '../components/Pagination.css'

export function AuthorList() {
    const {db} = useDatabase()
    const [searchParams, setSearchParams] = useSearchParams()

    if (!db) return null

    const page = Number(searchParams.get('page') ?? 1)
    const sort = (searchParams.get('sort') ?? 'name') as AuthorQueryParams['sort']
    const order = (searchParams.get('order') ?? 'asc') as AuthorQueryParams['order']
    const search = searchParams.get('search') ?? ''

    const result = getAuthors(db, {
        page,
        pageSize: 50,
        sort,
        order,
        search: search || undefined,
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

    function handleSearchChange(value: string) {
        updateParams({search: value})
    }

    function handleSortChange(field: string) {
        const newOrder = sort === field && order === 'asc' ? 'desc' : 'asc'
        updateParams({sort: field, order: newOrder})
    }

    function handlePageChange(newPage: number) {
        updateParams({page: String(newPage)})
    }

    return (
        <div>
            <h2>Autores</h2>

            <input
                type="search"
                placeholder="Buscar autores..."
                value={search}
                onChange={(e) => handleSearchChange(e.target.value)}
                aria-label="Buscar autores"
            />

            <AuthorTable
                authors={result.data}
                sort={sort}
                order={order}
                onSortChange={handleSortChange}
            />

            <Pagination
                page={result.page}
                totalPages={result.totalPages}
                onPageChange={handlePageChange}
            />

            <p>{result.total} autores encontrados</p>
        </div>
    )
}
