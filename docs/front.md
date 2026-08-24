# front

## 0. Filosofía de estilos

**CSS exclusivamente estructural.** El frontend no define estética: no hay colores, fuentes, gradientes,
sombras, animaciones ni transiciones. Todo el CSS se limita a propiedades de layout y espaciado para que los
componentes sean funcionales y navegables.

**Propiedades permitidas:** `display`, `flex`, `grid`, `gap`, `padding`, `margin`, `width`, `height`,
`overflow`, `border` (solo como separador funcional, Ej. tablas e inputs), `list-style`, `text-align`,
`cursor`, `white-space`, `position`, `top`, `left`, `right`, `bottom`, `z-index`.

**Propiedades prohibidas:** `color`, `font-family`, `font-size`, `font-weight`, `background`,
`background-color`, `border-radius`, `box-shadow`, `text-shadow`, `transition`, `animation`,
`transform`, `opacity`, `filter`, `gradient`, `text-decoration` (salvo `underline` funcional en links).

**Excepción:** `font-weight: bold` se permite exclusivamente para marcar el link activo de navegación.

El sitio debe ser usable con estilos mínimos. Un usuario puede envolver todo en un framework CSS o escribir
CSS custom sin reestructurar componentes ni clases.

---

## 1. Stack detallado de tecnologías y dependencias

### 1.1. Lenguaje y framework

| Capa      | Tecnología              | Versión | Notas                                                   |
|-----------|-------------------------|---------|---------------------------------------------------------|
| Framework | React                   | 19      | UI componentes                                          |
| Lenguaje  | TypeScript              | 6       | Configuración estricta (ver §1.3)                       |
| Bundler   | Vite                    | 8       | Build + dev server                                      |
| Linter    | ESLint                  | 10      | Flat config                                             |
| Router    | `react-router-dom`      | ^7      | Hash routing (`createHashRouter`)                       |
| Tabla     | `@tanstack/react-table` | ^9      | Headless, sort/pagination client-side                   |
| SQLite    | `sql.js`                | —       | SQLite compilado a WebAssembly, ejecuta en el navegador |
| CSS       | Ninguno                 | —       | CSS plain, ver §0                                       |

### 1.2. Dependencias a instalar

```bash
npm install sql.js @types/sql.js react-router-dom @tanstack/react-table
```

### 1.3. Restricciones de TypeScript

La configuración `tsconfig.app.json` impone reglas que afectan el estilo del código:

| Setting                      | Valor    | Implicación                                                                       |
|------------------------------|----------|-----------------------------------------------------------------------------------|
| `verbatimModuleSyntax`       | `true`   | Siempre usar `import type` para importaciones de tipos                            |
| `erasableSyntaxOnly`         | `true`   | Sin `enum` (usar `as const` objects), sin `namespace`, sin constructor properties |
| `noUnusedLocals`             | `true`   | Error en variables no usadas                                                      |
| `noUnusedParameters`         | `true`   | Error en parámetros no usados                                                     |
| `noFallthroughCasesInSwitch` | `true`   | Sin fallthrough en `switch`                                                       |
| `target`                     | `es2023` | Output moderno                                                                    |

### 1.4. Convenciones de código

| Regla                   | Valor                                                                         |
|-------------------------|-------------------------------------------------------------------------------|
| Nombres de componentes  | PascalCase (`SourceDetailPage.tsx`)                                           |
| Nombres de funciones    | camelCase (`fetchSources`)                                                    |
| Archivos de componentes | Un componente por archivo, nombre igual al componente                         |
| Tipos                   | `type` en vez de `interface`; exportar con `export type` si solo es tipo      |
| Enums                   | Prohibidos. Usar `as const` objects: `const FORMAT = { PDF: 'PDF' } as const` |
| Tipos de datos          | Definir como `type` en `src/types/` con `import type` desde componentes       |
| Props de componentes    | Definir como `type` justo encima del componente, no en archivo separado       |

