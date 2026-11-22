package ddv.application.uc1;

import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;
import ddv.ports.RepositoryHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopeResolverTest {

    private final RepoScope scope = RepoScope.lastN("/repo", 5);
    private final GitSourcePort gitSourcePort = mock(GitSourcePort.class);
    private final RepositoryHandle handle = mock(RepositoryHandle.class);
    private final ScopeResolver resolver = new ScopeResolver(gitSourcePort);

    @Test
    @DisplayName("Opens the repository, resolves the range and closes the handle in order")
    void resolvesRangeAndClosesHandle() {
        var expected = new AnalysisScope(scope, List.of(Commit.of("c1"), Commit.of("c2")));
        when(gitSourcePort.open(scope)).thenReturn(handle);
        when(gitSourcePort.resolveRange(handle, scope)).thenReturn(expected);

        var result = resolver.resolve(scope);

        assertThat(result).isSameAs(expected);
        var order = inOrder(gitSourcePort, handle);
        order.verify(gitSourcePort).open(scope);
        order.verify(gitSourcePort).resolveRange(handle, scope);
        order.verify(handle).close();
    }

    @Test
    @DisplayName("Closes the handle when range resolution fails and propagates the error")
    void closesHandleWhenResolveRangeFails() {
        when(gitSourcePort.open(scope)).thenReturn(handle);
        when(gitSourcePort.resolveRange(handle, scope))
                .thenThrow(new ScopeException(ScopeException.Code.EMPTY_RANGE, "empty range"));

        assertThatThrownBy(() -> resolver.resolve(scope))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.EMPTY_RANGE);

        verify(handle).close();
    }

    @Test
    @DisplayName("Propagates open failures without trying to resolve the range")
    void propagatesOpenFailure() {
        when(gitSourcePort.open(scope))
                .thenThrow(new ScopeException(ScopeException.Code.REPO_NOT_FOUND, "missing repo"));

        assertThatThrownBy(() -> resolver.resolve(scope))
                .isInstanceOf(ScopeException.class);

        verify(gitSourcePort, never()).resolveRange(handle, scope);
    }
}
