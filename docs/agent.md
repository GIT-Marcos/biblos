# 1. Stack detallado de tecnologías y dependencias

## 1.1. Lenguaje y build

| Capa          | Tecnología  | Versión | Notas                                         |
|---------------|-------------|---------|-----------------------------------------------|
| Lenguaje      | Java        | 21      | LTS                                           |
| Build         | Maven       | —       | Wrapper en `agent/mvnw`                       |
| Base de datos | SQLite      | —       | Archivo `.db` portátil                        |
| Acceso a BD   | JDBI        | 3.54.0  | Capa sobre JDBC para SQLite                   |
| SQLite JDBC   | sqlite-jdbc | 3.46    | Driver JDBC para SQLite, 0 dependencias extra |
| Logging       | Log4j 2     | 2.23.1  | API directa (`log4j-api` + `log4j-core`)      |

## 1.2. APIs del JDK utilizadas

| Paquete JDK     | Clases principales                            | Propósito                                        |
|-----------------|-----------------------------------------------|--------------------------------------------------|
| `java.nio.file` | `Path`, `Paths`, `Files`, `SimpleFileVisitor` | Escaneo de directorios, operaciones con archivos |
| `java.security` | `MessageDigest`, `DigestInputStream`          | Cálculo de hash SHA-256                          |
| `java.sql`      | `Connection`, `Statement`, `ResultSet`        | Conexión a SQLite via JDBI                       |
| `java.io`       | `IOException`, `InputStream`                  | E/S básica y manejo de errores                   |
| `java.util`     | `List`, `Map`, `Set`, `Properties`            | Colecciones, configuración y utilidades          |
| `java.time`     | `Duration`                                    | Timeouts y configuración temporal                |

## 1.3. Dependencias externas (fuera del JDK)

| Dependencia                           | Versión | Ámbito  | Justificación                                        |
|---------------------------------------|---------|---------|------------------------------------------------------|
| `org.xerial:sqlite-jdbc`              | 3.46    | runtime | Driver JDBC para SQLite, sin dependencias extra      |
| `org.jdbi:jdbi3-core`                 | 3.54.0  | compile | Capa de acceso a SQLite más simple que JDBC directo  |
| `org.jdbi:jdbi3-sqlite`               | 3.54.0  | compile | Plugin SQLite para JDBI (configura tipos y dialecto) |
| `org.apache.logging.log4j:log4j-api`  | 2.23.1  | compile | API de logging estructurado                          |
| `org.apache.logging.log4j:log4j-core` | 2.23.1  | runtime | Implementación de Log4j 2 (consola + rolling file)   |

# 2. Cómputo de hash SHA-256

**Algoritmo:**
SHA-256 estándar del JDK (`java.security.MessageDigest`).

**Modalidad:**
Streaming con buffer de 8KB (`DigestInputStream`). El archivo nunca se carga completo en memoria.

**Protecciones:**

- Timeout por archivo: configurable (default 30s)
- Límite de tamaño: configurable (default 500MB)
- Detección de write-race: compara tamaño antes/después del hash

**Formato de salida:**
Hash de 64 caracteres en minúsculas hexadecimales.

# 3. Inferencia de autor

El agente extrae el nombre del autor desde la carpeta padre inmediata dentro del directorio raíz.

**Reglas:**

1. El path relativo del archivo respecto al directorio raíz se calcula primero.
   **Nota:** El path se normaliza (resolver `..`, `.`) ANTES de inferir el autor (ver §9.8 G3).
2. El **primer segmento** del path relativo se trata como nombre de la carpeta del autor.
3. Si el archivo está directamente en la raíz (sin subcarpetas), `authorName = null`.
4. El nombre se normaliza con `.strip()`.
5. Se preserva el casing original de la carpeta.
6. Windows es case-insensitive, por lo que no hay duplicados de autores.

**Ejemplos:**

| Path en FS                             | authorName inferido        |
|----------------------------------------|----------------------------|
| `Gabriel Garcia Marquez/Cien Anos.pdf` | `"Gabriel Garcia Marquez"` |
| `Anonimo/Medieval/texto.pdf`           | `"Anonimo"`                |
| `subcarpeta/libro.pdf`                 | `"subcarpeta"`             |
| `libro.pdf`                            | `null`                     |
| `  Autor con espacios  /doc.pdf`       | `"Autor con espacios"`     |

**RENAME:**

Cuando un archivo se renombra, el agente re-infiera el autor desde el **nuevo path** (no el antiguo).

Ejemplo:

- Path antiguo: `Autor Viejo/doc.pdf`
- Path nuevo: `Autor Nuevo/doc.pdf`
- authorName: `"Autor Nuevo"`

# 4. Procesos del agent

**Fuentes de verdad:**

