import type {SourceQueryParams, Author, Tag} from '../types/database'

interface SourceFiltersProps {
    search: string
    format: SourceQueryParams['format']
    authorId: number | undefined
    tagId: number | undefined
    authors: Author[]
    tags: Tag[]
    onSearchChange: (value: string) => void
    onFormatChange: (value: SourceQueryParams['format']) => void
    onAuthorChange: (value: number | undefined) => void
    onTagChange: (value: number | undefined) => void
}

export function SourceFilters({
                                  search,
                                  format,
                                  authorId,
                                  tagId,
                                  authors,
                                  tags,
                                  onSearchChange,
                                  onFormatChange,
                                  onAuthorChange,
                                  onTagChange,
                              }: SourceFiltersProps) {
    return (
        <div className="source-filters">
            <input
                type="search"
                placeholder="Buscar por nombre o autor..."
                value={search}
                onChange={(e) => onSearchChange(e.target.value)}
                aria-label="Buscar sources"
            />

            <select
                value={format ?? ''}
                onChange={(e) => {
                    const value = e.target.value
                    onFormatChange(
                        value === '' ? undefined : (value as SourceQueryParams['format']),
                    )
                }}
                aria-label="Filtrar por formato"
            >
                <option value="">Todos los formatos</option>
                <option value="PDF">PDF</option>
                <option value="EPUB">EPUB</option>
                <option value="MHTML">MHTML</option>
            </select>

            <select
                value={authorId ?? ''}
                onChange={(e) => {
                    const value = e.target.value
                    onAuthorChange(value === '' ? undefined : Number(value))
                }}
                aria-label="Filtrar por autor"
            >
                <option value="">Todos los autores</option>
                {authors.map((author) => (
                    <option key={author.id} value={author.id}>
                        {author.name}
                    </option>
                ))}
            </select>

            <select
                value={tagId ?? ''}
                onChange={(e) => {
                    const value = e.target.value
                    onTagChange(value === '' ? undefined : Number(value))
                }}
                aria-label="Filtrar por tag"
            >
                <option value="">Todos los tags</option>
                {tags.map((tag) => (
                    <option key={tag.id} value={tag.id}>
                        {tag.name}
                    </option>
                ))}
            </select>
        </div>
    )
}
