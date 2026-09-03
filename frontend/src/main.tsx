import {StrictMode} from 'react'
import {createRoot} from 'react-dom/client'
import {RouterProvider} from 'react-router-dom'
import {DatabaseProvider} from './context/DatabaseProvider'
import {router} from './router'
import './index.css'

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <DatabaseProvider>
            <RouterProvider router={router}/>
        </DatabaseProvider>
    </StrictMode>,
)