---

## 2. Rutas y navegación

### 2.1. Estructura de rutas

El router usa `createHashRouter` de `react-router-dom` v7. Las rutas son relativas al hash (`/#/ruta`).

| Ruta            | Componente         | Descripción                                                        |
|-----------------|--------------------|--------------------------------------------------------------------|
| `#/`            | `SourcesPage`      | Lista de sources con tabla, filtros y búsqueda                     |
| `#/sources/:id` | `SourceDetailPage` | Detalle de un source: metadatos, edición, gestión de tags          |
| `#/authors`     | `AuthorsPage`      | Lista de autores con búsqueda                                      |
| `#/tags`        | `TagsPage`         | CRUD de tags: crear, renombrar, eliminar                           |

No existe ruta de reconciliación. El usuario re-scanea ejecutando el CLI del agente.

### 2.2. Flujo de navegación

**Componentes de navegación:**

- **Header horizontal** fijo en la parte superior con 4 links de navegación.
- Cada link apunta a una ruta: Sources (`#/`), Authors (`#/authors`), Tags (`#/tags`).
- El link activo se marca con clase CSS `active` (aplicar `font-weight: bold` como mínimo visual).

**Flujos entre páginas:**

```
#/ (Sources) ──click fila──▶ #/sources/:id (Detalle)
                                  │
                                  ▼
                          "Volver" → #/ (preservando filtros)

#/authors ──click autor──▶ #/?authorId=uuid

#/tags ──click tag──▶ #/?tagId=uuid
```

### 2.3. Query parameters en `#/`

La página de sources (`#/`) gestiona filtros vía `useSearchParams()` de react-router-dom.

| Param        | Tipo     | Ejemplo             | Origen                         |
|--------------|----------|---------------------|--------------------------------|
| `q`          | `string` | `?q=garcia`         | Búsqueda inline                |
| `authorId`   | `UUID`   | `?authorId=abc-123` | Click desde Authors            |
| `tagId`      | `UUID`   | `?tagId=def-456`    | Click desde Tags               |
| `format`     | `enum`   | `?format=PDF`       | Select de formato              |
| `page`       | `int`    | `?page=2`           | Paginación                     |
| `sort`       | `string` | `?sort=year,desc`   | Click en encabezado de columna |

**Nota:** Los query params se conservan al navegar entre páginas. Al hacer click en "Volver" desde el
detalle, se preserva el estado de filtros de la lista.

---

## 3. Gestión de base de datos SQLite en el navegador

El frontend carga y gestiona un archivo SQLite (.db) completamente en el navegador. No hay servidor backend.
Todos los datos permanecen en el dispositivo del usuario.

### 3.1. sql.js — carga del archivo .sqlite

**Qué es sql.js:** SQLite compilado a WebAssembly. Permite ejecutar queries SQL completas desde JavaScript
en el navegador. El archivo `.db` completo se carga a memoria RAM.

**Flujo de carga:**

1. Inicializar sql.js: `initSqlJs()` retorna una promesa con la librería.
2. El usuario selecciona un archivo `.db` desde su dispositivo.
3. Leer el archivo como `ArrayBuffer` con `FileReader`.
4. Crear la instancia: `new SQL.Database(buffer)`.
5. La base de datos queda en memoria, lista para queries.

**Consideraciones:**

- El archivo completo se carga a RAM. Para catálogos personales (miles de archivos) esto es trivial.
- sql.js pesa ~2MB (gzipped). Se descarga una vez y se cachea por el navegador.
- Las queries se ejecutan sincrónicamente en el hilo principal. Para catálogos grandes, considerar
  Web Workers (no implementado en V1).

### 3.2. File System Access API (Chromium)

El `File System Access API` permite leer y escribir archivos directamente en el disco del usuario
sin pasar por un flujo de upload/download.

**Leer archivo:**

