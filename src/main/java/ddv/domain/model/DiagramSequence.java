package ddv.domain.model;

import ddv.domain.exception.DiagramException;

import java.util.List;
import java.util.Objects;

/**
 * Secuencia de diagramas — salida del UC4 y entrada de UC5.
 *
 * <p>Un {@link DiagramSpec} por $S_i$ de origen, en el mismo orden: UC5 recorre
 * {@link #diagrams()} y sabe que la i-ésima corresponde a la i-ésima Δ sin
 * código adhesivo. La alineación se valida contra los commits de cada $S_i$.</p>
 *
 * @param source   secuencia de $S_i$ de la que derivan los diagramas (trazabilidad)
 * @param diagrams un diagrama por $S_i$, en el mismo orden
 */
public record DiagramSequence(StructuralSequence source, List<DiagramSpec> diagrams) {

    public DiagramSequence {
        Objects.requireNonNull(source, "La secuencia de Sᵢ de origen no puede ser nula");
        Objects.requireNonNull(diagrams, "La lista de diagramas no puede ser nula");
        diagrams = List.copyOf(diagrams);
        if (diagrams.size() != source.size()) {
            throw new DiagramException(DiagramException.Code.INVALID_INPUT,
                    "Se esperaba un diagrama por Sᵢ: " + source.size() + " Sᵢ pero "
                            + diagrams.size());
        }
        for (int i = 0; i < diagrams.size(); i++) {
            var delta = source.changes().get(i).delta();
            var diagram = diagrams.get(i);
            if (!diagram.previous().equals(delta.previous())
                    || !diagram.current().equals(delta.current())) {
                throw new DiagramException(DiagramException.Code.INVALID_INPUT,
                        "El diagrama " + i + " no corresponde a su Sᵢ");
            }
        }
    }

    public int size() {
        return diagrams.size();
    }
}
