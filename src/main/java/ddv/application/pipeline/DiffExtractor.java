package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.ports.GitSourcePort;

import java.util.ArrayList;
import java.util.List;

/**
 * Etapa «DiffExtractor» del pipeline — caso de uso UC2: ejecutar la detección
 * de diferencias sobre el alcance resuelto por UC1.
 *
 * <p>Recorre {@link AnalysisScope#consecutivePairs()} pidiendo la Δ de cada par
 * al puerto de Git y ensambla la {@link DeltaSequence} alineada. Conoce solo a
 * {@code domain} y {@code ports}: la infraestructura vive en el adaptador.</p>
 */
public class DiffExtractor {

    private final GitSourcePort gitSourcePort;
    private final SourceFileFilter sourceFileFilter;

    public DiffExtractor(GitSourcePort gitSourcePort, SourceFileFilter sourceFileFilter) {
        this.gitSourcePort = gitSourcePort;
        this.sourceFileFilter = sourceFileFilter;
    }

    public DeltaSequence extract(AnalysisScope scope) {
        try (var handle = gitSourcePort.open(scope.source())) {
            var deltas = new ArrayList<CommitDelta>();
            for (var pair : scope.consecutivePairs()) {
                deltas.add(gitSourcePort.diff(handle, pair.previous(), pair.current()));
            }
            return new DeltaSequence(scope, deltas);
        }
    }

    /** Cambios de todo el rango que UC3 puede parsear, en orden de aparición. */
    public List<CommitDelta.FileChange> relevantChanges(DeltaSequence sequence) {
        var changes = new ArrayList<CommitDelta.FileChange>();
        for (var delta : sequence.deltas()) {
            changes.addAll(sourceFileFilter.filter(delta.files()));
        }
        return List.copyOf(changes);
    }
}