1. `window.showOpenFilePicker()` abre un diálogo de selección de archivo.
2. El usuario selecciona el `.db`.
3. `fileHandle.getFile()` retorna el contenido.
4. `arrayBuffer()` obtiene el `ArrayBuffer` para sql.js.

**Guardar cambios:**

1. `window.showSaveFilePicker()` abre un diálogo de guardado.
2. El usuario elige dónde guardar el `.db` modificado.
3. `createWritable()` obtiene un stream de escritura.
4. `write()` escribe el contenido modificado.
5. `close()` cierra el stream.

**Persistencia de acceso:**

- `fileHandle.requestPermission({ mode: 'readwrite' })` pide permiso al usuario.
- Si el usuario otorga permiso, se puede re-leer el archivo sin volver a mostrar el diálogo.
- El permiso se pierde al cerrar la pestaña.

**Limitación:** Solo funciona en navegadores Chromium (Chrome, Edge, Opera). No funciona en Firefox ni Safari.

### 3.3. Fallback upload/download (otros navegadores)

Para navegadores que no soportan File System Access API, se usa un flujo manual de upload/download.

**Carga de archivo:**

1. Componente `<input type="file" accept=".db">`.
2. El usuario selecciona el archivo.
3. `FileReader.readAsArrayBuffer()` lee el contenido.
4. `new SQL.Database(buffer)` carga la base de datos en memoria.

**Descarga de cambios:**

1. Obtener el contenido actualizado de la memoria: `db.export()` retorna un `Uint8Array`.
2. Crear un `Blob` con tipo `application/octet-stream`.
3. Crear un `<a>` con `URL.createObjectURL(blob)` y atributo `download="biblocat.db"`.
4. Simular click para descargar.
5. Liberar el `URL.revokeObjectURL()` después de la descarga.

**Flujo completo del usuario:**

```
1. Abrir frontend en el navegador
2. Seleccionar biblocat.db desde el dispositivo (upload)
3. Navegar, buscar, agregar tags, editar metadata
4. Hacer click en "Descargar" (download)
5. El navegador descarga biblocat.db modificado
```

### 3.4. Persistencia y exportación

**Memoria principal:** El `.db` modificado se mantiene en memoria RAM de sql.js. No se guarda
automáticamente en el disco del usuario.

**localStorage como respaldo:** Se puede guardar una copia del `.db` en `localStorage` como
respaldo temporal. Limitaciones: `localStorage` tiene un límite de ~5MB por origin. Si el `.db`
supera este tamaño, se omite el respaldo.

**Exportación JSON:** Como alternativa al `.db` completo, se puede exportar el catálogo como un
archivo `.json` con todos los sources, autores y tags. Más ligero pero pierde la estructura SQL.

**Regla de persistencia:** El usuario es responsable de descargar el `.db` modificado para
preservar sus cambios. Si cierra la pestaña sin descargar, los cambios se pierden (salvo que
se haya usado File System Access API con permiso de persistencia).

---

## 4. Modelo de datos SQLite

### 4.1. Tablas

El frontend opera sobre el mismo schema creado por el agente CLI. Las tablas relevantes son:

| Tabla           | Acceso              | Descripción                                    |
|-----------------|---------------------|------------------------------------------------|
| `sources`       | Lectura + Escritura | Archivos PDF, EPUB, MHTML del catálogo         |
| `authors`       | Solo lectura        | Autores inferidos por el agente (no editables) |
| `tags`          | Lectura + Escritura | Etiquetas creadas por el usuario               |
| `source_tags`   | Lectura + Escritura | Relación muchos a muchos                       |
| `scan_metadata` | Solo lectura        | Control de versiones del schema                |

**Nota sobre authors:** Los autores son inferidos por el agente desde la estructura de carpetas.
El frontend no puede crear ni editar autores. Si el usuario quiere cambiar un autor, debe
renombrar la carpeta en el filesystem y re-ejecutar el scan del agente.

### 4.2. Queries de lectura

