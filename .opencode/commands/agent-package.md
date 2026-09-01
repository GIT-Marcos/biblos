---
description: Construye el ejecutable distribuible del agent (app-image con jpackage) y lo comprime en .zip
---

# /agent-package

Construye una app-image distribuible del módulo agent usando jpackage, y la comprime en un archivo `.zip` listo para
subir a GitHub Releases.

## Prerequisitos

- JDK 21+ instalado y en el PATH (`java -version` debe mostrar 21.x)
- Directorio `agent/` presente en la raíz del proyecto
- PowerShell disponible (nativo en Windows) para `Compress-Archive`

## Flujo

1. **Validar entorno**: ejecuta `java -version` para confirmar JDK 21+. Si falla o muestra otra versión,
   aborta con error claro: "Se requiere JDK 21+ para jpackage. Versión actual: [output]".

2. **Verificar directorio**: comprueba que el directorio `agent/` existe. Si no, aborta e indica al desarrollador el
   motivo.

3. **Leer versión**: extrae el valor de `<appVersion>` del plugin `jpackage-maven-plugin` en `agent/pom.xml`:
   ```bash
   grep -oP '<appVersion>\K[^<]+' agent/pom.xml
   ```
   Si no se encuentra aborta e indica al desarrollador el motivo.

4. **Ejecutar build**: cambia al directorio `agent/` y ejecuta:
   ```
   ./mvnw clean package jpackage:jpackage
   ```
   Si el usuario proporcionó `$ARGUMENTS`, añádelos al final del comando.
   Si el build falla, aborta sin comprimir — los artefactos parciales se mantienen para debug.

5. **Comprimir app-image**: una vez exitoso el build, comprime la carpeta generada:
   ```powershell
   Compress-Archive -Path "agent\target\dist\biblos-agent" -DestinationPath "agent\target\dist\biblos-agent-<version>.zip"
   ```
   Reemplaza `<version>` con el valor obtenido en el paso 3.

6. **Reportar resultado**:
    - Si éxito: muestra la ubicación del `.zip` generado (`agent/target/dist/biblos-agent-<version>.zip`) y el
      tamaño del archivo.
    - Si falla el build: muestra el error y las últimas 20 líneas del log de Maven.

7. **Revisión**: revisa todos los logs generados por la operación. Si encuentras problemas, errores o advertencias
   inesperadas, evalúa riesgos y genera un reporte conciso indicando: problema, causa, posible solución + trade-off.

## Notas

- **Siempre** que ocurra un fallo en cualquier lugar del proceso, detente completamente, haz revisión como en el paso 7
  e informa al desarrollador; NUNCA intentes corregirlo por tu cuenta.
- `clean` elimina artefactos previos.
- `package` compila y empaqueta el JAR.
- `jpackage:jpackage` genera la app-image nativa (instalador o directorio ejecutable según configuración).
- La app-image resultante es portable y no requiere JDK instalado en la máquina destino.
- `Compress-Archive` es un cmdlet nativo de PowerShell, disponible en Windows 10+ sin instalación adicional.
- La versión del `.zip` se lee de `<appVersion>` en el `pom.xml` del módulo agent.
