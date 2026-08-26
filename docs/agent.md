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

**Manejo de errores:**
- Timeout: se salta el archivo y se loguea WARN
- Archivo muy grande: se salta y se loguea WARN
- Write-race detectado: se reintenta (máx 3 veces)
- Error de I/O: se salta el archivo y se loguea WARN
- Algoritmo no disponible: RuntimeException (fallo irrecuperable)

**Formato de salida:**
Hash de 64 caracteres en minúsculas hexadecimales.

**Edge cases:**
- Archivos vacíos: hash constante conocido
- Mismo contenido, diferente nombre: mismo hash (determinístico)
- Archivos que fallan: se excluyen del pipeline, se reintenta en próximo scan

# 3. Inferencia de autor

El agente extrae el nombre del autor desde la carpeta padre inmediata dentro del directorio raíz.

**Reglas:**

1. El path relativo del archivo respecto al directorio raíz se calcula primero.
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
- Extensiones soportadas: .pdf, .epub, .mhtml
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
- Convención de nombres: `V` + 4 dígitos + `__` + descripción
- Solo migraciones versionadas (no repeatable)
- Nuevas columnas siempre nullable o con default (additive-only)
- No eliminar columnas existentes

### Edge cases

#### Foundation

| # | Caso                                   | Comportamiento                    |
|---|----------------------------------------|-----------------------------------|
| 1 | Directorio no existe                   | ScannerException, abortar proceso |
| 2 | Directorio no es legible               | ScannerException, abortar proceso |
| 3 | Archivos con extensiones no soportados | Skip, log DEBUG                   |
| 4 | Subdirectorios inaccesibles            | Skip subárbol, log WARN           |

#### Reconciliation

| #  | Caso                            | Comportamiento                                   |
|----|---------------------------------|--------------------------------------------------|
| 5  | Archivo desapareció             | DELETE (soft-delete)                             |
| 6  | Archivo renombrado              | RENAME (mismo hash, diferente path)              |
| 7  | Archivo modificado              | UPDATE (mismo path, diferente hash)              |
| 8  | Archivo nuevo                   | CREATE                                           |
| 9  | Orphan reaparece                | REACTIVATE                                       |
| 10 | Mismo hash en múltiples sources | selectBestMatch (active > orphan > alphabetical) |
| 11 | Rename + Delete en mismo scan   | Delete se salta (renamedIds)                     |

#### Migration

| #  | Caso                          | Comportamiento         |
|----|-------------------------------|------------------------|
| 12 | Versión de DB no encontrada   | Error, abortar proceso |
| 13 | Migración fallida             | Rollback + Error       |
| 14 | Migración ya aplicada         | Skip, continuar        |
| 15 | Archivo de migración corrupto | Error, abortar proceso |

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
    authors ||--o{ sources : "tiene"
    sources ||--o{ source_tags : "tiene"
    tags ||--o{ source_tags : "tiene"
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

# 6. Backup

Antes de aplicar cambios en reconciliation, el agente crea una copia de seguridad del archivo DB que ya existe.

**Reglas:**

1. Solo se crea backup en modo comparativo (cuando el `.db` ya existe).
2. El backup se renombra a `.db.bak` (reemplaza el backup anterior).
3. Se mantiene solo el último backup (no hay históricos).
4. Si el backup falla (permisos, disco lleno), se loguea WARN pero el escaneo continúa.

**Flujo:**

```
biblocat.db      → se lee el estado previo
biblocat.db.bak  → se crea como copia del original (Files.copy con REPLACE)
biblocat.db      → se sobrescribe con los nuevos datos
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

**Estructura mensaje:**

**Tipos de mensaje:**

| Tipo | Color | Descripción |
|------|-------|-------------|

# 9. Excepciones

**Tabla de excepciones:**

| Excepción       | Disparo             | Respuesta |
|-----------------|---------------------|-----------|

# 10. Testing

## 10.1. Estrategia

| Técnica             | Herramienta                     | Versión | Propósito                                       |
|---------------------|---------------------------------|---------|-------------------------------------------------|

## 10.2. Clases de test y cobertura

| Clase     | Tests | Categoría | Estrategia |
|-----------|-------|-----------|------------|
| **Total** |       |           |            |

# 11. Distribución

