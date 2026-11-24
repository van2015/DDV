package ddv.ports;

import ddv.domain.model.ClassTrajectory;
import ddv.domain.model.DiagramSpec;

/**
 * Contrato de render de diagramas: los adaptadores (p. ej. Mermaid) lo
 * implementan; el núcleo solo conoce esta interfaz y los tipos de dominio.
 *
 * <p>Cada adaptador emite el documento completo en su notación — título y
 * código del diagrama — listo para escribir en disco.</p>
 */
public interface RendererPort {

    /**
     * Documento del diagrama de clases listo para exportar. Un spec sin nodos
     * ni aristas produce un documento con placeholder en lugar de código
     * (decisión 4 de UC4: un punto por commit para UC5).
     */
    String render(DiagramSpec spec);

    /**
     * Documento de la trayectoria de una clase (UC5): estado de la clase en
     * cada punto temporal relevante del rango.
     */
    String renderTrajectory(ClassTrajectory trajectory);
}
