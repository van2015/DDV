# DDV — Detección de Diferencias Versionadas

Herramienta de análisis de evolución estructural del código: detecta cambios de clases, métodos y dependencias directas a partir de *diffs* de commits, y genera diagramas UML por commit.

## Estructura

- `AGENTS.md` — casos de uso (MVP) y reglas del proyecto
- `docs/` — diagramas de casos de uso (`UC-diagram.md`), arquitectura (`architecture.md`), tareas (`UC1-tasks.md`, `UC2-tasks.md`, `UC3-tasks.md`), deuda de diseño diferida (`refactors.md`)
- `pom.xml` — proyecto Maven (Java 25+)
- `src/main/java/ddv/` — código siguiendo la arquitectura **Hexagonal + Pipeline**
  - `domain/` — modelos puros (sin frameworks)
  - `application/` — casos de uso (UC1, UC5) y pipeline (UC2→UC3→UC4)
  - `ports/` — interfaces del contrato hexagonal
  - `adapters/in/cli/` — entrada picocli · `adapters/out/` — salidas (jgit, ast, mermaid, file)

## Requisitos

- **JDK 25+** y **Maven 3.9+**.
- En esta máquina se usa Temurin 25 portable, instalado en `~/.local/jdks/jdk-25.0.5.1+2`:
  ```bash
  export JAVA_HOME=~/.local/jdks/jdk-25.0.5.1+2
  export PATH="$JAVA_HOME/bin:$PATH"
  ```

## Compilar y testear

```bash
export JAVA_HOME=~/.local/jdks/jdk-25.0.5.1+2   # ver Requisitos
export PATH="$JAVA_HOME/bin:$PATH"
mvn clean test-compile
mvn clean test
```

`mvn clean test` genera el informe de cobertura en `target/site/jacoco/index.html` y **falla si algún paquete baja del 80% de líneas** (gate JaCoCo de `pom.xml`, regla `PACKAGE ≥ 0.80`).

## Uso (UC1)

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp "target/classes:$(cat target/cp.txt)" ddv.adapters.in.cli.DdvCommand \
    analyze --repo <ruta|URL> --last 10          # últimos N commits
# ... analyze --repo <ruta|URL> --range A..B     # rango inclusivo (A ancestro de B)
```

Códigos de salida: `0` ok · `1` error de alcance (`ScopeException`) · `2` error de uso de la CLI.
