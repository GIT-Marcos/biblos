import {createHashRouter} from 'react-router-dom'
import {HomeRoute} from './routes/HomeRoute'
import {RootLayout} from './routes/RootLayout'
import {SourceList} from './routes/SourceList'
import {SourceDetail} from './routes/SourceDetail'
import {AuthorList} from './routes/AuthorList'
import {AuthorDetail} from './routes/AuthorDetail'
import {TagList} from './routes/TagList'
import {NotFound} from './routes/NotFound'

export const router = createHashRouter([
    {
        path: '/',
        element: <HomeRoute/>,
    },
    {
        element: <RootLayout/>,
        children: [
            {path: 'sources', element: <SourceList/>},
            {path: 'sources/:id', element: <SourceDetail/>},
            {path: 'authors', element: <AuthorList/>},
            {path: 'authors/:id', element: <AuthorDetail/>},
            {path: 'tags', element: <TagList/>},
            {path: '*', element: <NotFound/>},
        ],
    },
])
