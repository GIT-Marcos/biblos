import type {Source} from '../types/database'

interface SourceMetadataProps {
    source: Source
}

export function SourceMetadata({source}: SourceMetadataProps) {
    const isOrphan = source.deleted_at !== null

    return (
        <div className={`source-metadata ${isOrphan ? 'source-orphan' : ''}`}>
            <h3>Metadata</h3>

            <dl>
                <dt>Nombre</dt>
                <dd>{source.name}</dd>

                <dt>Ruta</dt>
                <dd>{source.path}</dd>

                <dt>Formato</dt>
                <dd>{source.file_format}</dd>

                <dt>Año</dt>
                <dd>{source.year ?? '—'}</dd>

                <dt>Edición</dt>
                <dd>{source.edition ?? '—'}</dd>

                <dt>URL</dt>
                <dd>
                    {source.url ? (
                        <a href={source.url} target="_blank" rel="noopener noreferrer">
                            {source.url}
                        </a>
                    ) : (
                        '—'
                    )}
                </dd>

                <dt>Creado</dt>
                <dd>{new Date(source.created_at).toLocaleDateString()}</dd>

                <dt>Actualizado</dt>
                <dd>{new Date(source.updated_at).toLocaleDateString()}</dd>
            </dl>

            {isOrphan && (
                <p className="source-orphan-notice">
                    Este source fue eliminado del sistema de archivos.
                </p>
            )}
        </div>
    )
}
