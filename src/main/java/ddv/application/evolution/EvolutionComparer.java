package ddv.application.evolution;

import ddv.domain.exception.EvolutionException;
import ddv.domain.model.ClassTrajectory;
import ddv.domain.model.StructuralChange;
import ddv.ports.StorePort;

import java.util.ArrayList;

/**
 * Caso de uso UC5: comparación de la evolución estructural global.
 *
 * <p>De los estados $S_1..S_n$ guardados en el {@link StorePort}, recopila los
 * puntos temporales de una clase concreta —los commits en los que aparece un
 * {@link StructuralChange.ClassChange} con su nombre— en orden cronológico.
 * Conoce solo a {@code domain} y {@code ports}.</p>
 */
public class EvolutionComparer {

    private final StorePort storePort;

    public EvolutionComparer(StorePort storePort) {
        this.storePort = storePort;
    }

    public ClassTrajectory compare(String className) {
        var name = requireName(className);
        var points = pointsOf(name);
        if (points.isEmpty()) {
            throw new EvolutionException(EvolutionException.Code.CLASS_NOT_FOUND,
                    "La clase " + name + " no aparece en ningún commit del rango");
        }
        return new ClassTrajectory(name, points);
    }

    // el nombre en blanco no coincide con ninguna clase: se valida antes de leer
    private static String requireName(String className) {
        if (className == null || className.isBlank()) {
            throw new EvolutionException(EvolutionException.Code.INVALID_INPUT,
                    "El nombre de la clase no puede estar vacío");
        }
        return className.trim();
    }

    private ArrayList<ClassTrajectory.Point> pointsOf(String name) {
        var points = new ArrayList<ClassTrajectory.Point>();
        for (var state : storePort.loadStates()) {
            for (var file : state.files()) {
                for (var classChange : file.classes()) {
                    if (classChange.name().equals(name)) {
                        points.add(new ClassTrajectory.Point(state.delta().current(),
                                classChange));
                    }
                }
            }
        }
        return points;
    }
}
