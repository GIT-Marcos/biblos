const cache = new Map<string, {value: number; expiresAt: number}>()
const MAX_CACHE_SIZE = 50
const DEFAULT_TTL_MS = 30_000

function generateKey(sql: string, params?: unknown[]): string {
    return params ? `${sql}|${JSON.stringify(params)}` : sql
}

export function getCountCache(
    sql: string,
    params?: unknown[],
): number | null {
    const key = generateKey(sql, params)
    const entry = cache.get(key)

    if (!entry) return null
    if (Date.now() > entry.expiresAt) {
        cache.delete(key)
        return null
    }

    return entry.value
}

export function setCountCache(
    sql: string,
    params: unknown[] | undefined,
    value: number,
    ttlMs: number = DEFAULT_TTL_MS,
): void {
    if (cache.size >= MAX_CACHE_SIZE) {
        const firstKey = cache.keys().next().value
        if (firstKey !== undefined) {
            cache.delete(firstKey)
        }
    }

    const key = generateKey(sql, params)
    cache.set(key, {value, expiresAt: Date.now() + ttlMs})
}

export function invalidateCountCache(): void {
    cache.clear()
}
