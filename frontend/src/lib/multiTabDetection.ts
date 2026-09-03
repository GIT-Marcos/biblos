const CHANNEL_NAME = 'biblos_multi_tab'
const TAB_OPEN_TIMEOUT_MS = 1000

export type TabMessage =
    | {type: 'tab-open'; tabId: string}
    | {type: 'tab-closed'; tabId: string}
    | {type: 'heartbeat'; tabId: string}

let channel: BroadcastChannel | null = null
let tabId: string | null = null
let activeTabs: Set<string> = new Set()
let onTabsChange: ((count: number) => void) | null = null

function generateTabId(): string {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID()
    }
    return `tab_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

function handleMessage(event: MessageEvent<TabMessage>) {
    if (!tabId) return

    const data = event.data

    if (data.type === 'tab-open' && data.tabId !== tabId) {
        activeTabs.add(data.tabId)
        onTabsChange?.(activeTabs.size)
    } else if (data.type === 'tab-closed' && data.tabId !== tabId) {
        activeTabs.delete(data.tabId)
        onTabsChange?.(activeTabs.size)
    } else if (data.type === 'heartbeat' && data.tabId !== tabId) {
        activeTabs.add(data.tabId)
        onTabsChange?.(activeTabs.size)
        channel?.postMessage({type: 'tab-open', tabId})
    }
}

export function initMultiTabDetection(callback: (count: number) => void): () => void {
    if (!('BroadcastChannel' in globalThis)) {
        callback(0)
        return () => {}
    }

    tabId = generateTabId()
    onTabsChange = callback
    activeTabs.clear()

    channel = new BroadcastChannel(CHANNEL_NAME)
    channel.onmessage = handleMessage

    channel.postMessage({type: 'tab-open', tabId})

    channel.postMessage({type: 'heartbeat', tabId})

    const interval = setInterval(() => {
        channel?.postMessage({type: 'heartbeat', tabId})
    }, TAB_OPEN_TIMEOUT_MS)

    function cleanup() {
        clearInterval(interval)
        if (channel && tabId) {
            channel.postMessage({type: 'tab-closed', tabId})
        }
        channel?.close()
        channel = null
        tabId = null
        activeTabs.clear()
        onTabsChange = null
    }

    window.addEventListener('beforeunload', cleanup)

    return () => {
        window.removeEventListener('beforeunload', cleanup)
        cleanup()
    }
}

export function isMultiTabSupported(): boolean {
    return 'BroadcastChannel' in globalThis
}
