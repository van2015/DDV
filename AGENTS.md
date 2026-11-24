# AGENTS.md — Información y reglas para el agente (proyecto DDV)

> **DDV — Detección de Diferencias Versionadas:** herramienta Java (25) que
> analiza la evolución estructural del código —clases, métodos y dependencias
> directas— a partir de *diffs* de commits y genera diagramas UML por commit.

El alcance, los actores, las definiciones y las dependencias de los casos de
uso (UC1–UC5) viven en **[`docs/UC-diagram.md`](docs/UC-diagram.md)**: es la
única fuente de verdad sobre casos de uso — no duplicarlos aquí.

---

## 🗺️ Navegación (información para cualquier tarea)

| Documento | Qué contiene |
| :--- | :--- |
| `README.md` | requisitos (JDK 25 y `JAVA_HOME`), compilar/testear, uso de la CLI |
| `docs/UC-diagram.md` | casos de uso MVP: alcance, actores, dependencias y diagramas Mermaid |
| `docs/architecture.md` | arquitectura Hexagonal + Pipeline, puertos/adaptadores y decisiones |
| `docs/class-diagram.md` | diagrama de clases generado del código real (regla 6) |
| `docs/UC1-tasks.md` … `docs/UC5-tasks.md` | plan, decisiones tomadas y cierre de cada caso de uso |
| `docs/refactors.md` | deuda de diseño con sus **gatillos**: no anticipar; esperar a que salgan |

**Convención de tareas:** cada caso de uso tiene su documento `UCn-tasks.md` y
sus tareas se numeran `U<n>T<m>`; el estado global del proyecto (qué UC están
completas) se lee en la cabecera de esos documentos.

---

## 📏 Reglas

1. **Diagramas en Mermaid:** todos los diagramas (casos de uso, flujo, secuencia, dependencias, etc.) deben generarse en sintaxis **Mermaid** para que sean visualizados directamente en el navegador/GitHub, sin necesidad de herramientas externas de renderizado. Los bloques deben ir en ```` ```mermaid ```` dentro de los documentos Markdown de `docs/`.
2. **Arquitectura Hexagonal + Pipeline:** las dependencias apuntan hacia adentro. El núcleo (`domain`, `application`, `ports`) **nunca** importa frameworks ni adaptadores (JGit, picocli, etc.); los adaptadores (`adapters/`) implementan los puertos (`ports/`), nunca al revés. El bucle UC2→UC3→UC4 se modela como pipeline interno de `application/`. Detalle completo en `docs/architecture.md`.
3. **Nunca hacer commit en su lugar:** el agente **no debe ejecutar `git commit`** (ni `git push` ni otras operaciones que alteren el historial). En su lugar, debe **sugerir un mensaje de convención Conventional Commits** (`tipo(ámbito): descripción`) **escrito en inglés** y dejar los cambios preparados (`git add`) para que el usuario confirme el commit.
4. **Comentarios de código mínimos:** no excederse con comentarios en el código. Solo se comenta la **intención de una decisión** o **contextualización no evidente** en el propio código (por qué se hace algo así, restricción externa, caso de uso que motiva el diseño). Si un comentario describe *qué* hace el código, es probablemente redundante: puede indicar código de mala calidad, mal nombrado o métodos/clases poco cohesivos — en ese caso se corrige el código en vez de documentarlo.
5. **Tests en inglés:** la definición de los tests y su código deben estar en inglés — nombres de métodos de prueba, `@DisplayName`, variables y mensajes de aserción.
6. **Diagrama de clases actualizado:** `docs/class-diagram.md` se genera a partir del código real en `src/main/java/ddv`. Cada cambio que añada, elimine o modifique clases, interfaces, enums o relaciones debe actualizar el diagrama en la misma modificación (antes de preparar el commit).
