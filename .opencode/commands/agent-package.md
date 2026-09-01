---
description: Construye el ejecutable distribuible del agent (app-image con jpackage)
---

# /agent-package

Construye una app-image distribuible del módulo agent usando jpackage.

## Prerequisitos

- JDK 21+ instalado y en el PATH (`java -version` debe mostrar 21.x)
- Directorio `agent/` presente en la raíz del proyecto

## Flujo

1. **Validar entorno**: ejecuta `java -version` para confirmar JDK 21+. Si falla o muestra otra versión,
   aborta con error claro: "Se requiere JDK 21+ para jpackage. Versión actual: [output]".

2. **Verificar directorio**: comprueba que el directorio `agent/` existe. Si no, aborta.

3. **Ejecutar build**: cambia al directorio `agent/` y ejecuta:
   ```
   ./mvnw clean package jpackage:jpackage
   ```
   Si el usuario proporcionó `$ARGUMENTS`, añádelos al final del comando.

4. **Reportar resultado**:
    - Si éxito: muestra la ubicación de la app-image generada (típicamente en `agent/target/jpackage/`).
    - Si falla: muestra el error y las últimas 20 líneas del log de Maven.

5. **Revisión**: Revisas todos los logs generados por la operación, y si encuentras alguno que podría estar indicando
   problemas, errores, advertencias, etc. no esperados en el flujo de la operación, evalúas riesgos y generas un reporte
   conciso y resumido indicando: problema, causa, posible solución + trade-off de solución.

## Notas

- `clean` elimina artefactos previos.
- `package` compila y empaqueta el JAR.
- `jpackage:jpackage` genera la app-image nativa (instalador o directorio ejecutable según configuración).
- La app-image resultante es portable y no requiere JDK instalado en la máquina destino.
