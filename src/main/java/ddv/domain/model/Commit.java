package ddv.domain.model;

import ddv.domain.exception.ScopeException;

import java.time.Instant;
import java.util.Objects;

/**
 * Commit en el dominio DDV (independiente de JGit: el adaptador traduce aquí).
 *
 * @param id         identificador completo (SHA-1/SHA-256 del commit)
 * @param shortId    forma corta para mostrar (si ausente, se deriva de {@code id})
 * @param message    mensaje del commit (primera línea); puede ser vacío, no nulo
 * @param authoredAt instante de autoría; puede ser {@code null} si no existe
 */
public record Commit(String id, String shortId, String message, Instant authoredAt) {

    /** Longitud del shortId derivado cuando no se provee uno explícito. */
    private static final int SHORT_ID_LENGTH = 8;

    public Commit {
        if (id == null || id.isBlank()) {
            throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                    "El identificador del commit no puede estar vacío");
        }
        id = id.trim();
        if (shortId == null || shortId.isBlank()) {
            shortId = id.substring(0, Math.min(SHORT_ID_LENGTH, id.length()));
        }
        Objects.requireNonNull(message, "El mensaje del commit no puede ser nulo");
    }

    /** Commit solo con el SHA; resto con valores por defecto. */
    public static Commit of(String id) {
        return new Commit(id, null, "", null);
    }
}
