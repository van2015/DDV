# Propuesta de Tareas — UC1: Conectar y Definir el Alcance del Repositorio

**Estado:** T1–T10 completadas — **UC1 completa**
**Stack:** Java 25+, Maven, JGit, picocli, JUnit 5 + AssertJ + Mockito
**Arquitectura:** Hexagonal + Pipeline (ver `docs/architecture.md`)

---

## Alcance (según `AGENTS.md`)

Seleccionar fuente (local/remota), especificar rango de análisis (`commit A..B` o últimos N commits) y validar que el repositorio exista y el rango sea coherente.

**Supuestos:** acceso Git que ya funcione en el entorno (local o HTTPS anónimo); sin gestión de SSH keys/tokens.

---

## Tareas

| # | Tarea | Paquete hexagonal | Descripción | Entregable |
| :--- | :--- | :--- | :--- | :--- |
| **T1** | ✅ Estructura Maven base | raíz | `pom.xml` con deps (JGit, picocli, JUnit, Mockito); paquetes `domain`, `application`, `ports`, `adapters.in/cli`, `adapters.out/jgit` | `mvn compile` OK — *ver nota de entorno abajo* |
| **T2** | ✅ Modelo de dominio | `domain/model`, `domain/exception` | `RepoScope` (fuente local/URL, rango `A..B` \| `LAST_N`), `AnalysisScope` (lista inmutable $C_1..C_n$), `ScopeException` | POJOs inmutables (records) — `Commit` añadido como modelo propio (evita exponer `RevCommit` de JGit al dominio); 15 tests ✅ |
| **T3** | ✅ Puerto de Git | `ports` | Interfaz `GitSourcePort`: `open(RepoScope)`, `resolveRange(RepoScope)`, `diff(prev, curr)` | Contrato compilable — con `RepositoryHandle` opaco (sin tipos JGit) y `CommitDelta` (contrato de salida de `diff`); `HexagonalArchitectureTest` verifica la regla 2 en bytecode; 16 tests ✅ |
| **T4** | ✅ Adaptador JGit: conexión | `adapters/out/jgit` | Abrir repo local (`Git.open`), clonar/verificar remoto (`cloneRepository`/`lsRemote`); detectar HEAD | `JGitAdapter` (conexión) — local `Git.open`, remoto clona a workspace temporal (`ddv-clone-*`) limpiado en `close()`; HEAD diferido a T5; 5 tests ✅ |
| **T5** | ✅ Adaptador JGit: rango | `adapters/out/jgit` | Resolver `A..B` y últimos N a `List<RevCommit>` (`RevWalk`, `log`); verificar ancestroía | `JGitAdapter` (rango) — devuelve `AnalysisScope` ascendente; switch *sealed* sobre `Range`; `NOT_ANCESTOR`/`COMMIT_NOT_FOUND`/`EMPTY_RANGE`; fixtures compartidos `GitFixtures`; 6 tests ✅ |
| **T6** | ✅ Caso de uso ScopeResolver | `application/uc1` | Orquesta T3–T5: valida fuente, rango coherente, no vacío → devuelve `AnalysisScope` | `ScopeResolver.java` — `open → resolveRange → close` con *try-with-resources* e inyección de puerto; 3 tests (Mockito) ✅ |
| **T7** | ✅ Validaciones y errores | `domain/exception` | Repo inexistente/sin acceso, commits inexistentes, `A` no ancestro de `B`, rango vacío → `ScopeException` con mensajes claros | Matriz de códigos en `docs/class-diagram.md`; tests nuevos: `CommitDeltaTest` (5), archivo como repo → `REPO_NOT_FOUND`, handle ajeno → `INVALID_ARGUMENT`; mensajes auditados ✅ |
| **T8** | ✅ CLI (adaptador de entrada) | `adapters/in/cli` | Comando picocli: `ddv analyze --repo <ruta\|URL> --range A..B \| --last N` → llama a `ScopeResolver` | `AnalyzeCommand` (`Callable<Integer>`, ArgGroup exclusivo, salida por writers de picocli) + `DdvCommand` (raíz/composición, `main`); exit 0/1/2; 8 tests ✅ |
| **T9** | ✅ Salida hacia UC2 | `application`, `domain` | Garantizar que `AnalysisScope` es el contrato que consumirá el bucle UC2→UC3→UC4 | Interfaz estable — javadoc de contrato con las 3 garantías; +4 tests de contrato en `AnalysisScopeTest` (instancias, pares inmutables, `source`, igualdad por valor) y `GitSourcePortContractTest` (bucle `open → diff → close` con puerto falso) ✅ |
| **T10** | ✅ Pruebas y QA | `src/test` | Unitarias del dominio y `ScopeResolver` con `GitSourcePort` mockeado (Mockito); integración con repos JGit temporales (fixtures); E2E del comando CLI | 56 tests; `DdvCommandE2ETest` (CLI real + JGit real sobre `@TempDir`); JaCoCo 0.8.16 con **gate ≥80% líneas por paquete** en `mvn test` — ver métricas abajo ✅ |

