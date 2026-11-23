package ddv.ports;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the UC1 → UC2 seam: the pipeline loop head must be able to drive
 * {@link GitSourcePort#diff} straight from {@link AnalysisScope#consecutivePairs()}
 * with no glue code. If this test breaks, UC2–UC4 need to be revisited.
 */
class GitSourcePortContractTest {

    private final RecordingGitSource gitSource = new RecordingGitSource();

    @Test
    @DisplayName("Each consecutive pair of the scope produces exactly one diff, in order")
    void eachConsecutivePairProducesOneDiffInOrder() {
        var scope = new AnalysisScope(RepoScope.lastN("/repo", 4),
                List.of(Commit.of("c1"), Commit.of("c2"), Commit.of("c3"), Commit.of("c4")));

        var deltas = runLoopHead(scope);

        assertThat(deltas).hasSize(3);
        assertThat(deltas).extracting(CommitDelta::previous).extracting(Commit::id)
                .containsExactly("c1", "c2", "c3");
        assertThat(deltas).extracting(CommitDelta::current).extracting(Commit::id)
                .containsExactly("c2", "c3", "c4");
    }

    @Test
    @DisplayName("A single-commit scope opens the repository but requests no diff")
    void singleCommitScopeRequestsNoDiff() {
        var scope = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(Commit.of("c1")));

        var deltas = runLoopHead(scope);

        assertThat(deltas).isEmpty();
        assertThat(gitSource.repositoryClosed).isTrue();
    }

    /** Reproduce la cabecera del bucle UC2: open → para cada par → diff → close. */
    private List<CommitDelta> runLoopHead(AnalysisScope scope) {
        try (var handle = gitSource.open(scope.source())) {
            for (var pair : scope.consecutivePairs()) {
                gitSource.diff(handle, pair.previous(), pair.current());
            }
        }
        return List.copyOf(gitSource.diffs);
    }

    private static final class RecordingGitSource implements GitSourcePort {

        private final List<CommitDelta> diffs = new ArrayList<>();
        private boolean repositoryClosed;

        @Override
        public RepositoryHandle open(RepoScope scope) {
            return () -> repositoryClosed = true;
        }

        @Override
        public AnalysisScope resolveRange(RepositoryHandle repository, RepoScope scope) {
            throw new UnsupportedOperationException("not part of this contract");
        }

        @Override
        public CommitDelta diff(RepositoryHandle repository, Commit previous, Commit current) {
            var delta = new CommitDelta(previous, current, List.of());
            diffs.add(delta);
            return delta;
        }

        @Override
        public Optional<String> content(RepositoryHandle repository, Commit commit, String path) {
            throw new UnsupportedOperationException("not part of this contract");
        }
    }
}
