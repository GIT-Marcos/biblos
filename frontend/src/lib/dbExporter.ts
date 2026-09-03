import type {Database} from 'sql.js'

export function exportDatabase(db: Database): Uint8Array {
    return db.export()
}

export function toBase64(data: Uint8Array): string {
    const binStr = Array.from(data, (b) => String.fromCharCode(b)).join('')
    return btoa(binStr)
}

export function fromBase64(base64: string): Uint8Array {
    const binStr = atob(base64)
    return Uint8Array.from(binStr, (c) => c.charCodeAt(0))
}

function generateDownloadName(originalName: string): string {
    const baseName = originalName.replace(/\.(db|sqlite)$/i, '')
    const now = new Date()
    const dd = String(now.getDate()).padStart(2, '0')
    const mm = String(now.getMonth() + 1).padStart(2, '0')
    const yy = String(now.getFullYear()).slice(-2)
    const hh = String(now.getHours()).padStart(2, '0')
    const min = String(now.getMinutes()).padStart(2, '0')
    const ss = String(now.getSeconds()).padStart(2, '0')
    return `${baseName}_${dd}${mm}${yy}-${hh}${min}${ss}.db`
}

export function downloadDatabase(db: Database, originalName: string): void {
    const data = exportDatabase(db)
    const arrayBuffer = data.slice().buffer as ArrayBuffer
    const blob = new Blob([arrayBuffer], {type: 'application/octet-stream'})
    const url = URL.createObjectURL(blob)

    const a = document.createElement('a')
    a.href = url
    a.download = generateDownloadName(originalName)
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)

    URL.revokeObjectURL(url)
}
