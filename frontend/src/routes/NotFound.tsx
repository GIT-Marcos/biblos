import {Link} from 'react-router-dom'
import './NotFound.css'

export function NotFound() {
    return (
        <div className="not-found">
            <h2>404</h2>
            <p>Pagina no encontrada</p>
            <Link to="/">Volver al inicio</Link>
        </div>
    )
}
