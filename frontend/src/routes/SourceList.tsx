import {useSearchParams} from 'react-router-dom'
import {useDatabase} from '../hooks/useDatabase'
import {getAuthors, getSources, getTags} from '../lib/queries'
import type {SourceQueryParams} from '../types/database'
import {SourceFilters} from '../components/SourceFilters'
import {SourceTable} from '../components/SourceTable'
import {Pagination} from '../components/Pagination'
import '../components/SourceFilters.css'
import '../components/SourceTable.css'
import '../components/Pagination.css'

export function SourceList() {
    const {db} = useDatabase()
    const [searchParams, setSearchParams] = useSearchParams()

    if (!db) return null

    const page = Number(searchParams.get('page') ?? 1)
    const sort = (searchParams.get('sort') ?? 'name') as SourceQueryParams['sort']
    const order = (searchParams.get('order') ?? 'asc') as SourceQueryParams['order']
    const search = searchParams.get('search') ?? ''
    const format = searchParams.get('format') as SourceQueryParams['format'] | null
    const authorId = searchParams.get('author') ? Number(searchParams.get('author')) : undefined
    const tagId = searchParams.get('tag') ? Number(searchParams.get('tag')) : undefined

    const result = getSources(db, {
        page,
        pageSize: 50,
        sort,
        order,
        search: search || undefined,
        format: format || undefined,
        authorId,
        tagId,
    })

    const authors = getAuthors(db, {page: 1, pageSize: 1000, sort: 'name', order: 'asc'})
    const tags = getTags(db, {page: 1, pageSize: 1000, sort: 'name', order: 'asc'})

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

    function handleFormatChange(value: SourceQueryParams['format']) {
        updateParams({format: value})
    }

    function handleAuthorChange(value: number | undefined) {
        updateParams({author: value ? String(value) : undefined})
    }

    function handleTagChange(value: number | undefined) {
        updateParams({tag: value ? String(value) : undefined})
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
            <h2>Sources</h2>

            <SourceFilters
                search={search}
                format={format ?? undefined}
                authorId={authorId}
                tagId={tagId}
                authors={authors.data}
                tags={tags.data}
                onSearchChange={handleSearchChange}
                onFormatChange={handleFormatChange}
                onAuthorChange={handleAuthorChange}
                onTagChange={handleTagChange}
            />

            <SourceTable
                sources={result.data}
                sort={sort}
                order={order}
                onSortChange={handleSortChange}
            />

            <Pagination
                page={result.page}
                totalPages={result.totalPages}
                onPageChange={handlePageChange}
            />

            <p>
                {result.total} sources encontrados
            </p>
        </div>
    )
}
