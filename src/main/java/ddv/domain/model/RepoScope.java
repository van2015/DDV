package ddv.domain.model;

import ddv.domain.exception.ScopeException;

/**
 * Alcance de análisis — salida del UC1 y entrada del pipeline.
 *
 * <p>Responde a las dos formas de definir el historial del caso de uso
 * «Conectar y Definir el Alcance del Repositorio»:</p>
 * <ul>
 *   <li>{@link Range#Between} — de {@code commit A} hasta {@code commit B}</li>
 *   <li>{@link Range#LastN} — los últimos {@code N} commits</li>
 * </ul>
 *
 * @param location ruta local o URL remota del repositorio (no vacía)
 * @param range    rango de análisis (no nulo)
 */
public record RepoScope(String location, Range range) {

    public RepoScope {
        if (location == null || location.isBlank()) {
            throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                    "La ubicación del repositorio no puede estar vacía");
        }
        location = location.trim();
        if (range == null) {
            throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                    "El rango de análisis es obligatorio (A..B o últimos N)");
        }
    }

    /** Fábrica: rango explícito {@code A..B}. */
    public static RepoScope between(String location, String from, String to) {
        return new RepoScope(location, new Range.Between(from, to));
    }

    /** Fábrica: últimos {@code n} commits. */
    public static RepoScope lastN(String location, int n) {
        return new RepoScope(location, new Range.LastN(n));
    }

    /** Heurística: ¿la ubicación es remota (URL o scp-style {@code git@host:..})? */
    public boolean isRemote() {
        return location.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")
                || location.matches("^[^/@]+@[^/:]+:.+");
    }

    /** Tipos de rango soportados por el MVP. */
    public sealed interface Range permits Range.Between, Range.LastN {

        /** De {@code from} (inclusivo) hasta {@code to} (inclusivo). */
        record Between(String from, String to) implements Range {
            public Between {
                if (from == null || from.isBlank() || to == null || to.isBlank()) {
                    throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                            "El rango A..B requiere los dos extremos (from y to) no vacíos");
                }
                from = from.trim();
                to = to.trim();
            }
        }

        /** Los últimos {@code n} commits de la rama actual. */
        record LastN(int n) implements Range {
            public LastN {
                if (n <= 0) {
                    throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                            "El número de commits debe ser positivo, fue: " + n);
                }
            }
        }
    }
}
