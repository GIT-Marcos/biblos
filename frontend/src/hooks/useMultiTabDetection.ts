import {useEffect, useState} from 'react'
import {initMultiTabDetection, isMultiTabSupported} from '../lib/multiTabDetection'

export function useMultiTabDetection(): boolean {
    const [otherTabsActive, setOtherTabsActive] = useState(false)

    useEffect(() => {
        if (!isMultiTabSupported()) {
            return
        }

        const cleanup = initMultiTabDetection((count) => {
            setOtherTabsActive(count > 0)
        })

        return cleanup
    }, [])

    return otherTabsActive
}