| Fuente          | Contenido                                                  |
|-----------------|------------------------------------------------------------|
| Filesystem (FS) | Estado actual de los archivos (nombres, rutas, contenido)  |
| SQLite (.db)    | Estado conocido del catálogo (metadatos, tags, relaciones) |

**Diferencias clave:**

| Aspecto                     | Foundation                | Reconciliation                     | Migration             |
|-----------------------------|---------------------------|------------------------------------|-----------------------|
| ¿Lee SQLite previo?         | No                        | Si                                 | Si (estructura de DB) |
| ¿Preserva tags del usuario? | No (genera SQLite limpio) | Si                                 | Si                    |
| ¿Detecta renames?           | No (todo es CREATE)       | Si (compara content hash)          | No                    |
| ¿Detecta deletes?           | No (no hay estado previo) | Si (archivos que desaparecieron)   | No                    |
| ¿Detecta soft-delete?       | No                        | Si (archivos ausentes → deletedAt) | No                    |
| ¿Cambia estructura de DB?   | No (solo contenido)       | No (solo contenido)                | Si                    |

**Clasificación de archivos:**

Por cada archivo en el FS, el agente determina su relación con el estado conocido (almacenado en SQLite)
aplicando la siguiente tabla de decisión:

| # | Archivo en FS | Existe en SQLite (path) | Hash coincide | `deletedAt` en SQLite | Clasificación |
|---|---------------|-------------------------|---------------|-----------------------|---------------|
| A | Si            | Si                      | Si            | No                    | SKIP          |
| B | Si            | Si                      | Si            | Si                    | REACTIVATE    |
| C | Si            | Si                      | No            | No                    | UPDATE        |
| D | Si            | No                      | Si (otro)     | Cualquiera            | RENAME        |
| E | Si            | No                      | No            | —                     | CREATE        |
| F | No            | Si                      | —             | No                    | DELETE        |
| G | No            | Si                      | —             | Si                    | SKIP          |
| H | Si            | Si                      | No            | Si                    | CREATE        |

**Notas sobre la clasificación:**

- Los archivos con hash fallido (contentHash = null) se excluyen del pipeline.
- Las operaciones se ordenan: RENAME → UPDATE → REACTIVATE → CREATE → DELETE.

## 4.1. Foundation

**Cuándo se usa:**

- Primera ejecución del agente
- Cuando el usuario quiere regenerar un catálogo desde cero

**Flujo:**

1. Validar que el directorio raíz existe y es legible
2. Escanear el directorio (walkFileTree)
3. Filtrar por extensiones soportadas (.pdf, .epub, .mhtml)
4. Calcular hash SHA-256 de cada archivo
5. Inferir autor desde la estructura de carpetas
6. Crear base de datos SQLite
7. INSERTAR todos los sources (sin comparar estado previo)

**Reglas de Scan:**

- Extensiones soportadas: .pdf, .epub, .mhtml (cualquier otro formato se ignora silenciosamente)
- Paths normalizados: backslash → forward-slash, Unicode NFC
- Archivos ocultos: procesados normalmente
- Subdirectorios: según profundidad configurable

## 4.2. Reconciliation

**Cuándo se usa:**

- Todas las ejecuciones estándar (después de Foundation)
- Compara estado actual del FS con estado conocido en SQLite

**Flujo:**

1. Obtener estado previo de SQLite (sources conocidos)
2. Escanear directorio actual
3. Calcular hash SHA-256 de cada archivo
4. Clasificar archivos (tabla A-H)
5. Aplicar operaciones (CREATE, RENAME, UPDATE, DELETE)
6. Preservar tags y metadata del usuario

**Reglas de Scan:**

- Mismas que Foundation
- Plus: comparar con estado previo para clasificar

**Resolución de hash duplicado (`selectBestMatch`):**

Cuando múltiples sources en la DB tienen el mismo `content_hash`, el agente debe decidir
a qué source corresponde el archivo del FS. Algoritmo:

1. **active**: source con `deleted_at IS NULL` y mismo hash → ganador directo
2. **orphan**: source con `deleted_at IS NOT NULL` y mismo hash → reactivar
3. **alphabetical**: si hay múltiples candidatos en un nivel →
   a. Preferir el que coincida en `path_lower` con el path esperado (misma carpeta de autor)
   b. Si ninguno coincide → elegir el de menor `path_lower`
   c. Si el candidato ya fue "reclamado" por otro RENAME en el mismo scan → skip y elegir el siguiente

Si no hay candidatos → CREATE (source nuevo).

## 4.3. Migration

**Cuándo se usa:**

- Cuando se necesita actualizar la estructura de la DB
- Ejemplo: agregar columnas, modificar constraints

**Flujo:**

1. Leer versión actual de la DB (scan_metadata.db_version)
2. Comparar con versiones disponibles en archivos SQL
3. Aplicar migraciones en orden secuencial
4. Actualizar versión en scan_metadata