**Listar sources activos (con autor y tags):**

```sql
SELECT s.*, a.name AS author_name
FROM sources s
LEFT JOIN authors a ON s.author_id = a.id
WHERE s.deleted_at IS NULL
ORDER BY s.path_lower
```

Los tags se obtienen en una query separada por performance, o se pueden incluir con una subquery
o GROUP_CONCAT.

**Buscar sources por nombre o autor:**

```sql
SELECT s.*, a.name AS author_name
FROM sources s
LEFT JOIN authors a ON s.author_id = a.id
WHERE s.deleted_at IS NULL
  AND (s.name LIKE '%?%' OR a.name LIKE '%?%')
ORDER BY s.path_lower
```

**Filtrar por formato:**

```sql
WHERE s.file_format = ?
```

**Filtrar por tag:**

```sql
WHERE s.id IN (SELECT source_id FROM source_tags WHERE tag_id = ?)
```

**Contar total de sources (para paginación):**

```sql
SELECT COUNT(*) FROM sources WHERE deleted_at IS NULL
```

**Listar autores distinct:**

```sql
SELECT DISTINCT a.id, a.name
FROM authors a
JOIN sources s ON a.id = s.author_id
WHERE s.deleted_at IS NULL
ORDER BY a.name
```

**Listar tags:**

```sql
SELECT * FROM tags ORDER BY name
```

**Tags de un source específico:**

```sql
SELECT t.*
FROM tags t
JOIN source_tags st ON t.id = st.tag_id
WHERE st.source_id = ?
ORDER BY t.name
```

### 4.3. Escritura y transacciones

**Actualizar metadata de un source:**

```sql
UPDATE sources
SET year = ?, edition = ?, url = ?, updated_at = datetime('now')
WHERE id = ?
```

**Crear tag:**

```sql
INSERT INTO tags (id, name) VALUES (?, ?)
```

**Renombrar tag:**

```sql
UPDATE tags SET name = ? WHERE id = ?
```

**Eliminar tag:**

```sql
DELETE FROM tags WHERE id = ?
```

(La eliminación en cascada de `source_tags` se encarga de desasociar el tag de todos los sources.)

**Reemplazar tags de un source:**

```sql
DELETE FROM source_tags WHERE source_id = ?;
INSERT INTO source_tags (source_id, tag_id) VALUES (?, ?);
-- ... repetir por cada tag
```

**Transacciones:** Todas las operaciones de escritura se ejecutan dentro de una transacción SQL
para garantizar atomicidad. Si una operación falla, se revierten todos los cambios del grupo.

```sql
BEGIN TRANSACTION;
-- ... operaciones ...
COMMIT;
```

En sql.js, las transacciones se ejecutan sincrónicamente. No hay problemas de concurrencia porque
el frontend es un solo hilo.

---

## 5. Páginas / Views

### 5.1. Carga de archivo (`FileLoader`)

**Ruta:** No tiene ruta propia. Se muestra condicionalmente en `#/` cuando no hay DB cargada.

**Comportamiento:**

- Si no hay base de datos cargada en memoria, mostrar el componente `FileLoader`.
- Si hay base de datos cargada, ocultar `FileLoader` y mostrar `SourcesPage`.
- `FileLoader` ofrece dos opciones: subir archivo (universal) o seleccionar del disco (Chromium).
- Al cargar exitosamente, pasar la instancia de `SQL.Database` al contexto de la aplicación.

**Estados:**

| Estado      | Comportamiento                                        |
|-------------|-------------------------------------------------------|
| Sin DB      | Mostrar opciones de carga                             |
| Cargando    | Indicador de progreso                                 |
| Error       | Mensaje de error con opción de reintentar             |
| DB cargada  | Ocultar FileLoader, mostrar catálogo                  |

### 5.2. Lista de sources (`SourcesPage`)

**Ruta:** `#/`

**Componentes que la componen:**

