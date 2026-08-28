# 0. Filosofía de estilos

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

# 1. Stack detallado de tecnologías y dependencias

## 1.1. Lenguaje y framework

| Capa             | Tecnología | Versión | Para                                                                      |
|------------------|------------|---------|---------------------------------------------------------------------------|
| Lenguaje         | TypeScript | 5.x     | Type safety, IntelliSense, detección de errores en compile-time           |
| Framework UI     | React      | 19.x    | Componentes declarativos, manejo de estado                                |
| Build tool       | Vite       | 6.x     | Dev server, bundler, HMR                                                  |
| SQL en navegador | sql.js     | 1.11+   | SQLite compilado a WebAssembly para ejecutar SQL completo en el navegador |

## 1.2. Dependencias a instalar

| Dependencia        | Motivo                                                                                                              |
|--------------------|---------------------------------------------------------------------------------------------------------------------|
| `sql.js`           | SQLite compilado a WebAssembly. Permite ejecutar SQL completo (lectura y escritura) en el navegador sin servidor    |
| `@types/sql.js`    | Tipos TypeScript oficiales para sql.js (DefinitelyTyped). Proporciona tipos para Database, Statement, SqlValue, etc |
| `react`            | Framework UI declarativo con composición de componentes                                                             |
| `react-dom`        | Renderer de React para navegador DOM                                                                                |
| `react-router-dom` | Navegación por hash routing (requerido según AGENTS.md)                                                             |

## 1.3. Dependencias de desarrollo

| Dependencia            | Motivo                                         |
|------------------------|------------------------------------------------|
| `typescript`           | Compilador de TypeScript                       |
| `@types/react`         | Tipos TypeScript para React                    |
| `@types/react-dom`     | Tipos TypeScript para React DOM                |
| `vite`                 | Dev server y bundler                           |
| `@vitejs/plugin-react` | Plugin de Vite para React (JSX transform, HMR) |

# 2. Estilos

**Ver punto 0**

# 3. Subida de DB

## 3.1. Mecanismo: sql.js

El frontend usa **sql.js** para cargar, leer y modificar archivos SQLite directamente en el navegador.
No hay servidor involucrado — toda la operación ocurre en memoria del cliente.

**Concepto:**

- sql.js compila SQLite a WebAssembly
- La base de datos se carga completamente en memoria (RAM del navegador)
- Se ejecutan queries SQL estándar sobre la DB en memoria
- Los cambios se persisten exportando la DB a un archivo descargable

## 3.2. Flujo de carga

**Pasos:**

1. El usuario selecciona un archivo `.db` o `.sqlite` desde su disco usando un input de tipo archivo
2. El navegador lee el archivo como ArrayBuffer usando FileReader
3. Se convierte el ArrayBuffer a Uint8Array
4. Se inicializa una instancia de Database de sql.js pasando el Uint8Array
5. Se valida el esquema de la DB: verificar que existen las tablas requeridas (`sources`, `authors`, `tags`,
   `source_tags`)
6. Si el esquema es válido: se almacena la instancia de Database en el estado de la aplicación y se muestra el catálogo
7. Si el esquema no es válido: se muestra un error descriptivo al usuario

**Validaciones previas:**

- Extensión del archivo: debe ser `.db` o `.sqlite`
- Tamaño del archivo: mostrar advertencia si supera 50MB (riesgo de memoria insuficiente)
- Contenido: verificar que el archivo no esté vacío (mínimo 100 bytes)
- Magic number: los primeros 16 bytes deben contener `SQLite format 3\000`

**Estado de la aplicación:**

- La instancia de Database se almacena en React Context
- Mientras la DB esté cargada, se muestra el catálogo
- Si se cierra la pestaña sin descargar, se pierden los cambios (ver edge case SJ4)

## 3.3. Validación de esquema

Al cargar la DB, se verifica que existan las tablas y columnas esperadas para la última versión disponible.

Si falta alguna tabla o columna, se muestra un error indicando que la DB no es compatible con la versión actual del
frontend; se indica al usuario que puede actualizar su DB desde el último agent.

# 4. Descarga de DB

## 4.1. Mecanismo: db.export()

La descarga se realiza exportando la instancia de Database de sql.js a un archivo descargable.

**Flujo:**

1. El usuario hace clic en el botón "Descargar DB"
2. Se ejecuta `db.export()` que retorna un Uint8Array con los bytes de la DB
3. Se crea un Blob con tipo MIME `application/octet-stream`
4. Se genera una URL temporal usando `URL.createObjectURL()`
5. Se crea un elemento `<a>` con atributo `href` apuntando a la URL y atributo `download` con el nombre del archivo
6. Se simula un clic en el enlace para iniciar la descarga
7. Se revoca la URL temporal usando `URL.revokeObjectURL()` para liberar memoria