**Reglas de Migración:**

- Convención de nombres: `V` + 3 dígitos + `__` + descripción
- Solo migraciones versionadas (no repeatable)
- Nuevas columnas siempre nullable o con default (additive-only)
- No eliminar columnas existentes

# 5. Persistencia SQLite

## 5.1. Estructuras de datos

### 5.1.1. Source

| Campo        | Tipo    | Constraints                                               | Descripción                                       |
|--------------|---------|-----------------------------------------------------------|---------------------------------------------------|
| id           | INTEGER | PRIMARY KEY, AUTOINCREMENT                                | Identificador único                               |
| name         | TEXT    | NOT NULL                                                  | Nombre del archivo con extensión                  |
| path         | TEXT    | NOT NULL                                                  | Path relativo al directorio raíz                  |
| path_lower   | TEXT    | NOT NULL                                                  | Path en minúsculas (para detección de duplicados) |
| content_hash | TEXT    | NOT NULL                                                  | Hash SHA-256 (64 caracteres hex)                  |
| file_format  | TEXT    | NOT NULL, CHECK (file_format IN ('PDF', 'EPUB', 'MHTML')) | Formato del archivo                               |
| author_id    | INTEGER | FOREIGN KEY → authors(id) ON DELETE SET NULL              | ID del autor (nullable)                           |
| year         | INTEGER | NULL                                                      | Año de publicación (editable por usuario)         |
| edition      | TEXT    | NULL                                                      | Edición (editable por usuario)                    |
| url          | TEXT    | NULL                                                      | URL asociada (editable por usuario)               |
| created_at   | TEXT    | NOT NULL, DEFAULT CURRENT_TIMESTAMP                       | Fecha de creación                                 |
| updated_at   | TEXT    | NOT NULL, DEFAULT CURRENT_TIMESTAMP                       | Fecha de última modificación                      |
| deleted_at   | TEXT    | NULL                                                      | Marcador de soft-delete                           |

### 5.1.2. Author

| Campo | Tipo    | Constraints                | Descripción                          |
|-------|---------|----------------------------|--------------------------------------|
| id    | INTEGER | PRIMARY KEY, AUTOINCREMENT | Identificador único                  |
| name  | TEXT    | NOT NULL, UNIQUE           | Nombre del autor (casing preservado) |

### 5.1.3. Tag

| Campo | Tipo    | Constraints                | Descripción         |
|-------|---------|----------------------------|---------------------|
| id    | INTEGER | PRIMARY KEY, AUTOINCREMENT | Identificador único |
| name  | TEXT    | NOT NULL, UNIQUE           | Nombre del tag      |

### 5.1.4. source_tags

| Campo     | Tipo    | Constraints                                           | Descripción   |
|-----------|---------|-------------------------------------------------------|---------------|
| source_id | INTEGER | NOT NULL, FOREIGN KEY → sources(id) ON DELETE CASCADE | ID del source |
| tag_id    | INTEGER | NOT NULL, FOREIGN KEY → tags(id) ON DELETE CASCADE    | ID del tag    |

PRIMARY KEY: (source_id, tag_id)

## 5.2. Relaciones

```mermaid
erDiagram
    authors ||--o{ sources: "tiene"
    sources ||--o{ source_tags: "tiene"
    tags ||--o{ source_tags: "tiene"
```

**Comportamientos de eliminación:**

| FK                                 | Comportamiento                                           |
|------------------------------------|----------------------------------------------------------|
| sources.author_id → authors.id     | SET NULL (el source se queda sin autor)                  |
| source_tags.source_id → sources.id | CASCADE (eliminar source elimina sus tags)               |
| source_tags.tag_id → tags.id       | CASCADE (eliminar tag lo desasocia de todos los sources) |

## 5.3. Índices

| Nombre                    | Tabla       | Columnas     | Propósito                     |
|---------------------------|-------------|--------------|-------------------------------|
| idx_sources_path_lower    | sources     | path_lower   | Búsqueda por path normalizado |
| idx_sources_content_hash  | sources     | content_hash | Detección de renames          |
| idx_sources_deleted_at    | sources     | deleted_at   | Filtrar activos vs orphans    |
| idx_sources_author_id     | sources     | author_id    | Búsqueda por autor            |
| idx_source_tags_source_id | source_tags | source_id    | Búsqueda por source           |
| idx_source_tags_tag_id    | source_tags | tag_id       | Búsqueda por tag              |

## 5.4. Transaccionalidad

- Todas las operaciones de escritura se ejecutan dentro de una transacción
- Si una operación falla, se revierten todos los cambios del grupo
- SQLite maneja transacciones de forma serial (no hay concurrencia)

## 5.5. Migraciones de schema

