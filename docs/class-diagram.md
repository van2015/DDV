# Diagrama de Clases — DDV

> 📏 Generado a partir de `src/main/java/ddv` (ver `AGENTS.md` § Reglas, regla 6).
> **Última actualización:** 2026-11-22 — estado tras T7 (validaciones y errores).

---

## Diagrama

```mermaid
classDiagram
    direction TB

    class Commit {
        <<record>>
        +String id
        +String shortId
        +String message
        +Instant authoredAt
        +of(String id)$ Commit
    }

    class RepoScope {
        <<record>>
        +String location
        +Range range
        +between(String location, String from, String to)$ RepoScope
        +lastN(String location, int n)$ RepoScope
        +isRemote() boolean
    }

    class Range {
        <<interface>>
    }

    class Between {
        <<record>>
        +String from
        +String to
    }

    class LastN {
        <<record>>
        +int n
    }

    class AnalysisScope {
        <<record>>
        +RepoScope source
        +List~Commit~ commits
        +size() int
        +first() Commit
        +last() Commit
        +consecutivePairs() List~ConsecutivePair~
    }

    class ConsecutivePair {
        <<record>>
        +Commit previous
        +Commit current
    }

    class CommitDelta {
        <<record>>
        +Commit previous
        +Commit current
        +List~FileChange~ files
    }

    class FileChange {
        <<record>>
        +String path
        +ChangeType type
    }

    class ChangeType {
        <<enumeration>>
        ADDED
        MODIFIED
        DELETED
    }

    class ScopeException {
        <<exception>>
        -Code code
        +ScopeException(Code code, String message)
        +code() Code
    }

    class Code {
        <<enumeration>>
        INVALID_ARGUMENT
        REPO_NOT_FOUND
        NO_ACCESS
        COMMIT_NOT_FOUND
        NOT_ANCESTOR
        EMPTY_RANGE
    }

    class GitSourcePort {
        <<interface>>
        +open(RepoScope scope) RepositoryHandle
        +resolveRange(RepositoryHandle repository, RepoScope scope) AnalysisScope
        +diff(RepositoryHandle repository, Commit previous, Commit current) CommitDelta
    }

    class RepositoryHandle {
        <<interface>>
        +close() void
    }

    class JGitAdapter {
        <<adapter: out/jgit>>
        +open(RepoScope scope) RepositoryHandle
        +resolveRange(RepositoryHandle repository, RepoScope scope) AnalysisScope
        +diff(RepositoryHandle repository, Commit previous, Commit current) CommitDelta
        -openLocal(String location) RepositoryHandle
        -openRemote(String url) RepositoryHandle
    }

    class JGitHandle {
        <<private nested>>
        -Git git
        -Path clonedWorkspace
        +close() void
    }

    class ScopeResolver {
        <<use case: uc1>>
        -GitSourcePort gitSourcePort
        +ScopeResolver(GitSourcePort gitSourcePort)
        +resolve(RepoScope scope) AnalysisScope
    }

    %% Composición del dominio
    AnalysisScope "1" *-- "0..*" Commit : commits (C₁..Cₙ)
    AnalysisScope "1" *-- "1" RepoScope : source
    AnalysisScope "1" *-- "0..*" ConsecutivePair : consecutivePairs()
    CommitDelta "1" *-- "2" Commit : previous, current
    CommitDelta "1" *-- "0..*" FileChange : files
    FileChange "1" --> "1" ChangeType : type
    RepoScope "1" *-- "1" Range : range
    Range <|.. Between : implements
    Range <|.. LastN : implements
    ScopeException "1" *-- "1" Code : code

    %% El puerto depende solo de tipos de dominio
    GitSourcePort ..> RepoScope : open(), resolveRange()
    GitSourcePort ..> RepositoryHandle : open()
    GitSourcePort ..> AnalysisScope : resolveRange()
    GitSourcePort ..> Commit : diff(prev, curr)
    GitSourcePort ..> CommitDelta : diff()

    %% Adaptador de salida (T4)
    GitSourcePort <|.. JGitAdapter : implements
    RepositoryHandle <|.. JGitHandle : implements
    JGitAdapter *-- JGitHandle : crea en open()
    JGitAdapter ..> ScopeException : traduce errores de infraestructura

    %% Caso de uso UC1 (T6)
    ScopeResolver ..> GitSourcePort : usa (inyección)
    ScopeResolver ..> RepoScope : entrada
    ScopeResolver ..> AnalysisScope : salida

    %% Invariantes: constructores compactos lanzan ScopeException
    Commit ..> ScopeException : throws (INVALID_ARGUMENT)
    RepoScope ..> ScopeException : throws (INVALID_ARGUMENT)
    AnalysisScope ..> ScopeException : throws (EMPTY_RANGE)
    CommitDelta ..> ScopeException : throws (INVALID_ARGUMENT)
```

