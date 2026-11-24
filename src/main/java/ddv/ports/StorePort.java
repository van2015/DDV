package ddv.ports;

import ddv.domain.model.StructuralChange;

import java.nio.file.Path;
import java.util.List;

/**
 * Contrato de persistencia: los adaptadores (p. ej. filesystem) lo implementan;
 * el núcleo solo conoce esta interfaz.
 *
 * <p>Nace en UC4 para la exportación de diagramas y UC5 lo amplía con los
 * estados $S_1..S_n$: el pipeline los escribe y el comparador de evolución los
 * lee.</p>
 */
public interface StorePort {

    /**
     * Escribe el documento de un diagrama y devuelve la ruta resultante.
     *
     * @param commitId identificador del commit al que pertenece — nombra el archivo
     * @param document contenido a escribir (Markdown con el diagrama)
     */
    Path saveDiagram(String commitId, String document);

    /** Escribe los estados $S_1..S_n$ de todo el rango para que UC5 los lea. */
    void saveStates(List<StructuralChange> states);

    /** Lee los estados $S_1..S_n$ escritos por el pipeline; falla si no existen. */
    List<StructuralChange> loadStates();
}
