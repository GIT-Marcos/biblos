import {NavLink, Outlet} from 'react-router-dom'
import {useDatabase} from '../hooks/useDatabase'
import {DownloadButton} from '../components/DownloadButton'
import {AutoSaveIndicator} from '../components/AutoSaveIndicator'
import {MultiTabWarning} from '../components/MultiTabWarning'
import {useAutoSave} from '../lib/dbAutoSave'
import './RootLayout.css'
import '../components/AutoSaveIndicator.css'
import '../components/MultiTabWarning.css'

export function RootLayout() {
    const {db, fileName, closeDatabase, otherTabsActive} = useDatabase()
    const {status: autoSaveStatus, lastSaved} = useAutoSave(db)

    return (
        <div className="root-layout">
            <header className="root-header">
                <h1>Biblos</h1>
                <nav aria-label="Navegación principal">
                    <NavLink to="/sources">Sources</NavLink>
                    <NavLink to="/authors">Autores</NavLink>
                    <NavLink to="/tags">Tags</NavLink>
                </nav>
                <div className="root-info">
                    <span>{fileName}</span>
                    <AutoSaveIndicator status={autoSaveStatus} lastSaved={lastSaved}/>
                    <DownloadButton/>
                    <button type="button" onClick={closeDatabase}>
                        Cerrar DB
                    </button>
                </div>
            </header>
            <MultiTabWarning visible={otherTabsActive}/>
            <main className="root-main">
                <Outlet/>
            </main>
        </div>
    )
}
