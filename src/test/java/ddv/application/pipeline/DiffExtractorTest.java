package ddv.application.pipeline;

import ddv.domain.exception.DiffException;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;
import ddv.ports.RepositoryHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiffExtractorTest {

    private final RepoScope source = RepoScope.lastN("/repo", 3);
    private final Commit c1 = Commit.of("c1");
    private final Commit c2 = Commit.of("c2");
    private final Commit c3 = Commit.of("c3");
    private final AnalysisScope scope = new AnalysisScope(source, List.of(c1, c2, c3));

    private final GitSourcePort gitSourcePort = mock(GitSourcePort.class);
    private final RepositoryHandle handle = mock(RepositoryHandle.class);
    private final DiffExtractor extractor =
            new DiffExtractor(gitSourcePort, SourceFileFilter.javaFiles());

    @Test
    @DisplayName("Opens the repository, diffs each consecutive pair and closes the handle in order")
    void diffsEveryPairAndClosesHandleInOrder() {
        when(gitSourcePort.open(source)).thenReturn(handle);
        when(gitSourcePort.diff(handle, c1, c2)).thenReturn(delta("c1", "c2", "A.java"));
        when(gitSourcePort.diff(handle, c2, c3)).thenReturn(delta("c2", "c3", "B.java"));

        var sequence = extractor.extract(scope);

        assertThat(sequence.source()).isEqualTo(scope);
        assertThat(sequence.deltas())
                .extracting(CommitDelta::current)
                .extracting(Commit::id)
                .containsExactly("c2", "c3");
        var order = inOrder(gitSourcePort, handle);
        order.verify(gitSourcePort).open(source);
        order.verify(gitSourcePort).diff(handle, c1, c2);
        order.verify(gitSourcePort).diff(handle, c2, c3);
        order.verify(handle).close();
    }

    @Test
    @DisplayName("Closes the handle when a diff fails and propagates the error")
    void closesHandleWhenDiffFails() {
        when(gitSourcePort.open(source)).thenReturn(handle);
        when(gitSourcePort.diff(handle, c1, c2))
                .thenThrow(new DiffException(DiffException.Code.IO_ERROR, "read failed"));

        assertThatThrownBy(() -> extractor.extract(scope))
                .isInstanceOf(DiffException.class)
                .extracting(e -> ((DiffException) e).code())
                .isEqualTo(DiffException.Code.IO_ERROR);

        verify(handle).close();
        verify(gitSourcePort, never()).diff(handle, c2, c3);
    }

    @Test
    @DisplayName("Propagates open failures without requesting any diff")
    void propagatesOpenFailure() {
        when(gitSourcePort.open(source))
                .thenThrow(new ScopeException(ScopeException.Code.REPO_NOT_FOUND, "missing repo"));

        assertThatThrownBy(() -> extractor.extract(scope))
                .isInstanceOf(ScopeException.class);

        verify(gitSourcePort, never()).diff(handle, c1, c2);
    }

    @Test
    @DisplayName("A single-commit scope opens the repository but requests no diff")
    void singleCommitScopeRequestsNoDiff() {
        var single = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(c1));
        when(gitSourcePort.open(single.source())).thenReturn(handle);

        var sequence = extractor.extract(single);

        assertThat(sequence.deltas()).isEmpty();
        var order = inOrder(gitSourcePort, handle);
        order.verify(gitSourcePort).open(single.source());
        order.verify(handle).close();
    }

    @Test
    @DisplayName("A delta that does not match its pair fails the sequence alignment")
    void misalignedDeltaFromPortFailsAlignment() {
        when(gitSourcePort.open(source)).thenReturn(handle);
        when(gitSourcePort.diff(handle, c1, c2)).thenReturn(delta("other1", "other2", "A.java"));
        when(gitSourcePort.diff(handle, c2, c3)).thenReturn(delta("c2", "c3", "B.java"));

        assertThatThrownBy(() -> extractor.extract(scope))
                .isInstanceOf(DiffException.class)
                .extracting(e -> ((DiffException) e).code())
                .isEqualTo(DiffException.Code.INVALID_ARGUMENT);

        verify(handle).close();
    }

    @Test
    @DisplayName("relevantChanges keeps only analyzable files in order")
    void relevantChangesKeepsOnlyAnalyzableFiles() {
        var sequence = new DeltaSequence(scope, List.of(
                delta("c1", "c2", "src/A.java", "README.md"),
                delta("c2", "c3", "docs/notes.txt", "src/B.java")));

        var changes = extractor.relevantChanges(sequence);

        assertThat(changes)
                .extracting(CommitDelta.FileChange::path)
                .containsExactly("src/A.java", "src/B.java");
    }

    @Test
    @DisplayName("relevantChanges of a single-commit sequence is empty")
    void relevantChangesOfEmptySequenceIsEmpty() {
        var single = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(c1));
        var sequence = new DeltaSequence(single, List.of());

        assertThat(extractor.relevantChanges(sequence)).isEmpty();
    }

    private static CommitDelta delta(String previous, String current, String... paths) {
        var files = Arrays.stream(paths)
                .map(path -> new CommitDelta.FileChange(path, CommitDelta.ChangeType.MODIFIED))
                .toList();
        return new CommitDelta(Commit.of(previous), Commit.of(current), files);
    }
}
