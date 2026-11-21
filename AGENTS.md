Basado en el alcance del Producto Mínimo Viable (MVP) —que se centra en analizar cambios estructurales simples (clases, métodos y dependencias directas) mediante la detección de *diffs* de commits—, los siguientes son los casos de uso que deben cubrirse.

---

### 🧑‍💻 Actor Principal: Analista/Desarrollador
El usuario final que interactúa con la herramienta para analizar la evolución del código.

### I. Gestión y Configuración (Input)

#### Caso de Uso: Conectar y Definir el Alcance del Repositorio
*   **Objetivo:** Permitir al analista apuntar a un repositorio Git válido y definir qué historial debe ser revisado.
*   **Precondiciones:** El sistema debe estar configurado con credenciales o acceso local a Git.
*   **Flujo Principal:**
    1.  El Analista selecciona la fuente (local o remota).
    2.  El Analista especifica el rango de análisis (ej: `commit A` hasta `commit B`, o últimos $N$ commits).
    3.  El sistema valida que el repositorio exista y que el rango de *commits* sea coherente.

#### Caso de Uso: Ejecutar Detección de Diferencias ($\Delta$)
*   **Objetivo:** Obtener la lista secuencial de cambios estructurales entre cada par de commits consecutivos dentro del rango definido.
*   **Precondiciones:** Se ha ejecutado el caso de uso anterior y se ha seleccionado el rango de análisis.
*   **Flujo Principal:**
    1.  El sistema itera sobre los *commits* $C_1, C_2, \dots, C_n$.
    2.  Para cada par ($C_{i-1}, C_i$), el módulo de Git genera un *diff* a nivel de código fuente (archivos afectados).
    3.  El sistema registra metadatos del cambio: Archivo, Tipo de cambio (Añadido/Modificado/Eliminado), y la lista de archivos que contienen los cambios estructurales relevantes.

### II. Procesamiento Central (Core)

#### Caso de Uso: Analizar Estructura Cambiada por Commit
*   **Objetivo:** Parsear el código afectado en cada *commit* para identificar las nuevas estructuras semánticas sin necesidad de ejecutar el código completo.
*   **Precondiciones:** Se ha obtenido un set de archivos afectados (el $\Delta$) para un commit específico.
*   **Flujo Principal:**
    1.  El sistema utiliza los parsers AST del lenguaje definido (ej: Python).
    2.  Para cada archivo en el $\Delta$:
        a. **Identificar Clases/Estructuras:** Detectar nuevas definiciones de clases (`class`) o estructuras equivalentes.
        b. **Detectar Miembros:** Listar los métodos y atributos que fueron *añadidos* o *eliminados* en comparación con $C_{i-1}$.
        c. **Mapear Dependencias:** Analizar las declaraciones `import` o referencias de tipo para identificar nuevas dependencias estructurales (ej: la Clase A ahora usa la Clase B).
    3.  El sistema guarda los elementos identificados como una representación estructurada en memoria ($S_i$).

### III. Visualización y Reporte (Output)

#### Caso de Uso: Generar Diagrama UML por Commit
*   **Objetivo:** Crear un diagrama visual que represente las estructuras añadidas o modificadas significativamente en el *commit* $C_i$.
*   **Precondiciones:** Se ha completado el caso de uso "Analizar Estructura Cambiada" para $C_i$ y se tiene la representación estructurada $S_i$.
*   **Flujo Principal:**
    1.  El sistema toma los elementos clave ($S_i$) (Clases, Métodos añadidos, Dependencias nuevas).
    2.  Construye el código de notación UML (ej: PlantUML) que refleja solo estos cambios.
    3.  Exporta el diagrama en un formato visualizable y reproducible (PNG/SVG o texto fuente como `.puml`).

#### Caso de Uso: Comparar Evolución Estructural Global
*   **Objetivo:** Permitir al analista ver la trayectoria de una entidad específica (ej: "¿Cómo ha evolucionado la clase `PaymentProcessor`?") a lo largo de todo el historial seleccionado.
*   **Precondiciones:** Se han generado los diagramas para todos los commits del rango definido.
*   **Flujo Principal:**
    1.  El Analista selecciona una Clase o Componente objetivo.
    2.  El sistema recopila todas las modificaciones detectadas en ese elemento a lo largo de $C_1 \dots C_n$.
    3.  Genera un resumen textual y/o un diagrama de "Trajectoria" que muestra el estado del componente y sus dependencias clave en cada punto temporal relevante, ayudando al analista a trazar la evolución funcional.






    El flujo de los casos de uso es inherentemente **secuencial y lineal**, siguiendo la arquitectura de un *pipeline* de procesamiento de datos. El resultado de un caso de uso anterior es el requisito indispensable para poder ejecutar el siguiente.

