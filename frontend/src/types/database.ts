export interface Source {
    id: number
    name: string
    path: string
    path_lower: string
    content_hash: string
    file_format: 'PDF' | 'EPUB' | 'MHTML'
    author_id: number
    author_name?: string
    year: number | null
    edition: string | null
    url: string | null
    created_at: string
    updated_at: string
    deleted_at: string | null
}

export interface Author {
    id: number
    name: string
}

export interface Tag {
    id: number
    name: string
}

export interface SourceTag {
    source_id: number
    tag_id: number
}

export interface SchemaValidation {
    valid: boolean
    missingTables: string[]
    missingColumns: Record<string, string[]>
}

export interface PaginationParams {
    page: number
    pageSize: number
}

export type SortOrder = 'asc' | 'desc'

export interface SourceQueryParams extends PaginationParams {
    sort: 'name' | 'author' | 'year' | 'format'
    order: SortOrder
    search?: string
    format?: 'PDF' | 'EPUB' | 'MHTML'
    authorId?: number
    tagId?: number
}

export interface AuthorQueryParams extends PaginationParams {
    sort: 'name' | 'count'
    order: SortOrder
    search?: string
}

export interface TagQueryParams extends PaginationParams {
    sort: 'name' | 'count'
    order: SortOrder
    search?: string
}

export interface PaginatedResult<T> {
    data: T[]
    total: number
    page: number
    pageSize: number
    totalPages: number
}
