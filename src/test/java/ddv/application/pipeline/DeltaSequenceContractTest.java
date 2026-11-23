package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;
import ddv.ports.RepositoryHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the UC2 → UC3 seam: the future {@code StructuralAnalyzer} must be able
 * to consume (pair, delta) tuples straight from {@link DiffExtractor} output
 * with no glue code, and to select the analyzable files of the whole range.
 *
 * <p>If this test breaks, UC3 has to be revisited together with UC2.</p>
 */
class DeltaSequenceContractTest {

    private final DiffExtractor extractor =
            new DiffExtractor(new PairDeltasPort(), SourceFileFilter.javaFiles());
    private final RecordingAnalyzer analyzer = new RecordingAnalyzer();

    @Test
    @DisplayName("The analyzer receives each pair aligned with its delta, in order")
    void analyzerReceivesAlignedPairsAndDeltas() {
        var scope = scopeOf("c1", "c2", "c3", "c4");

        var sequence = extractor.extract(scope);
        var pairs = scope.consecutivePairs();
        for (int i = 0; i < pairs.size(); i++) {
            analyzer.consume(pairs.get(i), sequence.deltas().get(i));
        }

        assertThat(analyzer.consumed).hasSize(3);
        assertThat(analyzer.consumed).allSatisfy(entry -> {
            assertThat(entry.delta.previous().id()).isEqualTo(entry.pair.previous().id());
            assertThat(entry.delta.current().id()).isEqualTo(entry.pair.current().id());
        });
        assertThat(analyzer.consumed)
                .extracting(entry -> entry.pair.current().id())
                .containsExactly("c2", "c3", "c4");
    }

    @Test
    @DisplayName("relevantChanges exposes only the analyzable files of the whole range")
    void relevantChangesExposesOnlyAnalyzableFiles() {
        var scope = scopeOf("c1", "c2", "c3", "c4");

        var sequence = extractor.extract(scope);

        assertThat(extractor.relevantChanges(sequence))
                .extracting(CommitDelta.FileChange::path)
                .containsExactly("src/c2.java", "src/c3.java", "src/c4.java");
    }

    @Test
    @DisplayName("A one-commit scope yields no pairs, deltas or relevant files")
    void singleCommitScopeYieldsNothing() {
        var scope = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(Commit.of("c1")));

        var sequence = extractor.extract(scope);

        assertThat(sequence.deltas()).isEmpty();
        assertThat(extractor.relevantChanges(sequence)).isEmpty();
    }

    private static AnalysisScope scopeOf(String... ids) {
        var commits = java.util.Arrays.stream(ids).map(Commit::of).toList();
        return new AnalysisScope(RepoScope.lastN("/repo", commits.size()), commits);
    }

    private record Consumed(AnalysisScope.ConsecutivePair pair, CommitDelta delta) {
    }

    private static final class RecordingAnalyzer {
        private final List<Consumed> consumed = new ArrayList<>();

        void consume(AnalysisScope.ConsecutivePair pair, CommitDelta delta) {
            consumed.add(new Consumed(pair, delta));
        }
    }

    /** Puerto falso: cada Δ contiene un fichero Java y uno no analizable. */
    private static final class PairDeltasPort implements GitSourcePort {

        @Override
        public RepositoryHandle open(RepoScope scope) {
            return () -> { };
        }

        @Override
        public AnalysisScope resolveRange(RepositoryHandle repository, RepoScope scope) {
            throw new UnsupportedOperationException("fuera del alcance de este contrato");
        }

        @Override
        public CommitDelta diff(RepositoryHandle repository, Commit previous, Commit current) {
            return new CommitDelta(previous, current, List.of(
                    new CommitDelta.FileChange("src/" + current.shortId() + ".java",
                            CommitDelta.ChangeType.MODIFIED),
                    new CommitDelta.FileChange("README.md", CommitDelta.ChangeType.MODIFIED)));
        }
    }
}
