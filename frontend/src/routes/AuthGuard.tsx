import {Navigate, Outlet} from 'react-router-dom'
import {useDatabase} from '../hooks/useDatabase'

export function AuthGuard() {
    const {status} = useDatabase()

    if (status !== 'ready') {
        return <Navigate to="/" replace/>
    }

    return <Outlet/>
}
