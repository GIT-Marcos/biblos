import {createHashRouter, Navigate} from 'react-router-dom'
import {HomeRoute} from './routes/HomeRoute'
import {AuthGuard} from './routes/AuthGuard'
import {RootLayout} from './routes/RootLayout'
import {SourceList} from './routes/SourceList'
import {SourceDetail} from './routes/SourceDetail'
import {AuthorList} from './routes/AuthorList'
import {AuthorDetail} from './routes/AuthorDetail'
import {TagList} from './routes/TagList'

export const router = createHashRouter([
    {
        path: '/',
        element: <HomeRoute/>,
    },
    {
        element: <AuthGuard/>,
        children: [
            {
                element: <RootLayout/>,
                children: [
                    {path: 'sources', element: <SourceList/>},
                    {path: 'sources/:id', element: <SourceDetail/>},
                    {path: 'authors', element: <AuthorList/>},
                    {path: 'authors/:id', element: <AuthorDetail/>},
                    {path: 'tags', element: <TagList/>},
                    {index: true, element: <Navigate to="/sources" replace/>},
                ],
            },
        ],
    },
])
