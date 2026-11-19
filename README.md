# DDV — Detección de Diferencias Versionadas

Herramienta de análisis de evolución estructural del código: detecta cambios de clases, métodos y dependencias directas a partir de *diffs* de commits, y genera diagramas UML por commit.

## Estructura

- `AGENTS.md` — casos de uso (MVP) y reglas del proyecto
- `docs/` — diagramas de casos de uso (`UC-diagram.md`), arquitectura (`architecture.md`), tareas (`UC1-tasks.md`)
- `pom.xml` — proyecto Maven (Java 17+)
- `src/main/java/ddv/` — código siguiendo la arquitectura **Hexagonal + Pipeline**
  - `domain/` — modelos puros (sin frameworks)
  - `application/` — casos de uso (UC1, UC5) y pipeline (UC2→UC3→UC4)
  - `ports/` — interfaces del contrato hexagonal
  - `adapters/in/cli/` — entrada picocli · `adapters/out/` — salidas (jgit, ast, mermaid, file)

## Requisitos

- **JDK 17+** y **Maven 3.9+**.
- ⚠️ En esta máquina, `javac` del sistema apunta al JDK 8 y falta el compilador del JDK 25. Se usa un JDK 17 portable descargado en `~/.local/jdks/jdk-17.0.21.1+2`:
  ```bash
  export JAVA_HOME=~/.local/jdks/jdk-17.0.21.1+2
  ```

## Compilar y testear

```bash
export JAVA_HOME=~/.local/jdks/jdk-17.0.21.1+2   # ver Requisitos
mvn compile
mvn test
```
