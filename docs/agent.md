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

| Aspecto                     | Foundation                | Reconciliation                     | Migration            |
|-----------------------------|---------------------------|------------------------------------|----------------------|
| ¿Lee SQLite previo?         | No                        | Si                                 | Si (estrucuta de DB) |
| ¿Preserva tags del usuario? | No (genera SQLite limpio) | Si                                 | Si                   |
| ¿Detecta renames?           | No (todo es CREATE)       | Si (compara content hash)          | No                   |
| ¿Detecta deletes?           | No (no hay estado previo) | Si (archivos que desaparecieron)   | No                   |
| ¿Detecta soft-delete?       | No                        | Si (archivos ausentes → deletedAt) | No                   |
| ¿Cambia estructura de DB?   | No (solo contenido)       | No (solo contenido)                | Si                   |

**Clasificación de archivos:**

Por cada archivo en el FS, el agente determina su relación con el estado conocido (almacenado en SQLite)
aplicando la siguiente tabla de decisión:

| # | Archivo en FS | Existe en SQLite (path) | Hash coincide       | `deletedAt` en SQLite | Clasificación                    |
|---|---------------|-------------------------|---------------------|-----------------------|----------------------------------|

**Notas sobre la clasificación:**

-

## 4.1. Foundation

**Flujo:**

```mermaid
flowchart TD
    
```

### Reglas de Scan


**Pasos:**
1. Scan.
2.

## 4.3. Reconciliation

**Flujo:**

```mermaid
flowchart TD
    
```
### Reglas de Scan


**Pasos:**
1.

## 4.4. Migration

**Flujo:**

```mermaid
flowchart TD
    
```

**Pasos:**
1.

# 5. Persistencia SQLite

## 5.1. Estructuras de datos

### 5.1.1. Source

### 5.1.2. Author

### 5.1.3. Tag

### 5.1.4. ...

## 5.2. Relaciones

```mermaid
erDiagram
    
```
**Comportamientos de eliminación:**

| FK                                   | Comportamiento       |
|--------------------------------------|----------------------|

## 5.3. Gestión de la conexión

## 5.4. Índices

| Nombre | Propósito |
|--------|-----------|

## 5.5. Transaccionalidad


## 5.6. Migraciones de schema

Convención de nombres: `V` + 4 dígitos + `__` + descripción en snake_case (ej: `V0001__initial_schema.sql`).
Solo migraciones versionadas (no repeatable). Nuevas columnas siempre nullable o con default (additive-only).

| Archivo                     | Descripción                                                                                                                                                                                             |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `V0001__initial_schema.sql` | Crea el tipo ENUM `file_format`, las tablas `authors`, `sources`, `tags`, `source_tags` y `reconciliation` con sus columnas, constraints, FKs, índices, y el seed row de `reconciliation` con `id = 1`. |

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

| Comando | Descripción                                                        |
|---------|--------------------------------------------------------------------|


## 7.2. Argumentos y opciones

**Modo no interactivo (`--non-interactive`):**

| Argumento/Opción    | Requerido           | Descripción                                                                        |
|---------------------|---------------------|------------------------------------------------------------------------------------|

**Modo interactivo (default):**

No se requiere ningún argumento. El agente pregunta:

1. 

**Opciones adicionales (ambos modos):**

| Opción                   | Tipo | Default | Descripción                                                   |
|--------------------------|------|---------|---------------------------------------------------------------|

## 7.3. Mensajes de salida y códigos de retorno

**Códigos de retorno:**

| Código | Significado                                                                |
|--------|----------------------------------------------------------------------------|

**Formato de salida:**

| Canal  | Contenido                                                               |
|--------|-------------------------------------------------------------------------|

**Reglas de output:**

-

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

