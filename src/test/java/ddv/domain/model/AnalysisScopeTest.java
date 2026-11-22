package ddv.domain.model;

import ddv.domain.exception.ScopeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisScopeTest {

    private static final RepoScope SOURCE = RepoScope.lastN("/repo", 3);

    private static Commit commit(String sha) {
        return Commit.of(sha);
    }

    @Test
    @DisplayName("Without commits throws EMPTY_RANGE")
    void emptyCommitsThrowsEmptyRange() {
        assertThatThrownBy(() -> new AnalysisScope(SOURCE, List.of()))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.EMPTY_RANGE);
    }

    @Test
    @DisplayName("Null source throws NullPointerException")
    void nullSourceThrowsNullPointerException() {
        assertThatThrownBy(() -> new AnalysisScope(null, List.of(commit("a1"))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The commit list is immutable even when built from a mutable one")
    void commitListIsImmutable() {
        var external = new ArrayList<>(List.of(commit("a1"), commit("b2")));
        var scope = new AnalysisScope(SOURCE, external);

        external.clear(); // mutating the external source must not leak into the domain
        assertThat(scope.size()).isEqualTo(2);

        assertThatThrownBy(() -> scope.commits().add(commit("c3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("first()/last() return the range bounds")
    void firstAndLastReturnRangeBounds() {
        var scope = new AnalysisScope(SOURCE,
                List.of(commit("aaa1111"), commit("bbb2222"), commit("ccc3333")));

        assertThat(scope.first().id()).isEqualTo("aaa1111");
        assertThat(scope.last().id()).isEqualTo("ccc3333");
        assertThat(scope.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("consecutivePairs() yields n-1 ordered pairs (Cᵢ₋₁, Cᵢ)")
    void consecutivePairsYieldsTransitionsForPipeline() {
        var scope = new AnalysisScope(SOURCE,
                List.of(commit("c1"), commit("c2"), commit("c3"), commit("c4")));

        var pairs = scope.consecutivePairs();

        assertThat(pairs).hasSize(3);
        assertThat(pairs.get(0).previous().id()).isEqualTo("c1");
        assertThat(pairs.get(0).current().id()).isEqualTo("c2");
        assertThat(pairs.get(2).previous().id()).isEqualTo("c3");
        assertThat(pairs.get(2).current().id()).isEqualTo("c4");
    }

    @Test
    @DisplayName("Pairs reference the very same Commit instances of the range")
    void pairsReferenceTheSameCommitInstances() {
        var commits = List.of(commit("c1"), commit("c2"), commit("c3"));
        var scope = new AnalysisScope(SOURCE, commits);

        var pairs = scope.consecutivePairs();

        assertThat(pairs.get(0).previous()).isSameAs(commits.get(0));
        assertThat(pairs.get(0).current()).isSameAs(commits.get(1));
        assertThat(pairs.get(1).previous()).isSameAs(commits.get(1));
        assertThat(pairs.get(1).current()).isSameAs(commits.get(2));
    }

    @Test
    @DisplayName("The pairs list is immutable")
    void pairsListIsImmutable() {
        var scope = new AnalysisScope(SOURCE, List.of(commit("c1"), commit("c2")));

        assertThatThrownBy(() -> scope.consecutivePairs().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("The source scope is preserved for traceability")
    void sourceIsPreservedForTraceability() {
        var scope = new AnalysisScope(SOURCE, List.of(commit("c1")));

        assertThat(scope.source()).isEqualTo(SOURCE);
        assertThat(scope.source().range()).isEqualTo(new RepoScope.Range.LastN(3));
    }

    @Test
    @DisplayName("Scopes are compared by value so UC5 can deduplicate them")
    void scopesAreComparedByValue() {
        var first = new AnalysisScope(SOURCE, List.of(commit("c1"), commit("c2")));
        var second = new AnalysisScope(SOURCE, List.of(commit("c1"), commit("c2")));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("A single commit yields no pairs")
    void singleCommitYieldsNoPairs() {
        var scope = new AnalysisScope(SOURCE, List.of(commit("c1")));

        assertThat(scope.consecutivePairs()).isEmpty();
    }

    @Test
    @DisplayName("Commit.of() derives an 8-char shortId with empty defaults")
    void commitDerivesShortId() {
        var c = Commit.of("1234567890abcdef");

        assertThat(c.shortId()).isEqualTo("12345678");
        assertThat(c.message()).isEmpty();
        assertThat(c.authoredAt()).isNull();
    }

    @Test
    @DisplayName("Commit with blank id throws INVALID_ARGUMENT")
    void blankCommitIdThrowsInvalidArgument() {
        assertThatThrownBy(() -> Commit.of(" "))
                .isInstanceOf(ScopeException.class);
    }
}
