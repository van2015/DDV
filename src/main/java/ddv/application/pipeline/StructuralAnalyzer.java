package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.CodeStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.FileStructureChange;
import ddv.domain.model.StructuralSequence;
import ddv.ports.GitSourcePort;
import ddv.ports.ParserPort;
import ddv.ports.RepositoryHandle;

import java.util.ArrayList;
import java.util.List;

/**
 * Etapa «StructuralAnalyzer» del pipeline — caso de uso UC3: analizar la
 * estructura cambiada por commit.
 *
 * <p>Para cada Δ del rango abre el repositorio, lee la versión vieja y la nueva
 * de cada archivo fuente, las parsea y <em>resta</em> ambas estructuras para
 * producir $S_i$. Conoce solo a {@code domain} y {@code ports}.</p>
 */
public class StructuralAnalyzer {

    private static final CodeStructure EMPTY_STRUCTURE = new CodeStructure(List.of(), List.of());

    private final GitSourcePort gitSourcePort;
    private final ParserPort parserPort;
    private final SourceFileFilter sourceFileFilter;

    public StructuralAnalyzer(GitSourcePort gitSourcePort, ParserPort parserPort,
            SourceFileFilter sourceFileFilter) {
        this.gitSourcePort = gitSourcePort;
        this.parserPort = parserPort;
        this.sourceFileFilter = sourceFileFilter;
    }

    public StructuralSequence analyze(DeltaSequence sequence) {
        try (var handle = gitSourcePort.open(sequence.source().source())) {
            var changes = new ArrayList<StructuralChange>();
            for (var delta : sequence.deltas()) {
                changes.add(analyzeDelta(handle, delta));
            }
            return new StructuralSequence(sequence, changes);
        }
    }

    private StructuralChange analyzeDelta(RepositoryHandle handle, CommitDelta delta) {
        var files = new ArrayList<FileStructureChange>();
        for (var change : delta.files()) {
            if (sourceFileFilter.isRelevant(change)) {
                files.add(analyzeFile(handle, delta, change));
            }
        }
        return new StructuralChange(delta, files);
    }

    private FileStructureChange analyzeFile(RepositoryHandle handle, CommitDelta delta,
            CommitDelta.FileChange change) {
        var before = structureAt(handle, delta.previous(), change.path());
        var after = structureAt(handle, delta.current(), change.path());
        return FileStructureChange.between(change.path(), before, after);
    }

    private CodeStructure structureAt(RepositoryHandle handle, Commit commit, String path) {
        return gitSourcePort.content(handle, commit, path)
                .map(source -> parserPort.parse(path, source))
                .orElse(EMPTY_STRUCTURE);
    }
}
