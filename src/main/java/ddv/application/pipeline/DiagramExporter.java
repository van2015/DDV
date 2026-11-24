package ddv.application.pipeline;

import ddv.domain.model.DiagramSequence;
import ddv.ports.RendererPort;
import ddv.ports.StorePort;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Etapa de exportación del caso de uso UC4: renderiza cada diagrama de la
 * secuencia y lo escribe en disco.
 *
 * <p>Conoce solo a {@code domain} y a los puertos; la notación la decide
 * {@link RendererPort} y la ubicación {@link StorePort}.</p>
 */
public class DiagramExporter {

    private final RendererPort rendererPort;
    private final StorePort storePort;

    public DiagramExporter(RendererPort rendererPort, StorePort storePort) {
        this.rendererPort = rendererPort;
        this.storePort = storePort;
    }

    /** Exporta toda la secuencia en orden y devuelve las rutas escritas. */
    public List<Path> export(DiagramSequence sequence) {
        var paths = new ArrayList<Path>();
        for (var diagram : sequence.diagrams()) {
            var document = rendererPort.render(diagram);
            paths.add(storePort.saveDiagram(diagram.current().shortId(), document));
        }
        // al final: los estados solo se guardan si todos los diagramas salieron bien
        storePort.saveStates(sequence.source().changes());
        return List.copyOf(paths);
    }
}
