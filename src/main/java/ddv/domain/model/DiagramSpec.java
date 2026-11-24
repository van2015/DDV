package ddv.domain.model;

import ddv.domain.exception.DiagramException;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.StructuralChange.ChangeKind;

import java.util.List;
import java.util.Objects;

/**
 * Diagrama de clases de un commit — salida del UC4 y entrada del render.
 *
 * <p>Representación <em>neutra de lenguaje</em>: no conoce Mermaid ni PlantUML,
 * solo qué clases cambiaron en el commit {@code previous → current}, qué
 * miembros añadieron y qué dependencias nuevas surgieron (decisión 5 de UC4:
 * solo cambios significativos). Un spec sin nodos ni aristas representa un
 * commit sin cambios estructurales, que el render convierte en placeholder
 * (decisión 4).</p>
 *
 * @param previous commit anterior del par (etiqueta del diagrama)
 * @param current  commit actual del par (etiqueta del diagrama)
 * @param nodes    clases añadidas, modificadas o eliminadas en el commit
 * @param edges    dependencias nuevas, como pares (clase que las usa, tipo usado)
 */
public record DiagramSpec(Commit previous, Commit current, List<Node> nodes, List<Edge> edges) {

    public DiagramSpec {
        Objects.requireNonNull(previous, "El commit anterior no puede ser nulo");
        Objects.requireNonNull(current, "El commit actual no puede ser nulo");
        Objects.requireNonNull(nodes, "La lista de nodos no puede ser nula");
        Objects.requireNonNull(edges, "La lista de aristas no puede ser nula");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /**
     * @param name         nombre de la clase (calificado dentro de su archivo)
     * @param state        alta, baja o modificación de la clase en este commit
     * @param addedMembers miembros dados de alta (todos, si la clase es nueva)
     */
    public record Node(String name, ChangeKind state, List<MemberStructure> addedMembers) {

        public Node {
            requireNotBlank(name, "El nombre de la clase no puede estar vacío");
            Objects.requireNonNull(state, "El estado de la clase no puede ser nulo");
            Objects.requireNonNull(addedMembers, "Los miembros añadidos no pueden ser nulos");
            addedMembers = List.copyOf(addedMembers);
        }
    }

    /**
     * @param source clase que pasa a usar la dependencia
     * @param target tipo usado (por nombre, sin resolución de símbolos)
     */
    public record Edge(String source, String target) {

        public Edge {
            requireNotBlank(source, "El origen de la arista no puede estar vacío");
            requireNotBlank(target, "El destino de la arista no puede estar vacío");
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new DiagramException(DiagramException.Code.INVALID_INPUT, message);
        }
    }
}
