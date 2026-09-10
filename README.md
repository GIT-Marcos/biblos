# Biblos

Sistema que permite hacer back-up de los metadatos de una biblioteca personal digital local, consultar su contenido y
gestionar etiquetas para las fuentes. A la fecha soporta 3 tipos de archivos: PDF, EPUB y MHTML.

Genera una base de datos portátil (.bd) con la información de la biblioteca. Biblos **no** almacena tus archivos — solo
guarda referencias y metadatos en una base de datos SQLite portátil que puedes leer en el sitio web.

## Cómo funciona

El sistema tiene dos partes independientes:

| Parte        | Tecnología     | Qué hace                                                                                                                                                                           |
|--------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Agent**    | Java CLI       | Escanea tu carpeta de biblioteca, detecta archivos compatibles, calcula hashes SHA-256, infiere autores desde la estructura de carpetas, y crea/actualiza una base de datos SQLite |
| **Frontend** | React + sql.js | Interfaz web para navegar, buscar, filtrar y editar metadatos del catálogo. Corre enteramente en el navegador con WASM (WebAssembly) para las consultas a DB                       |

Hasta el momento, la única manera de indicar el autor de una fuente es que el agent la infiera a partir de la primera
carpeta contenedora dentro de la raíz. En un futuro se piensa agregar relación `many-many` entre autores y fuentes.

## Requisitos

- **Windows** 10/11 (exclusivamente)
- **Java 21+** para el Agent (o usar el distribuible con JVM embebida)
- **Node.js 18+** para desarrollar el Frontend

## Uso rápido

```bash
# Compilar el Agent
cd agent
./mvnw clean package

# Ejecutar scan (primera vez — crea la DB desde cero)
java -jar target/biblos-agent.jar scan \
  --root-dir "C:\Users\tu-usuario\Mi Biblioteca" \
  --db-path "C:\Users\tu-usuario\Mi Biblioteca\biblos.db" \
  --flow foundation

# Ejecutar scan (ejecuciones siguientes — sincroniza cambios)
java -jar target/biblos-agent.jar scan \
  --root-dir "C:\Users\tu-usuario\Mi Biblioteca" \
  --db-path "C:\Users\tu-usuario\Mi Biblioteca\biblos.db"

# Abrir el Frontend
cd ../frontend
npm install
npm run dev
```

Abre `http://localhost:5173` en tu navegador y carga el archivo `.db` que acabas de crear.

## Estructura del proyecto

```
biblos/
├── agent/          # CLI Java — escaneo y sincronización con filesystem
├── frontend/       # React — interfaz de usuario
├── docs/           # Documentación técnica detallada
├── AGENTS.md       # Instrucciones para agentes de IA
└── LICENSE
```

## Formatos soportados

- **PDF** — archivos `.pdf`
- **EPUB** — archivos `.epub`
- **MHTML** — archivos `.mhtml`

## Más información

- [`docs/agent.md`](docs/agent.md) — especificación completa del Agent
- [`docs/front.md`](docs/front.md) — especificación completa del Frontend
- [`AGENTS.md`](AGENTS.md) — instrucciones generales del proyecto
