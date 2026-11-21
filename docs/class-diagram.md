# Diagrama de Clases — DDV

> 📏 Generado a partir de `src/main/java/ddv` (ver `AGENTS.md` § Reglas, regla 6).
> **Última actualización:** 2026-11-21 — estado tras T3 (`GitSourcePort`).

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
- **`RepositoryHandle` es opaco:** la implementación real (JGit) se oculta en `adapters/out/jgit` (T4); el núcleo solo lo ve como `AutoCloseable`.
- **`Code`** está anidado en `ScopeException` (`ScopeException.Code`).
- **`CommitDelta`** pertenece al UC2 pero su contrato nace en T3 (firma de `GitSourcePort.diff`).

## Pendiente de implementación (no representado aún)

| Tarea | Clases futuras |
| :--- | :--- |
| T4–T5 | `adapters/out/jgit/JGitAdapter` (implementa `GitSourcePort`, `RepositoryHandle`) |
| T6 | `application/uc1/ScopeResolver` |
| T7 | validaciones completas de `ScopeException` |
| T8 | `adapters/in/cli/AnalyzeCommand` (picocli) |
| UC3 | `ParserPort`, `AstParserAdapter`, `StructuralChange` |
| UC4 | `RendererPort`, `MermaidRenderer`, `DiagramSpec` |
| UC5 | `StorePort`, `FileStoreAdapter`, `EvolutionComparer` |
