---
description: Construye distribuciones del módulo agent -> app-image con JVM y fat JAR sin JVM.
---

Construye una app-image distribuible del módulo agent usando jpackage con jlink runtime, y la comprime en un archivo
`.zip` listo para subir a GitHub Releases. También genera una distribución ligera (fat JAR sin JVM) para usuarios que ya
tienen Java 21+ instalado.

# Prerequisitos

- JDK 21+ instalado y en el PATH (`java -version` debe mostrar 21.x)
- Directorio `agent/` presente en la raíz del proyecto
- PowerShell disponible (nativo en Windows) para `Compress-Archive` y `Copy-Item`

# Flujo

## Paso 1: Validar entorno

Ejecuta `java -version` para confirmar JDK 21+.

- Si falla o muestra otra versión → abortar con error claro:
  "Se requiere JDK 21+ para jpackage. Versión actual: [output]".

## Paso 2: Verificar directorio

Comprueba que `agent/` existe en la raíz del proyecto.

- Si no existe → abortar: "Directorio agent/ no encontrado".

## Paso 3: Leer versión

Extrae `<appVersion>` del plugin `jpackage-maven-plugin` en `agent/pom.xml`:

```bash
grep -oP '<appVersion>\K[^<]+' agent/pom.xml
```

- Si no se encuentra → abortar: "No se encontró <appVersion> en pom.xml".

## Paso 4: Ejecutar build

Desde `agent/`, ejecuta:

```
./mvnw clean package jpackage:jpackage
```

- Si el usuario proporcionó argumentos adicionales, añadirlos al final.
- Si falla → abortar SIN comprimir. Los artefactos parciales se mantienen para debug.

## Paso 5: Verificar app-image

Comprueba que existe `agent/dist/biblos-agent/biblos-agent.exe`.

- Si no existe → abortar: "app-image no generada. Revisar logs de jpackage."

## Paso 6: Comprimir app-image

```powershell
Compress-Archive -Path "agent\dist\biblos-agent" -DestinationPath "agent\dist\biblos-agent-<version>.zip"
```

Reemplaza `<version>` con el valor obtenido en el paso 3.

## Paso 7: Copiar fat JAR para distribución ligera

```powershell
Copy-Item "agent\target\agent-<version>.jar" "agent\dist\agent-<version>.jar"
```

Reemplaza `<version>` con el valor obtenido en el paso 3.

## Paso 8: Comprimir distribución ligera

```powershell
Compress-Archive -Path "agent\dist\agent-<version>.jar" -DestinationPath "agent\dist\agent-<version>-slim.zip"
```

Reemplaza `<version>` con el valor obtenido en el paso 3.

## Paso 9: Reportar resultado

**Si éxito**, mostrar:

- `agent/dist/biblos-agent-<version>.zip` — app-image con JVM (~[tamaño] MB)
- `agent/dist/agent-<version>-slim.zip` — fat JAR sin JVM (~[tamaño] MB)
- Tamaño de cada archivo

**Si falla el build**, mostrar:

- Error de Maven
- Últimas 20 líneas del log

## Paso 10: Revisión

Revisar todos los logs generados. Si hay problemas, errores o advertencias
inesperadas, evaluar riesgos y generar reporte conciso indicando:

- Problema
- Causa
- Posible solución + trade-off

## Notas

- **Siempre** que ocurra un fallo en cualquier lugar del proceso, detente completamente, haz revisión como en el paso 7
  e informa al desarrollador; NUNCA intentes corregirlo por tu cuenta.
- `clean` elimina artefactos previos.
- `package` compila y empaqueta el JAR.
- `jpackage:jpackage` genera la app-image nativa.
- La app-image resultante es portable y no requiere JDK instalado en la máquina destino.
- El fat JAR requiere Java 21+ en PATH del usuario.
- La versión se lee de `<appVersion>` en `pom.xml`.
- La versión del `.zip` se lee de `<appVersion>` en el `pom.xml` del módulo agent.
