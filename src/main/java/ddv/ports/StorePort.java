package ddv.ports;

import java.nio.file.Path;

/**
 * Contrato de persistencia: los adaptadores (p. ej. filesystem) lo implementan;
 * el núcleo solo conoce esta interfaz.
 *
 * <p>Nace en UC4 para la exportación de diagramas (decisión 3) y UC5 lo amplía
 * con la persistencia de los estados $S_1..S_n$.</p>
 */
public interface StorePort {

    /**
     * Escribe el documento de un diagrama y devuelve la ruta resultante.
     *
     * @param commitId identificador del commit al que pertenece — nombra el archivo
     * @param document contenido a escribir (Markdown con el diagrama)
     */
    Path saveDiagram(String commitId, String document);
}
