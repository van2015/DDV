package ddv.domain.model;

import ddv.domain.exception.ScopeException;

import java.util.List;
import java.util.Objects;

/**
 * Rango resuelto de commits $C_1..C_n$ — contrato de salida del UC1 y
 * entrada del bucle UC2 → UC3 → UC4.
 *
 * <p>Lista inmutable y ordenada cronológicamente (del más antiguo al más
 * reciente), tal como la consume el pipeline.</p>
 *
 * @param source  alcance del que deriva el rango (trazabilidad)
 * @param commits commits del rango, orden $C_1..C_n$ (no vacío)
 */
public record AnalysisScope(RepoScope source, List<Commit> commits) {

    public AnalysisScope {
        Objects.requireNonNull(source, "El alcance de origen no puede ser nulo");
        if (commits == null || commits.isEmpty()) {
            throw new ScopeException(ScopeException.Code.EMPTY_RANGE,
                    "El rango de análisis no contiene commits");
        }
        commits = List.copyOf(commits); // inmutable, defensa contra copias externas
    }

    /** Número de commits del rango ($n$). */
    public int size() {
        return commits.size();
    }

    /** Primer commit del rango ($C_1$). */
    public Commit first() {
        return commits.get(0);
    }

    /** Último commit del rango ($C_n$). */
    public Commit last() {
        return commits.get(commits.size() - 1);
    }

    /**
     * Pares consecutivos $(C_{i-1}, C_i)$ para el UC2: son las transiciones
     * sobre las que se calcula cada diff Δᵢ. Devuelve $n-1$ pares.
     */
    public List<ConsecutivePair> consecutivePairs() {
        if (commits.size() < 2) {
            return List.of();
        }
        var pairs = new java.util.ArrayList<ConsecutivePair>(commits.size() - 1);
        for (int i = 1; i < commits.size(); i++) {
            pairs.add(new ConsecutivePair(commits.get(i - 1), commits.get(i)));
        }
        return List.copyOf(pairs);
    }

    /** Par $(C_{i-1}, C_i)$ sobre el que se calcula una Δ. */
    public record ConsecutivePair(Commit previous, Commit current) {
        public ConsecutivePair {
            Objects.requireNonNull(previous, "previous no puede ser nulo");
            Objects.requireNonNull(current, "current no puede ser nulo");
        }
    }
}