---

## Leyenda

| Relación | Significado |
| :--- | :--- |
| `*--` | Composición (el todo posee a la parte; listas inmutables con `List.copyOf`) |
| `<\|..` | Implementación de interfaz (`Range` es *sealed*) |
| `..>` | Dependencia (uso en parámetros/retorno o lanzamiento de excepción) |
| `«record»` | Registro Java 17+: inmutable, igualdad por valor, constructores compactos validan invariantes |

## Notas de diseño

- **`Commit` no tiene `delta()`:** la Δ se calcula en el adaptador vía `GitSourcePort.diff()`; el dominio solo ensambla `CommitDelta` (discusión 2026-11-21 — evita que `Commit` dependa de infraestructura).
- **`RepositoryHandle` es opaco:** su implementación real es `JGitHandle` (clase interna privada de `JGitAdapter`); maneja `Git` y limpia el workspace temporal del clon en `close()`.
- **`JGitAdapter` traduce errores** de JGit a `ScopeException` (`REPO_NOT_FOUND` en local, `NO_ACCESS` en remoto).
- **`resolveRange()` (T5)** resuelve `A..B` (inclusivo, con `isMergedInto` → `NOT_ANCESTOR`) y últimos N (`setMaxCount` → `EMPTY_RANGE` si HEAD no existe), siempre en **orden ascendente**; `diff()` sigue pendiente hasta UC2.
- **`ScopeResolver` (T6)** orquesta `open → resolveRange → close` con *try-with-resources* mediante inyección de `GitSourcePort`; no conoce JGit ni valida directamente (validan dominio y adaptador).
- **`Code`** está anidado en `ScopeException` (`ScopeException.Code`).

### Matriz de validaciones (T7)

| Código | Origen | Causa |
| :--- | :--- | :--- |
| `INVALID_ARGUMENT` | `RepoScope` / `AnalysisScope` / `JGitAdapter` | `n ≤ 0`, origen/destino en blanco, `Commits` vacío, par con mismo commit, handle ajeno |
| `REPO_NOT_FOUND` | `JGitAdapter.open` (local) | ruta inexistente, directorio no-git, ruta que no es directorio |
| `NO_ACCESS` | `JGitAdapter.open` (remoto) / `resolveRange` | fallo de clone o red; errores inesperados al resolver el rango |
| `COMMIT_NOT_FOUND` | `JGitAdapter.resolveRange` | extremo `A` o `B` inexistente (resuelto o `MissingObjectException`) |
| `NOT_ANCESTOR` | `JGitAdapter.resolveRange` | `A` no es ancestro de `B` (`isMergedInto`) |
| `EMPTY_RANGE` | `AnalysisScope` / `JGitAdapter` | lista vacía, único commit o `A == B`; HEAD sin crear |
- **`CommitDelta`** pertenece al UC2 pero su contrato nace en T3 (firma de `GitSourcePort.diff`).

## Pendiente de implementación (no representado aún)

| Tarea | Clases futuras |
| :--- | :--- |
| T8 | `adapters/in/cli/AnalyzeCommand` (picocli) |
| UC2 | `JGitAdapter.diff()` (Δ entre commits consecutivos) |
| UC3 | `ParserPort`, `AstParserAdapter`, `StructuralChange` |
| UC4 | `RendererPort`, `MermaidRenderer`, `DiagramSpec` |
| UC5 | `StorePort`, `FileStoreAdapter`, `EvolutionComparer` |