Convención de nombres: `V` + 3 dígitos + `__` + descripción en snake_case (ej: `V001__initial_schema.sql`).
Solo migraciones versionadas (no repeatable). Nuevas columnas siempre nullable o con default (additive-only).

| Archivo                    | Descripción                                                                                              |
|----------------------------|----------------------------------------------------------------------------------------------------------|
| `V001__initial_schema.sql` | Crea las tablas `authors`, `sources`, `tags`, `source_tags` con sus columnas, constraints, FKs e índices |

## 5.6. Configuración de conexión SQLite

Al abrir cada conexión a la base de datos, el agente debe ejecutar los siguientes
PRAGMAs antes de realizar cualquier operación:

| PRAGMA         | Valor  | Propósito                                                                   |
|----------------|--------|-----------------------------------------------------------------------------|
| `foreign_keys` | `ON`   | Habilitar enforcement de foreign keys (deshabilitado por defecto en SQLite) |
| `busy_timeout` | `5000` | Esperar 5 segundos si el DB está locked antes de fallar con SQLITE_BUSY     |

**Nota:** Estos PRAGMAs se ejecutan una vez por conexión, antes de cualquier transacción.

# 6. Backup

Antes de aplicar cambios en reconciliation, el agente crea una copia de seguridad del archivo DB que ya existe.

**Reglas:**

1. Solo se crea backup en modo comparativo (cuando el `.db` ya existe).
2. El backup se renombra a `.db.bak` (reemplaza el backup anterior).
3. Se mantiene solo el último backup (no hay históricos).
4. Si el backup falla (permisos, disco lleno), se loguea WARN pero el escaneo continúa.

**Flujo:**

```
biblos.db      → se lee el estado previo
biblos.db.bak  → se crea como copia del original (Files.copy con REPLACE)
biblos.db      → se sobrescribe con los nuevos datos
```

**Nota:** Si el usuario quiere mantener un histórico de backups, debe hacerlo manualmente antes de ejecutar el CLI.

# 7. CLI — Interfaz de línea de comandos

## 7.1. Comandos disponibles

El agente expone un único comando:

| Comando | Descripción                                                  |
|---------|--------------------------------------------------------------|
| `scan`  | Ejecuta el pipeline completo: scan → hash → classify → apply |

**Flujos disponibles:**

| Flujo            | Descripción                                          |
|------------------|------------------------------------------------------|
| `foundation`     | Crear DB desde cero (primera ejecución)              |
| `reconciliation` | Sincronizar FS con DB existente (ejecución estándar) |
| `migration`      | Actualizar schema de la DB                           |

## 7.2. Argumentos y opciones

| Argumento/Opción | Requerido | Descripción                                                     |
|------------------|-----------|-----------------------------------------------------------------|
| `--root-dir`     | Si        | Directorio raíz de la biblioteca                                |
| `--db-path`      | Si        | Ruta al archivo .db                                             |
| `--flow`         | No        | foundation, reconciliation, migration (default: reconciliation) |
| `--max-depth`    | No        | Profundidad máxima de escaneo (default: 10)                     |
| `--batch-size`   | No        | Tamaño de lote para operaciones (default: 50)                   |
| `--timeout`      | No        | Timeout por archivo en segundos (default: 30)                   |

## 7.3. Mensajes de salida y códigos de retorno

**Códigos de retorno:**

| Código | Significado              |
|--------|--------------------------|
| 0      | Éxito                    |
| 1      | Error de configuración   |
| 2      | Directorio no encontrado |
| 3      | Error de escaneo         |
| 4      | Error de hash            |
| 5      | Error de base de datos   |

**Formato de salida:**

| Canal  | Contenido                        |
|--------|----------------------------------|
| stdout | Mensajes de progreso y resultado |
| stderr | Errores y warnings               |

**Reglas de output:**

- Mensajes de progreso en stdout
- Errores y warnings en stderr

# 8. Logging

## 8.1. Estructura del mensaje

**Consola** (formato legible por humano):

```
[HH:mm:ss] [LEVEL] mensaje
```

Patrón Log4j2: `%d{HH:mm:ss} [%level] %msg%n`

**Archivo** (formato detallado con contexto):

```
[yyyy-MM-dd HH:mm:ss] [LEVEL] [thread] loggerName - mensaje
```

Patrón Log4j2: `%d{yyyy-MM-dd HH:mm:ss} [%level] [%t] %c{1.} - %msg%n`

## 8.2. Niveles de log

