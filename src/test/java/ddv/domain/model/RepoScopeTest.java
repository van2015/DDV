package ddv.domain.model;

import ddv.domain.exception.ScopeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoScopeTest {

    @Test
    @DisplayName("between() creates the scope with a trimmed A..B range")
    void betweenCreatesRangeWithTrimmedCommits() {
        var scope = RepoScope.between("/repo/x", " abc123 ", "def456");

        assertThat(scope.location()).isEqualTo("/repo/x");
        assertThat(scope.range()).isInstanceOf(RepoScope.Range.Between.class);
        var between = (RepoScope.Range.Between) scope.range();
        assertThat(between.from()).isEqualTo("abc123");
        assertThat(between.to()).isEqualTo("def456");
    }

    @Test
    @DisplayName("lastN() creates the scope with a last-N-commits range")
    void lastNCreatesLastNRange() {
        var scope = RepoScope.lastN("/repo/x", 10);

        assertThat(scope.range()).isEqualTo(new RepoScope.Range.LastN(10));
    }

    @Test
    @DisplayName("lastN() with N <= 0 throws INVALID_ARGUMENT")
    void nonPositiveLastNThrowsInvalidArgument() {
        assertThatThrownBy(() -> RepoScope.lastN("/repo/x", 0))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Blank or null location throws INVALID_ARGUMENT")
    void blankLocationThrowsInvalidArgument() {
        assertThatThrownBy(() -> RepoScope.lastN("  ", 5))
                .isInstanceOf(ScopeException.class);
        assertThatThrownBy(() -> RepoScope.between(null, "a", "b"))
                .isInstanceOf(ScopeException.class);
    }

    @Test
    @DisplayName("A..B range with blank bounds throws INVALID_ARGUMENT")
    void rangeWithBlankBoundsThrowsInvalidArgument() {
        assertThatThrownBy(() -> RepoScope.between("/repo", "", "abc"))
                .isInstanceOf(ScopeException.class);
    }

    @Test
    @DisplayName("Null range throws INVALID_ARGUMENT")
    void nullRangeThrowsInvalidArgument() {
        assertThatThrownBy(() -> new RepoScope("/repo", null))
                .isInstanceOf(ScopeException.class);
    }

    @Test
    @DisplayName("isRemote() detects URLs and scp-style sources, not local paths")
    void detectsRemoteSource() {
        assertThat(RepoScope.between("https://github.com/x/y.git", "a", "b").isRemote()).isTrue();
        assertThat(RepoScope.between("git@github.com:x/y.git", "a", "b").isRemote()).isTrue();
        assertThat(RepoScope.lastN("/local/path", 3).isRemote()).isFalse();
        assertThat(RepoScope.lastN("./relative", 3).isRemote()).isFalse();
    }
}
