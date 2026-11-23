package ddv.domain.model;

import ddv.domain.exception.DiffException;

import java.util.List;
import java.util.Objects;

/**
 * Secuencia de $S_i$ — salida del UC3 y entrada de UC4.
 *
 * <p>Un cambio estructural por Δ del {@link DeltaSequence} de origen, en el
 * mismo orden: UC4 recorre {@link #changes()} y sabe que la i-ésima corresponde
 * a la i-ésima Δ sin código adhesivo. La alineación se valida contra la
 * procedencia de cada cambio.</p>
 *
 * @param source  secuencia de Δ de la que derivan los cambios (trazabilidad)
 * @param changes un $S_i$ por Δ, en el mismo orden
 */
public record StructuralSequence(DeltaSequence source, List<StructuralChange> changes) {

    public StructuralSequence {
        Objects.requireNonNull(source, "La secuencia de Δ de origen no puede ser nula");
        Objects.requireNonNull(changes, "La lista de cambios no puede ser nula");
        changes = List.copyOf(changes);
        var deltas = source.deltas();
        if (changes.size() != deltas.size()) {
            throw new DiffException(DiffException.Code.INVALID_ARGUMENT,
                    "Se esperaba un Sᵢ por Δ: " + deltas.size() + " Δ pero " + changes.size());
        }
        for (int i = 0; i < changes.size(); i++) {
            if (!changes.get(i).delta().equals(deltas.get(i))) {
                throw new DiffException(DiffException.Code.INVALID_ARGUMENT,
                        "El Sᵢ " + i + " no corresponde a su Δ");
            }
        }
    }

    public int size() {
        return changes.size();
    }
}