| Componente     | Descripción                                                                               |
|----------------|-------------------------------------------------------------------------------------------|
| `SearchBar`    | Input de búsqueda. Sincronizado con `?q=`. Búsqueda se aplica al escribir (con debounce). |
| `FormatFilter` | Select con opciones: Todos, PDF, EPUB, MHTML. Sincronizado con `?format=`.                |
| `SourcesTable` | Tabla paginada con TanStack Table (ver columnas abajo).                                   |
| `Pagination`   | Controles Prev/Next + indicador "Página X de Y".                                          |

**Tabla de sources — Columnas:**

| Columna     | Campo SQL        | Sortable | Notas                                                          |
|-------------|------------------|----------|----------------------------------------------------------------|
| Nombre      | `name`           | sí       | Click → cambia `?sort=name,asc` o `name,desc`                  |
| Autor       | `author_name`    | sí       | Click → `?sort=author_name,asc`. Source sin autor: celda vacía |
| Formato     | `file_format`    | sí       | Badge de texto: PDF, EPUB o MHTML                              |
| Año         | `year`           | sí       | Puede ser null (celda vacía)                                   |
| Edición     | `edition`        | no       | Puede ser null                                                 |
| Tags        | (de source_tags) | no       | Todos visibles como badges de texto                            |
| Creado      | `created_at`     | sí       | Formato ISO 8601 o legible                                     |
| Actualizado | `updated_at`     | sí       | Solo si no es null                                             |

**Paginación y sort client-side:**

TanStack Table gestiona la paginación y el ordenamiento completamente en el navegador. No hay
requests a un servidor. Los datos se cargan de SQLite una vez y se filtran/ordenan en memoria.

- La paginación usa un tamaño de página fijo (default: 20 filas).
- El sort se aplica sobre el array de datos en memoria.
- Los query params `?page=` y `?sort=` sincronizan el estado con la URL.

**Estados de la página:**

| Estado   | Comportamiento                                              |
|----------|-------------------------------------------------------------|
| Sin DB   | Mostrar `FileLoader` en vez de la tabla                     |
| Empty    | Mensaje "No se encontraron sources" con la tabla vacía      |
| Success  | Tabla con datos, paginación activa, sort funcional          |

### 5.3. Detalle de source (`SourceDetailPage`)

**Ruta:** `#/sources/:id`

**Componentes que la componen:**

| Componente      | Descripción                                      |
|-----------------|--------------------------------------------------|
| `SourceInfo`    | Campos de identificación (solo lectura)          |
| `MetadataForm`  | Formulario de edición de year, edition, url      |
| `TagManager`    | Lista de tags actuales + selector para reasignar |
| `BackButton`    | Botón "Volver" → `useNavigate(-1)`               |

**Sección 1 — Identificación (solo lectura):**

| Campo       | Fuente               | Formato                       |
|-------------|----------------------|-------------------------------|
| Nombre      | `source.name`        | Texto plano                   |
| Path        | `source.path`        | Texto plano, estilo monospace |
| Formato     | `source.file_format` | Badge: PDF / EPUB / MHTML     |
| Autor       | `author_name`        | Link a `/#/?authorId={id}`    |
| Creado      | `source.created_at`  | ISO 8601                      |
| Actualizado | `source.updated_at`  | ISO 8601, solo si no null     |

**Sección 2 — Metadatos editables (form):**

| Campo     | Tipo input | Validación                                          |
|-----------|------------|-----------------------------------------------------|
| `year`    | `number`   | Entero positivo o null                              |
| `edition` | `text`     | Máximo 50 caracteres, null permitido                |
| `url`     | `url`      | HTTP/HTTPS, máximo 2048 chars, null permitido       |

- Botón "Guardar" → ejecuta `UPDATE sources SET year=?, edition=?, url=? WHERE id=?` en SQLite.
- Después de guardar exitosamente, mostrar confirmación y mantener los datos en la página.

**Sección 3 — Gestión de tags:**