## 4.2. Nombre del archivo

- Mismo nombre + "_" + fecha y hora (ddmmaa-hhmmss).

## 4.3. Consideraciones

- El archivo exportado es SQLite válido y puede abrirse con cualquier herramienta SQLite
- Los cambios se pierden si el usuario no descarga antes de cerrar la pestaña

## 4.4. Guardado automático (backup en localStorage)

Para mitigar la pérdida accidental de cambios, el frontend guarda automáticamente la DB en localStorage:

- **Frecuencia:** Cada 30 segundos después de la última modificación
- **Almacenamiento:** Se exporta la DB a base64 y se guarda en localStorage bajo una clave específica
- **Límite:** localStorage tiene un límite de ~5-10MB según el navegador. Si la DB excede este tamaño, no se puede
  guardar automáticamente
- **Indicador:** Se muestra un indicador visual del estado del guardado automático
- **Restauración:** Al cargar una DB, se verifica si hay un backup en localStorage más reciente; si existe, se pregunta
  al usuario si desea restaurarlo

# 5. Páginas / Views

## 5.1. Carga de archivo / Inicio

**Ruta:** No tiene ruta propia. Se muestra condicionalmente en `#/` cuando no hay DB cargada.

**Propósito:** Permitir al usuario seleccionar un archivo `.db` o `.sqlite` desde su disco para cargarlo en el
navegador; revisar y escribir metadatos.

**Query parameters:** N/A.

## 5.2. Lista de sources

**Ruta:** `#/sources`

**Propósito:** Mostrar todos los sources (archivos PDF, EPUB, MHTML) del catálogo en una tabla paginada con opciones de
búsqueda, filtrado y ordenamiento.

**Query parameters:**

| Param    | Tipo   | Default | Descripción                                               |
|----------|--------|---------|-----------------------------------------------------------|
| `page`   | number | 1       | Número de página                                          |
| `sort`   | string | `name`  | Campo de ordenamiento: `name`, `author`, `year`, `format` |
| `order`  | string | `asc`   | Dirección: `asc` o `desc`                                 |
| `search` | string | —       | Texto de búsqueda (filtra por nombre)                     |
| `format` | string | —       | Filtrar por formato: `PDF`, `EPUB`, `MHTML`               |
| `author` | number | —       | Filtrar por ID de autor                                   |
| `tag`    | number | —       | Filtrar por ID de tag                                     |

## 5.3. Lista de autores

**Ruta:** `#/authors`

**Propósito:** Mostrar todos los autores inferidos por el agente, con el número de sources de cada uno.

**Query parameters:**

| Param    | Tipo   | Default | Descripción                            |
|----------|--------|---------|----------------------------------------|
| `page`   | number | 1       | Número de página                       |
| `sort`   | string | `name`  | Campo de ordenamiento: `name`, `count` |
| `order`  | string | `asc`   | Dirección: `asc` o `desc`              |
| `search` | string | —       | Texto de búsqueda (filtra por nombre)  |

## 5.4. Lista de tags

**Ruta:** `#/tags`

**Propósito:** Mostrar todos los tags creados por el usuario, con el número de sources asociados a cada uno. Permitir
crear, renombrar y eliminar tags.

**Query parameters:**

| Param    | Tipo   | Default | Descripción                            |
|----------|--------|---------|----------------------------------------|
| `page`   | number | 1       | Número de página                       |
| `sort`   | string | `name`  | Campo de ordenamiento: `name`, `count` |
| `order`  | string | `asc`   | Dirección: `asc` o `desc`              |
| `search` | string | —       | Texto de búsqueda (filtra por nombre)  |

## 5.5. Detalle de source

**Ruta:** `#/sources/:id`

**Propósito:** Mostrar la información completa de un source específico: metadata, tags asignados, y opciones de edición.

**Query parameters:** N/A.

**Parámetros de ruta:**

| Param | Tipo   | Descripción             |
|-------|--------|-------------------------|
| `id`  | number | ID del source a mostrar |

## 5.6. Detalle de autor

**Ruta:** `#/authors/:id`

**Propósito:** Mostrar todos los sources de un autor específico.

**Query parameters:**

| Param   | Tipo   | Default | Descripción                           |
|---------|--------|---------|---------------------------------------|
| `page`  | number | 1       | Número de página                      |
| `sort`  | string | `name`  | Campo de ordenamiento: `name`, `year` |
| `order` | string | `asc`   | Dirección: `asc` o `desc`             |

