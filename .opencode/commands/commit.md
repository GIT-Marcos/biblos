---
description: Crea un commit conventional con vista previa y confirmación
---

# /commit

Crea un commit conventional de todos los cambios detectados.

**Uso:**

- `/commit` — ejecuta el flujo completo
- `/commit --no-verify` — salta los hooks pre-commit

## Flujo

1. Ejecuta `git status` para identificar todos los archivos modificados, nuevos y eliminados. Si el working tree está
   limpio, informa al usuario y termina. **Restricción**: deben incluirse TODOS los cambios detectados, no seleccionar
   archivos individualmente ni omitir ninguno.

2. Ejecuta `git diff` y `git diff --cached` para inspeccionar el contenido real de los cambios.

3. Analiza los cambios y genera un mensaje de commit siguiendo la especificación Conventional
   Commits (https://www.conventionalcommits.org/en/v1.0.0/):
    - Tipo: feat | fix | docs | style | refactor | perf | test | build | ci | chore | revert
    - Scope: si todos los cambios pertenecen a un solo módulo, inclúyelo entre paréntesis — `(agent)`,`(front)`.
      Si son multi-módulo o involucran archivos raíz, omite el scope.
    - Descripción: en inglés, imperativo, corta (máx 72 chars)
    - Body opcional si el cambio lo requiere

4. **Detección de breaking changes**: si el diff contiene cualquiera de estos patrones, cambia el tipo a `feat!:` o
   `fix!:` y agrega un footer `BREAKING CHANGE: <descripción>`:
    - Eliminación de métodos, clases o funciones públicas
    - Modificaciones en archivos de configuración críticos (pom.xml raíz, package.json raíz, etc.)
    - Renombrado, eliminación o creación de funcionalidades

5. Muestra al usuario una vista previa formateada así:
   ```
   Archivos a commitear:
     - agent/src/main/java/.../Foo.java (modificado)
     - front/src/components/Bar.tsx (nuevo)
   
   Mensaje propuesto:
   feat(agent): add Foo service for bar processing
   ```

6. Usa la herramienta `question` para preguntar "¿Confirmas el commit?" con estas opciones. **Restricción**: NUNCA
   omitas este paso, sin importar cuán obvio parezca el commit. Siempre debes mostrar la vista previa y esperar
   confirmación explícita. Si el usuario elige "Editar mensaje", muestra primero el mensaje actual tal como fue
   propuesto, y luego pide el nuevo.
    - "Confirmar" → ejecuta el commit con el mensaje propuesto
    - "Editar mensaje" → muestra el mensaje actual y permite al usuario escribir uno nuevo antes de commitear
    - "Cancelar" → no hace nada, termina

7. Si el usuario confirma o edita, ejecuta el commit con la herramienta bash:
    - Si el argumento `$ARGUMENTS` contiene `--no-verify`, usa: `git add -A && git commit --no-verify -m "<mensaje>"`
    - Si no: `git add -A && git commit -m "<mensaje>"`
