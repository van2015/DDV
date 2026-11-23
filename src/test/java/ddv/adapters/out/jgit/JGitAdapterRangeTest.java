package ddv.adapters.out.jgit;

import ddv.domain.exception.ScopeException;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.Instant;
import java.nio.file.Path;

import static ddv.assertions.DdvAssertions.assertScopeThrows;
import static org.assertj.core.api.Assertions.assertThat;

class JGitAdapterRangeTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");

    private final JGitAdapter adapter = new JGitAdapter();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("lastN returns the most recent commits in ascending chronological order")
    void lastNReturnsAscendingChronologicalOrder() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        var c2 = GitFixtures.commit(repo, "c2", T2);
        var c3 = GitFixtures.commit(repo, "c3", T3);
        var scope = RepoScope.lastN(repo.toString(), 10);

        try (var handle = adapter.open(scope)) {
            var resolved = adapter.resolveRange(handle, scope);

            assertThat(resolved.size()).isEqualTo(3);
            assertThat(resolved.first().id()).isEqualTo(c1);
            assertThat(resolved.last().id()).isEqualTo(c3);
            assertThat(resolved.commits().get(1).id()).isEqualTo(c2);
        }
    }

    @Test
    @DisplayName("lastN caps to the N most recent commits")
    void lastNCapsToMostRecentCommits() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        var c2 = GitFixtures.commit(repo, "c2", T2);
        var c3 = GitFixtures.commit(repo, "c3", T3);
        var scope = RepoScope.lastN(repo.toString(), 2);

        try (var handle = adapter.open(scope)) {
            var resolved = adapter.resolveRange(handle, scope);

            assertThat(resolved.size()).isEqualTo(2);
            assertThat(resolved.first().id()).isEqualTo(c2);
            assertThat(resolved.last().id()).isEqualTo(c3);
        }
    }

    @Test
    @DisplayName("between resolves an inclusive A..B range")
    void betweenResolvesInclusiveRange() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        var c2 = GitFixtures.commit(repo, "c2", T2);
        var c3 = GitFixtures.commit(repo, "c3", T3);

        var scope = RepoScope.between(repo.toString(), c1, c3);
        try (var handle = adapter.open(scope)) {
            var resolved = adapter.resolveRange(handle, scope);

            assertThat(resolved.size()).isEqualTo(3);
            assertThat(resolved.first().id()).isEqualTo(c1);
            assertThat(resolved.last().id()).isEqualTo(c3);
        }

        var singleScope = RepoScope.between(repo.toString(), c2, c2);
        try (var handle = adapter.open(singleScope)) {
            var resolved = adapter.resolveRange(handle, singleScope);

            assertThat(resolved.size()).isEqualTo(1);
            assertThat(resolved.first().id()).isEqualTo(c2);
        }
    }

    @Test
    @DisplayName("between with reversed bounds throws NOT_ANCESTOR")
    void betweenWithNonAncestorThrowsNotAncestor() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        var c3 = GitFixtures.commit(repo, "c3", T3);
        var scope = RepoScope.between(repo.toString(), c3, c1);

        try (var handle = adapter.open(scope)) {
            assertScopeThrows(() -> adapter.resolveRange(handle, scope),
                    ScopeException.Code.NOT_ANCESTOR);
        }
    }

    @Test
    @DisplayName("Unknown commit id throws COMMIT_NOT_FOUND")
    void unknownCommitThrowsCommitNotFound() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        var scope = RepoScope.between(repo.toString(),
                "0000000000000000000000000000000000000001",
                "0000000000000000000000000000000000000002");

        try (var handle = adapter.open(scope)) {
            assertScopeThrows(() -> adapter.resolveRange(handle, scope),
                    ScopeException.Code.COMMIT_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("resolveRange with a foreign handle throws INVALID_ARGUMENT")
    void foreignHandleThrowsInvalidArgument() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        var scope = RepoScope.lastN(repo.toString(), 5);
        ddv.ports.RepositoryHandle foreign = () -> { };

        assertScopeThrows(() -> adapter.resolveRange(foreign, scope),
                ScopeException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Empty repository throws EMPTY_RANGE")
    void emptyRepositoryThrowsEmptyRange() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "empty");
        var scope = RepoScope.lastN(repo.toString(), 5);

        try (var handle = adapter.open(scope)) {
            assertScopeThrows(() -> adapter.resolveRange(handle, scope),
                    ScopeException.Code.EMPTY_RANGE);
        }
    }
}