**Parámetro de ruta:**

| Param | Tipo   | Descripción  |
|-------|--------|--------------|
| `id`  | number | ID del autor |

# 6. Edge cases

## 6.1. sql.js

### 6.1.1. Memoria y rendimiento

| #    | caso                                                                                 | solución                                                                                                            | trade-off                                                                              |
|------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| SJ1  | DB excede memoria del navegador (sql.js carga toda la DB en RAM via WASM)            | Validar tamaño antes de cargar; mostrar advertencia si supera 50MB; abortar si supera 100MB                         | Limita tamaño máximo de bibliotecas; alternativa futura: sql.js-httpvfs (solo lectura) |
| SJ2  | Archivo no es SQLite válido (usuario sube archivo corrupto o de otro formato)        | Catch error al inicializar `new SQL.Database()`; mostrar mensaje "El archivo no es una base de datos SQLite válida" | Usuario debe re-subir archivo correcto                                                 |
| SJ3  | Esquema incompatible (DB de versión anterior del agent sin tablas requeridas)        | Validar tablas al cargar usando `PRAGMA table_list`; mostrar error con tablas faltantes                             | Requiere conocimiento del esquema esperado por el frontend                             |
| SJ4  | Pérdida de datos al cerrar pestaña (sql.js almacena en memoria, no en disco)         | Auto-guardar en localStorage cada 30 segundos tras última modificación; mostrar indicador de guardado               | localStorage limitado a ~5-10MB; DBs grandes no se pueden guardar automáticamente      |
| SJ5  | Archivo exportado más grande que el original (overhead de metadatos WASM)            | Documentar comportamiento; no hay solución trivial sin compresión externa                                           | Compresión requeriría librería adicional (p.ej. pako)                                  |
| SJ6  | Concurrencia multipestaña (dos pestañas modifican la misma DB)                       | Detectar usando BroadcastChannel; mostrar advertencia al cargar si hay otra pestaña activa                          | Complejidad adicional; no siempre detectable en todos los navegadores                  |
| SJ7  | WASM no carga (CORS, ad-blocker, navegador obsoleto)                                 | Detectar error de carga de WASM; mostrar instrucciones al usuario (desactivar ad-blocker, usar navegador moderno)   | Requiere navegador con soporte WebAssembly                                             |
| SJ8  | Tipos de datos SQLite → JavaScript (INTEGER puede ser number o bigint)               | Usar `number` para todos los campos INTEGER (suficiente para IDs en el rango normal)                                | IDs > 2^53 podrían tener pérdida de precisión (muy improbable en Biblos)               |
| SJ9  | Transacciones parciales (error mid-query deja DB en estado inconsistente en memoria) | Usar transacciones SQL explícitas (BEGIN/COMMIT/ROLLBACK) para todas las operaciones de escritura                   | Complejidad adicional en el código de acceso a datos                                   |
| SJ10 | Archivos muy grandes (>100MB) causan tiempo de carga excesivo                        | Mostrar progress bar durante la carga; considerar lazy loading de la UI                                             | Complejidad de implementación;体验 de usuario degradada                                  |

### 6.1.2. Tipos y queries

| #    | caso                                      | solución                                                                               | trade-off                                           |
|------|-------------------------------------------|----------------------------------------------------------------------------------------|-----------------------------------------------------|
| SJ11 | Query SQL con errores de sintaxis         | Validar queries en desarrollo; en producción, catch errores y mostrar mensaje genérico | Errores de SQL pueden ser crípticos para el usuario |
| SJ12 | Results de query vacíos                   | Verificar que `results.length > 0` antes de acceder a `results[0].values`              | Manejo explícito de casos vacíos                    |
| SJ13 | Parámetros de query con tipos incorrectos | Usar parameter binding con `?` en vez de interpolación de strings                      | Prevención de SQL injection y errores de tipo       |
| SJ14 | Múltiples statements en una query         | Usar `db.exec()` para statements simples; `db.prepare()` para queries con parámetros   | `db.exec()` no retorna resultados de SELECT         |

### 6.1.3. Persistencia

| #    | caso                                                     | solución                                                                                               | trade-off                          |
|------|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------|------------------------------------|
| SJ15 | localStorage lleno (límite 5-10MB)                       | Detectar error de `localStorage.setItem()` ; mostrar advertencia "No se puede guardar automáticamente" | Usuario debe descargar manualmente |
| SJ16 | Datos en localStorage corruptos                          | Validar datos al restaurar; si son inválidos, ignorar y cargar DB del disco                            | Pérdida de cambios no guardados    |
| SJ17 | Usuario carga DB diferente a la guardada en localStorage | Preguntar si desea restaurar el backup o descartarlo                                                   | Decisión del usuario               |

