import {useContext} from 'react'
import {DatabaseContext, type DatabaseContextValue} from '../context/DatabaseContext'

export function useDatabase(): DatabaseContextValue {
    const context = useContext(DatabaseContext)

    if (!context) {
        throw new Error(
            'useDatabase() debe usarse dentro de un <DatabaseProvider>. ' +
            'Asegúrese de que el componente está en el árbol de componentes del Provider.',
        )
    }

    return context
}
