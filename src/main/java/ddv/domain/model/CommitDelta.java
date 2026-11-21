package ddv.domain.model;

import ddv.domain.exception.ScopeException;

import java.util.List;
import java.util.Objects;

/**
 * Δ entre dos commits consecutivos $(C_{i-1}, C_i)$ — salida del UC2 y
 * entrada del análisis estructural.
 *
 * @param previous commit anterior del par
 * @param current  commit actual del par
 * @param files    archivos del diff (puede ser vacío si el commit no toca archivos relevantes)
 */
public record CommitDelta(Commit previous, Commit current, List<FileChange> files) {

    public CommitDelta {
        Objects.requireNonNull(previous, "previous no puede ser nulo");
        Objects.requireNonNull(current, "current no puede ser nulo");
        Objects.requireNonNull(files, "files no puede ser nulo");
        files = List.copyOf(files);
    }

    /** Archivo afectado y tipo de cambio — metadatos que registra el UC2. */
    public record FileChange(String path, ChangeType type) {
        public FileChange {
            if (path == null || path.isBlank()) {
                throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                        "La ruta del archivo no puede estar vacía");
            }
            path = path.trim();
            Objects.requireNonNull(type, "El tipo de cambio no puede ser nulo");
        }
    }

    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED
    }
}