| Nivel | Uso en el agente                                                                    | Ejemplo                             |
|-------|-------------------------------------------------------------------------------------|-------------------------------------|
| ERROR | Fallos irrecuperables (DB corrupta, algoritmo no disponible)                        | `RuntimeException`                  |
| WARN  | Situaciones recuperables pero inusuales (timeout, archivo muy grande, write-race)   | Archivo excluido del pipeline       |
| INFO  | Progreso estándar del pipeline (inicio, archivos procesados, operaciones aplicadas) | "5 archivos creados, 2 renombrados" |
| DEBUG | Detalles técnicos para debugging (paths, hashes, clasificaciones)                   | Hash calculado: `e3b0c442...`       |
| TRACE | Información extremadamente detallada (cada línea procesada)                         | Solo en desarrollo                  |

## 8.3. Destinos de escritura

| Destino     | Nivel  | Formato                          | Propósito                       |
|-------------|--------|----------------------------------|---------------------------------|
| Console     | INFO+  | Corto, legible                   | Feedback inmediato al usuario   |
| RollingFile | DEBUG+ | Completo, con timestamp y thread | Auditoría y debugging histórico |

## 8.4. Ubicación del archivo de log

La ruta del archivo se deriva de `--db-path`:

- Si `--db-path` es `/biblioteca/biblos.db`, los logs van a `/biblioteca/logs/biblos.log`
- El subdirectorio `logs/` se crea automáticamente si no existe
- Si falla la creación del directorio o archivo, se loguea WARN a consola y la ejecución continúa sin archivo (no
  aborta)

## 8.5. Política de rotación

| Parámetro        | Valor | Descripción                   |
|------------------|-------|-------------------------------|
| `maxFileSize`    | 10 MB | Tamaño máximo por archivo     |
| `maxBackupIndex` | 5     | Número de archivos históricos |
| `totalSizeCap`   | 50 MB | Máximo total (10 MB × 5)      |

Archivos de ejemplo en `/biblioteca/logs/`:

```
biblos.log       ← log actual
biblos-1.log     ← rotación anterior
biblos-2.log     ← dos ejecuciones atrás
```

# 9. Edge Cases

Todos los edge cases del sistema, agrupados por área.

## 9.1. Hash SHA-256

