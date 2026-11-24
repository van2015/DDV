package ddv.domain.model;

import ddv.domain.exception.EvolutionException;

import java.util.List;
import java.util.Objects;

/**
 * Trayectoria de una clase a lo largo del rango — salida del UC5.
 *
 * <p>Recoge, en orden cronológico, cada punto temporal <em>relevante</em> de la
 * clase: los commits en los que aparece un {@link StructuralChange.ClassChange}
 * con su nombre. Los commits donde la clase no cambia no son puntos (decisión
 * de UC5 sobre «punto temporal relevante»).</p>
 *
 * @param className nombre de la clase objetivo (tal cual aparece en los Sᵢ)
 * @param points    puntos temporales, en orden ascendente (al menos uno)
 */
public record ClassTrajectory(String className, List<Point> points) {

    public ClassTrajectory {
        if (className == null || className.isBlank()) {
            throw new EvolutionException(EvolutionException.Code.INVALID_INPUT,
                    "El nombre de la clase no puede estar vacío");
        }
        className = className.trim();
        Objects.requireNonNull(points, "Los puntos de la trayectoria no pueden ser nulos");
        if (points.isEmpty()) {
            throw new EvolutionException(EvolutionException.Code.INVALID_INPUT,
                    "La trayectoria de " + className + " no contiene puntos");
        }
        points = List.copyOf(points);
    }

    /**
     * @param commit commit en el que la clase cambió (su versión nueva)
     * @param change cambio estructural que sufrió en ese commit
     */
    public record Point(Commit commit, StructuralChange.ClassChange change) {

        public Point {
            Objects.requireNonNull(commit, "El commit del punto no puede ser nulo");
            Objects.requireNonNull(change, "El cambio del punto no puede ser nulo");
        }
    }
}