## 6.2. Descarga

| #    | caso                                                 | solución                                                                                                | trade-off                                                   |
|------|------------------------------------------------------|---------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| SJ18 | Navegador bloquea descarga automática                | Usar descarga vía enlace `<a>` con atributo `download` (funciona en la mayoría de navegadores modernos) | Algunos navegadores pueden requerir interacción del usuario |
| SJ19 | Nombre de archivo original no disponible             | Usar nombre por defecto `biblos.db`                                                                     | Usuario debe renombrar manualmente                          |
| SJ20 | DB exportada no es válida para herramientas externas | sql.js exporta SQLite estándar; verificar compatibilidad con DB Browser for SQLite                      | Incompatibilidades raras con herramientas muy antiguas      |

## 6.4. Paginación

| #  | caso                                                 | solución                                                               | trade-off                                                       |
|----|------------------------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------------------|
| P1 | Página fuera de rango (page > totalPages o page < 1) | Validar número de página; si es inválido, mostrar página 1             | Evita mostrar páginas vacías                                    |
| P2 | DB cambia entre páginas (usuario modifica registros) | Recalcular total al cambiar de página; mantener posición si es posible | Puede causar saltos si se agregan/eliminan registros            |
| P3 | Ordenamiento cambia                                  | Resetear a página 1                                                    | Evita confusión con resultados diferentes                       |
| P4 | Filtros cambian                                      | Resetear a página 1                                                    | Consistencia con resultados filtrados                           |
| P5 | OFFSET con DB grande (>10k registros)                | SQLite optimiza OFFSET internamente; rendimiento aceptable             | Para DBs muy grandes, considerar paginación por cursor (futuro) |
| P6 | COUNT(*) lento en DB grande                          | Cache de total; invalidar al detectar cambios                          | Precisión vs rendimiento                                        |


# 7. Paginación

## 7.1. Estrategia: SQL OFFSET/LIMIT

Todas las vistas con listas (sources, autores, tags) usan paginación basada en SQL `LIMIT` y `OFFSET`.

**Concepto:**

- Tamaño de página configurable (default: 50 elementos)
- Se ejecuta `SELECT COUNT(*)` para obtener el total de registros
- Se ejecuta `SELECT ... LIMIT PAGE_SIZE OFFSET (page-1)*PAGE_SIZE` para obtener la página actual
- Se renderiza solo la página actual
- Se muestran controles de paginación (anterior, siguiente, indicador de página)

## 7.2. Cálculo de páginas

- Total de páginas: `Math.ceil(total / PAGE_SIZE)`
- Página actual: valor del query parameter `page` (default: 1)
- Página anterior: `page - 1` (si `page > 1`)
- Página siguiente: `page + 1` (si `page < totalPages`)

## 7.3. Reset de paginación

La paginación se resetea a página 1 cuando:

- Cambia el ordenamiento (`sort` o `order`)
- Cambia un filtro (`search`, `format`, `author`, `tag`)
- Se realiza una búsqueda

## 7.4. Query parameters para paginación

Cada vista con lista soporta los siguientes query parameters:

| Param  | Tipo   | Default | Descripción               |
|--------|--------|---------|---------------------------|
| `page` | number | 1       | Número de página (base 1) |

Los query parameters específicos de cada vista se documentan en la sección 5.

# 8. Transaccionalidad

## 8.1. Enfoque: Transacciones SQL explícitas

Todas las operaciones de escritura (INSERT, UPDATE, DELETE) se ejecutan dentro de transacciones SQL para garantizar
atomicidad.

## 8.3. Reglas

- Si una operación falla, se revierten todos los cambios del grupo
- No hay concurrencia real: sql.js es mono-hilo en el navegador
- `db.run()` es síncrono: bloquea el hilo principal durante la ejecución

## 8.4. Manejo de errores

- Si `BEGIN TRANSACTION` falla: la operación no se ejecuta
- Si una operación intermedia falla: se ejecuta `ROLLBACK` automáticamente
- Si `COMMIT` falla: se ejecuta `ROLLBACK` y se notifica al usuario
- Los errores se capturan y se muestran en la UI sin cerrar la aplicación

## 8.5. Consistencia

- Todas las vistas leen de la misma instancia de Database en memoria
- Los cambios son inmediatos para todas las vistas (no hay delay de sincronización)
- La UI se re-renderiza automáticamente al detectar cambios en el estado
