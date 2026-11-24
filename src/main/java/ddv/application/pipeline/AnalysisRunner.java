package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.DiagramSequence;
import ddv.domain.model.StructuralSequence;

import java.nio.file.Path;
import java.util.List;

/**
 * Orquesta el pipeline del análisis — UC2 → UC3 → UC4 — sobre un alcance ya
 * resuelto por UC1 y devuelve las rutas de los diagramas exportados.
 *
 * <p>Es el conductor del bucle por commit que antes solo ejecutaban los tests
 * E2E. Conoce solo a {@code domain} y a las etapas del propio pipeline.</p>
 */
public class AnalysisRunner {

    private final DiffExtractor diffExtractor;
    private final StructuralAnalyzer structuralAnalyzer;
    private final DiagramGenerator diagramGenerator;
    private final DiagramExporter diagramExporter;

    public AnalysisRunner(DiffExtractor diffExtractor, StructuralAnalyzer structuralAnalyzer,
            DiagramGenerator diagramGenerator, DiagramExporter diagramExporter) {
        this.diffExtractor = diffExtractor;
        this.structuralAnalyzer = structuralAnalyzer;
        this.diagramGenerator = diagramGenerator;
        this.diagramExporter = diagramExporter;
    }

    public List<Path> run(AnalysisScope scope) {
        DeltaSequence deltas = diffExtractor.extract(scope);
        StructuralSequence structural = structuralAnalyzer.analyze(deltas);
        DiagramSequence diagrams = diagramGenerator.generate(structural);
        return diagramExporter.export(diagrams);
    }
}
