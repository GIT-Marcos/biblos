import {useSearchParams} from 'react-router-dom'
import {useState} from 'react'
import {useDatabase} from '../hooks/useDatabase'
import {deleteTag, getTags} from '../lib/queries'
import type {Tag, TagQueryParams} from '../types/database'
import {TagTable} from '../components/TagTable'
import {TagForm} from '../components/TagForm'
import {ConfirmDialog} from '../components/ConfirmDialog'
import {Pagination} from '../components/Pagination'
import {invalidateCountCache} from '../lib/queryCache'
import '../components/TagTable.css'
import '../components/TagForm.css'
import '../components/ConfirmDialog.css'
import '../components/Pagination.css'

export function TagList() {
    const {db} = useDatabase()
    const [searchParams, setSearchParams] = useSearchParams()
    const [, setRefreshKey] = useState(0)
    const [showCreateForm, setShowCreateForm] = useState(false)
    const [tagToRename, setTagToRename] = useState<Tag | null>(null)
    const [tagToDelete, setTagToDelete] = useState<Tag | null>(null)

    if (!db) return null

    const page = Number(searchParams.get('page') ?? 1)
    const sort = (searchParams.get('sort') ?? 'name') as TagQueryParams['sort']
    const order = (searchParams.get('order') ?? 'asc') as TagQueryParams['order']
    const search = searchParams.get('search') ?? ''

    const result = getTags(db, {
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

    function handleRefresh() {
        setRefreshKey((k) => k + 1)
        setShowCreateForm(false)
        setTagToRename(null)
        setTagToDelete(null)
    }

    function handleDeleteConfirm() {
        if (tagToDelete && db) {
            deleteTag(db, tagToDelete.id)
            invalidateCountCache()
            handleRefresh()
        }
    }

    return (
        <div>
            <h2>Tags</h2>

            <input
                type="search"
                placeholder="Buscar tags..."
                value={search}
                onChange={(e) => handleSearchChange(e.target.value)}
                aria-label="Buscar tags"
            />

            <button type="button" onClick={() => setShowCreateForm(true)}>
                Crear tag
            </button>

            {showCreateForm && (
                <TagForm
                    db={db}
                    mode="create"
                    onSave={handleRefresh}
                    onCancel={() => setShowCreateForm(false)}
                />
            )}

            {tagToRename && (
                <TagForm
                    db={db}
                    mode="rename"
                    tagId={tagToRename.id}
                    initialName={tagToRename.name}
                    onSave={handleRefresh}
                    onCancel={() => setTagToRename(null)}
                />
            )}

            {tagToDelete && (
                <ConfirmDialog
                    message={`¿Eliminar el tag "${tagToDelete.name}"? Se desasociará de todos los sources.`}
                    onConfirm={handleDeleteConfirm}
                    onCancel={() => setTagToDelete(null)}
                />
            )}

            <TagTable
                tags={result.data}
                sort={sort}
                order={order}
                onSortChange={handleSortChange}
                onRename={setTagToRename}
                onDelete={setTagToDelete}
            />

            <Pagination
                page={result.page}
                totalPages={result.totalPages}
                onPageChange={handlePageChange}
            />

            <p>{result.total} tags encontrados</p>
        </div>
    )
}
