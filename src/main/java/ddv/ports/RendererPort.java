package ddv.ports;

import ddv.domain.model.DiagramSpec;

/**
 * Contrato de render de diagramas: los adaptadores (p. ej. Mermaid) lo
 * implementan; el núcleo solo conoce esta interfaz y {@link DiagramSpec}.
 *
 * <p>Cada adaptador emite el documento completo en su notación — título con el
 * commit y código del diagrama — listo para escribir en disco.</p>
 */
public interface RendererPort {

    /**
     * Documento del diagrama listo para exportar. Un spec sin nodos ni aristas
     * produce un documento con placeholder en lugar de código (decisión 4 de
     * UC4: un punto por commit para UC5).
     */
    String render(DiagramSpec spec);
}