| #  | caso                                                                                                                               | solución                                                                                                                                                                                                                                                                                                                                                                                                                                                              | trade-off                                                                                                                                                                 |
|----|------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| H1 | Archivo vacío (0 bytes): hash válido pero igual para todos los vacíos, falsos positivos en RENAME                                  | WARN: "Archivo vacío, excluido del pipeline: [path]" → `contentHash = null` → se salta el archivo                                                                                                                                                                                                                                                                                                                                                                     | Pierde trazabilidad de archivos vacíos; el usuario es informado via log                                                                                                   |
| H2 | Archivo bloqueado por otro proceso (antivirus, índice): `NoSuchFileException` o hash parcial                                       | Catch `IOException` → WARN + excluir del batch                                                                                                                                                                                                                                                                                                                                                                                                                        | Pierde un archivo; no aborta el pipeline                                                                                                                                  |
| H3 | Symlinks / Junction points: el sistema no soporta symlinks ni Junction points por ahora; `walkFileTree` los ignora silenciosamente | No soportado: skip silencioso + WARN: "Symlink detectado, excluido: [path]" (se loguea una vez por cada symlink encontrado)                                                                                                                                                                                                                                                                                                                                           | Puede excluir archivos legítimos si el usuario organiza su biblioteca con symlinks. Alternativa futura: soportar con opción `--follow-symlinks`                           |
| H4 | Timeout real no se respeta (`DigestInputStream` no tiene timeout): archivo lento puede colgar el hilo indefinidamente              | Timeout best-effort: `ExecutorService.submit(() -> calculateHash(file))` + `Future.get(timeout, TimeUnit.SECONDS)`. Si `TimeoutException` → `future.cancel(true)` + WARN: "Timeout calculando hash: [path]" → archivo excluido. Nota: `cancel(true)` envía `Thread.interrupt()` pero si el hilo está bloqueado en `read()` del OS, puede no efectuarse inmediatamente. Para archivos locales el timeout raramente se dispara; para archivos UNC/rede es más probable. | Si el timeout se dispara, el archivo se excluye del batch; el hilo puede quedar colgado si el OS no retorna — se reclaima cuando el proceso termina (resource leak menor) |
| H5 | Write-race: archivo modificado entre `size()` y hash: hash no refleja contenido real, tamaño difiere                               | Detectar: comparar `size()` antes y después del hash. Si difieren → WARN con ambos tamaños + reemplazar hash existente con el nuevo. La siguiente reconciliation lo clasificará como UPDATE si el hash cambió de nuevo.                                                                                                                                                                                                                                               | El hash puede corresponder a un estado transitorio; se estabiliza en ejecuciones posteriores                                                                              |
| H6 | Archivo en ruta UNC (`\\server\share`) con latencia de red                                                                         | Detectar UNC → incrementar timeout (ej: 300s); registrar WARN antes de procesar                                                                                                                                                                                                                                                                                                                                                                                       | Timeout mayor ralentiza pipeline si hay muchos archivos en red                                                                                                            |
| H7 | Path > 260 caracteres en Windows (MAX_PATH)                                                                                        | Usar prefijo `\\?\` en `Path` para operaciones de I/O. Requiere: (1) Habilitar "Enable Win32 long paths" en Group Policy o Registry, (2) La aplicación debe declarar `longPathAware` en manifest. Si no se habilita → WARN: "Path demasiado largo, archivo excluido: [path]"                                                                                                                                                                                          | Sin habilitación: archivos excluidos silenciosamente. Con habilitación: funciona pero puede causar problemas con herramientas que no soportan paths largos                |

## 9.2. Inferencia de autor

| #  | caso                                                                                    | solución                                               | trade-off                                         |
|----|-----------------------------------------------------------------------------------------|--------------------------------------------------------|---------------------------------------------------|
| A1 | Nombre de carpeta vacío o solo whitespace: `authorName = ""` después de `.strip()`      | Normalizar a `null` si `.strip().isEmpty()`            | Consistente con "sin autor"; evita strings vacíos |
| A2 | Path con `..` o `.` como primer segmento: `authorName = ".."` o `"."`, nombre no válido | Si primer segmento es `.` o `..` → `authorName = null` | Evita nombres inválidos en DB                     |
| A3 | Caracteres especiales en nombre de carpeta (`<`, `>`, `                                 | `, `?`, `*`): path puede ser problemático para el OS   | Preservar original (el FS ya los maneja)          | Más fiel al FS; no valida caracteres |

## 9.3. Foundation

| #  | caso                                                 | solución                                                                                                                   | trade-off                                                                     |
|----|------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| F1 | Directorio no existe                                 | Abortar proceso                                                                                                            | Abortar es el comportamiento más seguro; no hay datos que procesar            |
| F2 | Directorio no es legible (permisos insuficientes)    | Abortar proceso                                                                                                            | Mejor abortar que procesar parcialmente; evita catálogo incompleto            |
| F3 | Archivos con extensiones no soportados (.txt, .docx) | Skip, log DEBUG                                                                                                            | Skip evita crear sources basura; mantiene el catálogo limpio                  |
| F4 | Subdirectorios inaccesibles (permisos denegados)     | `preVisitDirectory()` lanza `FileSystemException` → catch WARN + retornar `SKIP_SUBTREE`                                   | Skip parcial preserva lo procesable; evita abortar por un subdirectorio       |
| F5 | Extensiones case-insensitive (.PDF, .Pdf, .pdf)      | Normalizar extensión a minúsculas antes de comparar. Ejemplo: `archivo.PDF` → extensión = `"pdf"` → se procesa normalmente | Consistente con Windows (case-insensitive); evita ignorar archivos por casing |

## 9.4. Reconciliation

| #     | caso                                                                | solución                                                                                                                                                                                                                                                                                                                                                                                                                                                            | trade-off                                                                                                                                                  |
|-------|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1    | Archivo desapareció del FS                                          | DELETE (soft-delete)                                                                                                                                                                                                                                                                                                                                                                                                                                                | Soft-delete preserva metadata; requiere limpieza manual periódica                                                                                          |
| R2    | Archivo renombrado (mismo hash, diferente path)                     | RENAME (actualiza path y re-infierre autor)                                                                                                                                                                                                                                                                                                                                                                                                                         | Preserva tags y metadata; re-infiere autor desde nuevo path                                                                                                |
| R3    | Archivo modificado (mismo path, diferente hash)                     | UPDATE (actualiza content_hash)                                                                                                                                                                                                                                                                                                                                                                                                                                     | Actualiza hash; pierde trazabilidad de versiones anteriores                                                                                                |
| R4    | Archivo nuevo (no existe en DB)                                     | CREATE (inserta source con metadatos inferidos)                                                                                                                                                                                                                                                                                                                                                                                                                     | Agrega al catálogo; crea autor si no existe                                                                                                                |
| R5    | Orphan reaparece en FS                                              | REACTIVATE (elimina deletedAt)                                                                                                                                                                                                                                                                                                                                                                                                                                      | Reactiva con metadata preservada; puede tener datos desactualizados                                                                                        |
| R6    | Mismo hash en múltiples sources                                     | selectBestMatch (active > orphan > alphabetical)                                                                                                                                                                                                                                                                                                                                                                                                                    | Prioriza activos; puede elegir incorrectamente si hay duplicados legítimos                                                                                 |
| R7    | Rename + Delete en mismo scan                                       | Delete se salta (renamedIds registra el renombrado)                                                                                                                                                                                                                                                                                                                                                                                                                 | Evita borrar un archivo que fue renombrado                                                                                                                 |
| R8    | Archivo renombrado + modificado en mismo scan (path y hash cambian) | Transferir metadata del viejo al nuevo (tags, year, edition, url) + re-inferir autor desde nuevo path; marcar viejo como DELETE                                                                                                                                                                                                                                                                                                                                     | Preserva trabajo del usuario; si el source nuevo ya existía → aplicar R10 (merge)                                                                          |
| R9    | Múltiples archivos con mismo hash (duplicados)                      | Crear ambos como sources separados                                                                                                                                                                                                                                                                                                                                                                                                                                  | Permite duplicados legítimos; puede crear ruido                                                                                                            |
| R10   | Rename a path que ya existe en DB                                   | Merge completo: (1) tags → unión de ambos conjuntos (sin duplicados); (2) metadata → para cada campo (year, edition, url): si source renombrado tiene valor no-null → usar; si tiene null → heredar del target; (3) author → re-inferir desde nuevo path (ignorar ambos); (4) deleted_at → si target tiene deleted_at → eliminarlo; (5) content_hash → usar el del source renombrado; (6) file_format → usar el del source renombrado; (7) eliminar target de la DB | Preserva trabajo del usuario; metadata del renombrado tiene prioridad; author siempre se re-infiere desde nuevo path                                       |
| R11   | DB corrupta o ilegible                                              | Validar `PRAGMA quick_check` al inicio; abortar código 5                                                                                                                                                                                                                                                                                                                                                                                                            | Evita corrupción de datos existentes                                                                                                                       |
| R12   | DB con versión incompatible (agent < DB)                            | Abortar con error claro                                                                                                                                                                                                                                                                                                                                                                                                                                             | Previene corrupción por migración incorrecta                                                                                                               |
| R13   | Archivo movido a otro directorio (cambia autor)                     | Re-inferir autor desde nuevo path                                                                                                                                                                                                                                                                                                                                                                                                                                   | Mantiene consistencia con estructura de carpetas                                                                                                           |
| R14   | Dos ejecuciones simultáneas del agente                              | `busy_timeout` maneja el locking; si persiste → abortar código 5 (error DB)                                                                                                                                                                                                                                                                                                                                                                                         | Previene corrupción; el usuario debe ejecutar secuencialmente                                                                                              |
| R15   | DB locked por ejecución previa abortada                             | `PRAGMA busy_timeout = 5000` espera 5s; si el lock persiste → abortar código 5                                                                                                                                                                                                                                                                                                                                                                                      | Recuperación automática para locks temporales; aborta para locks permanentes                                                                               |
| R16   | Path con normalización Unicode inconsistente (NFC vs NFD)           | Normalizar a NFC almacenar; `path_lower` con `Locale.ROOT` para búsquedas                                                                                                                                                                                                                                                                                                                                                                                           | Puede diferir del path mostrado en el FS                                                                                                                   |
| R17   | Path > 260 caracteres en Windows (MAX_PATH)                         | Usar prefijo `\\?\` en paths largos para operaciones de I/O. Requiere: (1) Habilitar "Enable Win32 long paths" en Group Policy o Registry, (2) La aplicación debe declarar `longPathAware` en manifest. Si no se habilita → WARN: "Path demasiado largo, archivo excluido: [path]"                                                                                                                                                                                  | Sin habilitación: archivos excluidos silenciosamente. Con habilitación: funciona pero puede causar problemas con herramientas que no soportan paths largos |
| R18-1 | Rename case-insensitive (Libro.pdf → libro.pdf)                     | Detectar usando `path_lower`: si `path_lower` coincide → es rename (no delete + create). Actualizar `name` pero mantener author y metadata                                                                                                                                                                                                                                                                                                                          | Consistente con Windows; evita perder metadata por cambio de casing                                                                                        |
| R19-2 | Archivo movido entre carpetas de autor en mismo scan                | Detectar: si DELETE y CREATE tienen mismo `content_hash` → RENAME (transferir metadata, re-inferir autor). Prioridad sobre CREATE individual                                                                                                                                                                                                                                                                                                                        | Preserva metadata; evita duplicados innecesarios                                                                                                           |

## 9.5. Migration

| #  | caso                                                                      | solución                                            | trade-off                                                        |
|----|---------------------------------------------------------------------------|-----------------------------------------------------|------------------------------------------------------------------|
| M1 | Migración fallida (SQL inválido, dependencia faltante, o constraint roto) | Rollback completo + error claro con archivo y línea | Rollback preserva integridad; error claro facilita debugging     |
| M2 | Migración ya aplicada (versión <= versión actual)                         | Skip, continuar                                     | Evita re-aplicar; requiere que las migraciones sean idempotentes |
| M3 | Migración que rompe constraints existentes                                | Validar dependencias antes de aplicar               | Prevención es mejor que corrección; requiere análisis estático   |
| M4 | Rollback parcial (operación falla a mitad)                                | Asegurar UNA transacción para toda la migración     | Atomicidad total; puede ser lento para migraciones grandes       |

## 9.6. Backup

| #  | caso                                                                                 | solución                                                       | trade-off                                                           |
|----|--------------------------------------------------------------------------------------|----------------------------------------------------------------|---------------------------------------------------------------------|
| B1 | Backup ya existe y no se puede sobrescrire (permisos, bloqueado): `Files.copy` falla | Usar `REPLACE_EXISTING` + catch → WARN, continuar sin backup   | Sin backup es arriesgado; no aborta el pipeline                     |
| B2 | Disco lleno durante creación de backup: backup incompleto o corrupto                 | Verificar espacio disponible antes de copiar; si no hay → WARN | Verificar es preventivo; fallar es más seguro                       |
| B3 | DB corrupta antes de backup (`PRAGMA quick_check` falla)                             | Abortar reconciliation; no crear backup de archivo corrupto    | Previene propagar corrupción; el usuario debe reparar la DB primero |

## 9.7. CLI

| #  | caso                                                                                 | solución                                                                                                                    | trade-off                                                                  |
|----|--------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| C1 | `--flow` inválido: parsing falla o comportamiento indefinido                         | Validar contra enum de flows válidos → error con código 1                                                                   | Validación temprana es mejor UX                                            |
| C2 | `--db-path` apunta a directorio, no archivo: `SQLException` confuso                  | Validar que el path es un archivo (no directorio) antes de abrir                                                            | Error claro es mejor UX                                                    |
| C3 | Señal SIGINT durante ejecución (Ctrl+C): pipeline a medio ejecutar, DB inconsistente | Usar `Runtime.getRuntime().addShutdownHook()` (cross-platform): log "Cancelado" → rollback transacción actual → código != 0 | Hook tiene ~10s antes de ser forzado; rollback puede fallar si disco lleno |

## 9.8. General

| #  | caso                                                                                                                                                                     | solución                                                        | trade-off                                                                     |
|----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|-------------------------------------------------------------------------------|
| G1 | Memoria insuficiente para catálogo grande: `OutOfMemoryError`                                                                                                            | Procesar en batches con `--batch-size` (streaming del pipeline) | Requiere implementar batching                                                 |
| G2 | Archivos del sistema en FS (`desktop.ini`, `Thumbs.db`): el agente solo procesa archivos con extensión soportada (.pdf, .epub, .mhtml); otros se ignoran silenciosamente | Verificar extensión antes de procesar (§4.1)                    | Puede excluir archivos legítimos sin extensión estándar (extremadamente raro) |
| G3 | Path relativo con `..` en nombre de archivo (path traversal): path en DB contiene `..` — problemático para frontend                                                      | Normalizar path: resolver `..` y `.` antes de almacenar         | Pierde fidelidad con FS original                                              |

## 9.9. Logging

| #  | caso                                                         | solución                               | trade-off                                                |
|----|--------------------------------------------------------------|----------------------------------------|----------------------------------------------------------|
| L1 | Directorio `logs/` no se puede crear (permisos denegados)    | WARN a consola, continuar sin archivo  | Sin log histórico; ejecución continúa                    |
| L2 | Archivo de log no se puede escribir (disco lleno o permisos) | WARN a consola, continuar sin archivo  | Sin log histórico; ejecución continúa                    |
| L3 | Disco lleno durante escritura de log                         | Log4j maneja internamente              | No aborta (manejo interno de Log4j); log puede truncarse |
| L4 | Ejecución desde directorio sin permisos para crear logs/     | WARN, continuar con consola únicamente | Solo log en consola; sin persistencia histórica          |

# 10. Excepciones

El agente usa un modelo de **excepciones unchecked** (hereda de `RuntimeException`). Cada excepción se clasifica como
**fatal** (aborta el pipeline) o **no-fatal** (se salta el elemento y se continúa).

**Regla:** Si el error afecta la integridad del pipeline completo → fatal. Si afecta solo a un elemento individual →
no-fatal.

| Categoría    | Comportamiento                                  |
|--------------|-------------------------------------------------|
| **Fatal**    | Aborta el pipeline completo, retorna código ≠ 0 |
| **No-fatal** | Se salta el elemento, se loguea WARN, continúa  |

**Tabla de excepciones:**

| Excepción | Descripción | Categoría | Código de retorno |
|-----------|-------------|-----------|-------------------|

# 11. Testing

definir luego de implementar código

## 11.1. Estrategia

| Técnica | Herramienta | Versión | Propósito |
|---------|-------------|---------|-----------|

## 11.2. Clases de test y cobertura

| Clase     | Tests | Categoría | Estrategia |
|-----------|-------|-----------|------------|
| **Total** |       |           |            |

# 12. Distribución

por definir
