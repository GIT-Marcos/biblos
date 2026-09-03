interface PaginationProps {
    page: number
    totalPages: number
    onPageChange: (page: number) => void
}

export function Pagination({page, totalPages, onPageChange}: PaginationProps) {
    if (totalPages <= 1) return null

    const pages = generatePageNumbers(page, totalPages)

    return (
        <nav className="pagination" aria-label="Paginación">
            <button
                type="button"
                onClick={() => onPageChange(page - 1)}
                disabled={page <= 1}
            >
                Anterior
            </button>

            {pages.map((p, i) =>
                    p === '...' ? (
                        <span key={`dots-${i}`} className="pagination-dots">
            ...
          </span>
                    ) : (
                        <button
                            key={p}
                            type="button"
                            onClick={() => onPageChange(p as number)}
                            aria-current={p === page ? 'page' : undefined}
                        >
                            {p}
                        </button>
                    ),
            )}

            <button
                type="button"
                onClick={() => onPageChange(page + 1)}
                disabled={page >= totalPages}
            >
                Siguiente
            </button>
        </nav>
    )
}

function generatePageNumbers(current: number, total: number): (number | string)[] {
    if (total <= 7) {
        return Array.from({length: total}, (_, i) => i + 1)
    }

    const pages: (number | string)[] = [1]

    if (current > 3) {
        pages.push('...')
    }

    const start = Math.max(2, current - 1)
    const end = Math.min(total - 1, current + 1)

    for (let i = start; i <= end; i++) {
        pages.push(i)
    }

    if (current < total - 2) {
        pages.push('...')
    }

    if (total > 1) {
        pages.push(total)
    }

    return pages
}
