package ddv.domain.model;

import ddv.domain.exception.DiffException;

import java.util.List;
import java.util.Objects;

/**
 * Secuencia de Δ $\Delta_1 .. \Delta_{n-1}$ — salida del UC2 y entrada de UC3.
 *
 * <p>Una Δ por par consecutivo del {@link AnalysisScope} de origen, en el mismo
 * orden: UC3 recorre {@link #deltas()} y sabe que la i-ésima corresponde al
 * i-ésimo par sin código adhesivo.</p>
 *
 * <p><b>Contrato estable:</b> inmutabilidad, alineación 1:1 con
 * {@link AnalysisScope#consecutivePairs()} y trazabilidad de {@code source}.
 * Bloqueado por {@code DeltaSequenceTest}.</p>
 *
 * @param source alcance del que derivan las Δ (trazabilidad)
 * @param deltas una Δ por par consecutivo, en orden $C_1..C_n$ (vacío si el
 *               rango tiene un solo commit)
 */
public record DeltaSequence(AnalysisScope source, List<CommitDelta> deltas) {

    public DeltaSequence {
        Objects.requireNonNull(source, "El alcance de origen no puede ser nulo");
        Objects.requireNonNull(deltas, "La lista de Δ no puede ser nula");
        deltas = List.copyOf(deltas);
        var pairs = source.consecutivePairs();
        if (deltas.size() != pairs.size()) {
            throw new DiffException(DiffException.Code.INVALID_ARGUMENT,
                    "Se esperaba una Δ por par consecutivo: " + pairs.size()
                            + " pares pero " + deltas.size() + " Δ");
        }
        for (int i = 0; i < deltas.size(); i++) {
            var pair = pairs.get(i);
            var delta = deltas.get(i);
            if (!pair.previous().id().equals(delta.previous().id())
                    || !pair.current().id().equals(delta.current().id())) {
                throw new DiffException(DiffException.Code.INVALID_ARGUMENT,
                        "La Δ " + i + " no corresponde al par "
                                + pair.previous().id() + " → " + pair.current().id());
            }
        }
    }

    public int size() {
        return deltas.size();
    }
}