- Lista de tags actuales como badges de texto.
- Cada badge tiene un botón "×" para quitar el tag.
- Select o input para agregar un tag existente (carga `SELECT * FROM tags` para opciones).
- Al modificar tags → `DELETE FROM source_tags WHERE source_id = ?` + `INSERT INTO source_tags` por cada tag.
- **Nota:** La operación reemplaza **todos** los tags. El componente debe mantener el estado local
  de tags seleccionados y ejecutar el DELETE + INSERT completo.

**Sección 4 — Navegación:**

- Botón "Volver" → `useNavigate(-1)` o link explícito a `#/`.

### 5.4. Lista de autores (`AuthorsPage`)

**Ruta:** `#/authors`

**Componentes que la componen:**

| Componente    | Descripción                                                            |
|---------------|------------------------------------------------------------------------|
| `SearchBar`   | Input de búsqueda. Sincronizado con `?q=`. Filtra la lista localmente. |
| `AuthorsList` | Lista simple de autores.                                               |

**Comportamiento:**

- Input de búsqueda en la parte superior.
- La lista se carga una vez con `SELECT DISTINCT a.id, a.name FROM authors a JOIN sources s ON a.id = s.author_id WHERE s.deleted_at IS NULL`.
- Al escribir, filtrar la lista localmente (no hay debounce porque no hay servidor).
- Lista vertical de autores. Cada item muestra el nombre del autor.
- **Click en un autor** → `navigate('/?authorId={id}')`. Esto navega a la lista de sources con el filtro pre-aplicado.
- Sin paginación (el número de autores es pequeño).
- Estados: empty ("No se encontraron autores"), success.

### 5.5. Gestión de tags (`TagsPage`)

**Ruta:** `#/tags`

**Componentes que la componen:**

| Componente  | Descripción                                                            |
|-------------|------------------------------------------------------------------------|
| `SearchBar` | Input de búsqueda. Sincronizado con `?q=`. Filtra la lista localmente. |
| `TagForm`   | Formulario de creación: input + botón "Crear".                         |
| `TagsList`  | Lista de tags con acciones inline.                                     |

**Creación de tag:**

- Input de texto + botón "Crear".
- Al enviar → `INSERT INTO tags (id, name) VALUES (?, ?)`. El ID se genera con `crypto.randomUUID()`.
- El nombre se normaliza a lowercase antes de insertar.
- Si el tag ya existe (violation en UNIQUE) → mostrar error "Ya existe un tag con ese nombre".
- Después de crear exitosamente, el tag aparece en la lista (refetch `SELECT * FROM tags`).

**Lista de tags:**

- Cada item muestra: nombre del tag.
- **Doble click en el nombre** → se convierte en input inline editable.
  - Al presionar Enter o perder foco → `UPDATE tags SET name = ? WHERE id = ?`.
  - Si el nombre nuevo ya existe (violation en UNIQUE) → mantener el nombre original.
  - Escape → cancelar edición.
- **Botón "Eliminar"** → `window.confirm("¿Eliminar este tag? Se desasociará de todos los sources.")` → `DELETE FROM tags WHERE id = ?`.
- **Click en el nombre del tag** (sin doble click) → `navigate('/?tagId={id}')`. Navega a la lista de sources con el filtro pre-aplicado.
- La búsqueda filtra la lista localmente.
- Estados: empty ("No hay tags creados"), success.

---

## 6. Configuración

### 6.1. Scripts de npm

| Script    | Comando                | Propósito                        |
|-----------|------------------------|----------------------------------|
| `dev`     | `vite`                 | Servidor de desarrollo con HMR   |
| `build`   | `tsc -b && vite build` | Type check + build de producción |
| `lint`    | `eslint .`             | Linting con ESLint flat config   |
| `preview` | `vite preview`         | Preview del build de producción  |

No hay variables de entorno. No hay proxy de desarrollo. No hay backend al que conectarse.