### Métricas de cobertura (JaCoCo, 2026-11-23)

| Paquete | Instrucciones | Líneas | Objetivo ≥80% |
| :--- | :--- | :--- | :--- |
| `ddv.application.uc1` | 100,0% | 100,0% | ✅ |
| `ddv.adapters.out.jgit` | 91,0% | 89,5% | ✅ |
| `ddv.adapters.in.cli` | 92,9% | 91,7% | ✅ |
| `ddv.domain.model` | 99,1% | 100,0% | ✅ |
| `ddv.domain.exception` | 100,0% | 100,0% | ✅ |

El *gate* (`jacoco:check`, regla `PACKAGE ≥ 0.80` líneas) se ejecuta en la fase `test` de todo `mvn clean test` y **rompe el build** si un paquete baja del umbral (verificado: a 0.99 falla con `Rule violated for package …`). Reporte HTML/CSV en `target/site/jacoco/`.

---

## Orden y dependencias

```mermaid
flowchart LR
    T1 --> T2 --> T3
    T3 --> T4 --> T5
    T3 --> T6
    T5 --> T6
    T2 --> T7
    T6 --> T7 --> T8 --> T9 --> T10
```

**Secuencia sugerida:** T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10
(T4/T5 pueden paralelizarse con T7 una vez exista T3; T7 puede avanzar en paralelo con T4/T5.)

---

## Notas de alineación arquitectónica

- **T2–T3 son puras** (sin framework): se testean sin JGit ni CLI.
- **T4–T5 son el adaptador de salida** de `GitSourcePort`: si mañana se cambia JGit por invocar al binario `git`, solo se reescribe `adapters/out/jgit`.
- **T6 es el caso de uso UC1**: no conoce JGit ni picocli; solo puertos.
- **T8 es el adaptador de entrada**: traduce argumentos CLI a `RepoScope` y formatea errores de `ScopeException`.

## Nota de entorno

- **Java 25 y mock frameworks:** Mockito 5.24.0 + AssertJ 3.27.6 (trae `byte-buddy:1.17.7`). AssertJ ≤3.26.3 arrastraba `byte-buddy:1.14.18`, incompatible con Java 25 (`ClassFileVersion: Unknown Java version`). Si se añadan dependencias que traigan *byte-buddy*, verificar en `mvn dependency:tree -Dincludes=net.bytebuddy` que todas converjan en ≥1.17.7.

- El entorno dispone de Temurin JDK 25.0.5.1+2 en `~/.local/jdks/jdk-25.0.5.1+2`. Configura `JAVA_HOME` y `PATH` como se indica en `README.md` para compilar y testear con el nivel Java requerido.

## Fuera de alcance de UC1

- Detección de diffs (UC2), análisis AST (UC3), diagramas (UC4/UC5)
- UI web (solo CLI)
- Gestión de credenciales complejas (SSH keys, tokens)
