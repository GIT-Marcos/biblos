# Biblos — Frontend

Interfaz de usuario para navegar y gestionar el catálogo de biblioteca personal digital.

## Stack

| Capa | Tecnología | Versión |
|------|------------|---------|
| Lenguaje | TypeScript | 6.x |
| Framework UI | React | 19.x |
| Build tool | Vite | 8.x |
| SQL en navegador | sql.js | 1.14+ |
| Navegación | react-router-dom | 7.x (hash routing) |
| Linter | Oxlint | 1.x |

## Scripts

```bash
npm run dev          # Dev server con HMR
npm run build        # Type-check + build de producción
npm run lint         # Linting con Oxlint
npm run preview      # Preview del build de producción
npx tsc --noEmit     # Type-check solo
```

## Estructura

```
frontend/
├── public/
│   └── sql-wasm.wasm          # SQLite compilado a WebAssembly
├── src/
│   ├── main.tsx               # Entry point
│   ├── index.css              # CSS global (estructural)
│   ├── router.tsx             # Hash router con todas las rutas
│   ├── types/
│   │   └── database.ts        # Tipos TypeScript
│   ├── context/
│   │   ├── DatabaseContext.tsx # Context definition
│   │   └── DatabaseProvider.tsx# Provider con lógica de carga
│   ├── hooks/
│   │   └── useDatabase.ts     # Hook para consumir context
│   ├── lib/
│   │   ├── sql.ts             # Init sql.js, validación de schema
│   │   ├── dbExporter.ts      # Export y descarga de DB
│   │   ├── dbAutoSave.ts      # Auto-guardado en localStorage
│   │   └── queries/           # Queries SQL parametrizadas
│   │       ├── sources.ts     # CRUD de sources
│   │       ├── authors.ts     # Lectura de autores
│   │       ├── tags.ts        # CRUD de tags
│   │       └── sourceTags.ts  # Asignación de tags
│   ├── routes/                # Componentes de ruta
│   └── components/            # Componentes reutilizables
```

## Arquitectura

### sql.js

SQLite compilado a WebAssembly. Toda la DB se carga en memoria del navegador.
No hay servidor — las operaciones son síncronas y ocurren en el hilo principal.

### Context

`DatabaseProvider` gestiona el estado de la DB:
- `idle` → `loading` → `ready` / `error` / `warning`
- Expone `db`, `status`, `error`, `fileName` y mutaciones

### Queries

Todas las queries usan `db.prepare()` + `stmt.bind()` para SQL parametrizado.
Las escrituras usan transacciones explícitas (`BEGIN/COMMIT/ROLLBACK`).

### Paginación

`LIMIT` / `OFFSET` con `COUNT(*)`. Tamaño de página: 50 elementos.
Se resetea a página 1 al cambiar filtros, orden o búsqueda.

### Auto-guardado

Cada 30 segundos después de la última modificación, la DB se exporta a base64
y se guarda en `localStorage`. Límite: ~5-10MB.

### CSS

Exclusivamente estructural: `display`, `flex`, `grid`, `gap`, `padding`, `margin`,
`width`, `height`, `border`, `overflow`, `position`, `text-align`, `cursor`.
Sin propiedades estéticas (`color`, `background`, `font-*`, `transition`, etc.).
