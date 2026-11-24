package ddv.application.pipeline;

import ddv.domain.model.DiagramSequence;
import ddv.domain.model.DiagramSpec;
import ddv.domain.model.StructuralSequence;

/**
 * Etapa «DiagramGenerator» del pipeline — caso de uso UC4: generar el diagrama
 * UML por commit.
 *
 * <p>Convierte cada $S_i$ del {@link StructuralSequence} en su
 * {@link DiagramSpec} neutro de lenguaje; el render y la exportación viven en
 * las etapas siguientes. Conoce solo a {@code domain}.</p>
 */
public class DiagramGenerator {

    public DiagramSequence generate(StructuralSequence sequence) {
        var diagrams = sequence.changes().stream().map(DiagramSpec::from).toList();
        return new DiagramSequence(sequence, diagrams);
    }
}