A continuación, se detallan las dependencias en orden de ejecución:

### ➡️ Dependencias Secuenciales (Flujo Principal)

Estos casos deben ejecutarse en orden cronológico para que el sistema pueda funcionar.

| Caso de Uso Requerido | Depende De | Por Qué Depende De Esto |
| :--- | :--- | :--- |
| **2. Ejecutar Detección de Diferencias ($\Delta$)** | 1. Conectar y Definir el Alcance del Repositorio | No se puede detectar un cambio sin saber *dónde* buscar (repositorio) y *cuál* es el rango de commits a analizar. |
| **3. Analizar Estructura Cambiada por Commit** | 2. Ejecutar Detección de Diferencias ($\Delta$) | El módulo de análisis estático requiere la lista precisa de archivos afectados (el $\Delta$ o los `diffs`) proporcionada por el Caso de Uso 2 para saber exactamente qué código leer y analizar en ese *commit* específico. |
| **4. Generar Diagrama UML por Commit** | 3. Analizar Estructura Cambiada por Commit | El diagrama es la representación visual del resultado del análisis estructural. Si no se ha analizado el estado ($S_i$), no hay datos para dibujar el cambio. |

***Nota sobre el Bucle:*** Los casos de uso 2, 3 y 4 están acoplados en un bucle (iterativo). El sistema debe ejecutar **(UC 2 $\rightarrow$ UC 3 $\rightarrow$ UC 4)** por *cada* commit dentro del rango definido en el Caso 1.

### 🌐 Dependencia de Resumen/Reporte Global

Este caso no es linealmente dependiente, sino que depende de la acumulación de datos generados a lo largo de todo el proceso secuencial.

| Caso de Uso Requerido | Depende De | Por Qué Depende De Esto |
| :--- | :--- | :--- |
| **5. Comparar Evolución Estructural Global** | 2, 3 y 4 (acumulativamente) | Para comparar la evolución global, el sistema debe haber procesado y **almacenado** de manera estructurada todos los estados parciales ($S_1, S_2, \dots, S_n$) generados en las iteraciones de los casos 2 y 3. El resultado final es una síntesis del proceso completo. |

### 📌 Resumen Visual del Flujo (Pipeline)

$$\text{UC 1} \xrightarrow{\text{Define Rango}} \left[ \begin{array}{c} \text{Bucle:} \\ \text{UC 2} \xrightarrow{\Delta\text{ archivos}} \text{UC 3} \xrightarrow{\text{Estructura } S_i} \text{UC 4 (Diagrama)} \end{array} \right] \xrightarrow{\text{Almacenamiento acumulado}} \text{UC 5}$$

---

### 📏 Reglas

1. **Diagramas en Mermaid:** todos los diagramas (casos de uso, flujo, secuencia, dependencias, etc.) deben generarse en sintaxis **Mermaid** para que sean visualizados directamente en el navegador/GitHub, sin necesidad de herramientas externas de renderizado. Los bloques deben ir en ```` ```mermaid ```` dentro de los documentos Markdown de `docs/`.
2. **Arquitectura Hexagonal + Pipeline:** las dependencias apuntan hacia adentro. El núcleo (`domain`, `application`, `ports`) **nunca** importa frameworks ni adaptadores (JGit, picocli, etc.); los adaptadores (`adapters/`) implementan los puertos (`ports/`), nunca al revés. El bucle UC2→UC3→UC4 se modela como pipeline interno de `application/`. Detalle completo en `docs/architecture.md`.
3. **Nunca hacer commit en su lugar:** el agente **no debe ejecutar `git commit`** (ni `git push` ni otras operaciones que alteren el historial). En su lugar, debe **sugerir un mensaje de convención Conventional Commits** (`tipo(ámbito): descripción`) **escrito en inglés** y dejar los cambios preparados (`git add`) para que el usuario confirme el commit.
4. **Comentarios de código mínimos:** no excederse con comentarios en el código. Solo se comenta la **intención de una decisión** o **contextualización no evidente** en el propio código (por qué se hace algo así, restricción externa, caso de uso que motiva el diseño). Si un comentario describe *qué* hace el código, es probablemente redundante: puede indicar código de mala calidad, mal nombrado o métodos/clases poco cohesivos — en ese caso se corrige el código en vez de documentarlo.
5. **Tests en inglés:** la definición de los tests y su código deben estar en inglés — nombres de métodos de prueba, `@DisplayName`, variables y mensajes de aserción.
6. **Diagrama de clases actualizado:** `docs/class-diagram.md` se genera a partir del código real en `src/main/java/ddv`. Cada cambio que añada, elimine o modifique clases, interfaces, enums o relaciones debe actualizar el diagrama en la misma modificación (antes de preparar el commit).