package ddv.application.pipeline;

import ddv.domain.exception.DiffException;
import ddv.domain.model.CommitDelta;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Selección de los archivos del Δ que UC3 puede parsear.
 *
 * <p>La relevancia a este nivel es solo de <em>extensión</em>: qué ficheros son
 * fuente del lenguaje analizable. La relevancia estructural real (¿contiene
 * cambios de clases o métodos?) la decide UC3 sobre su AST.</p>
 *
 * <p>Las extensiones se normalizan en el constructor: se comparan en
 * minúsculas (de ahí que {@link #isRelevant} solo normalice el path) y deben
 * empezar por punto, pues la coincidencia es {@code endsWith} literal.</p>
 *
 * @param extensions extensiones aceptadas con punto (p. ej. {@code .java}),
 *                   sin distinción de mayúsculas
 */
public record SourceFileFilter(Set<String> extensions) {

    public SourceFileFilter {
        Objects.requireNonNull(extensions, "Las extensiones no pueden ser nulas");
        if (extensions.isEmpty()) {
            throw new DiffException(DiffException.Code.INVALID_ARGUMENT,
                    "Debe definirse al menos una extensión de archivo fuente");
        }
        var normalized = new LinkedHashSet<String>();
        extensions.forEach(extension -> normalized.add(normalize(extension)));
        extensions = Set.copyOf(normalized);
    }

    private static String normalize(String extension) {
        var normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            throw new DiffException(DiffException.Code.INVALID_ARGUMENT,
                    "La extensión de archivo fuente debe empezar por punto: «" + extension + "»");
        }
        return normalized;
    }

    /** Filtro del lenguaje MVP: archivos {@code .java}. */
    public static SourceFileFilter javaFiles() {
        return new SourceFileFilter(Set.of(".java"));
    }

    /** Subconjunto de cambios que UC3 puede analizar, en el mismo orden. */
    public List<CommitDelta.FileChange> filter(List<CommitDelta.FileChange> changes) {
        return changes.stream().filter(this::isRelevant).toList();
    }

    public boolean isRelevant(CommitDelta.FileChange change) {
        var path = change.path().toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(path::endsWith);
    }
}
