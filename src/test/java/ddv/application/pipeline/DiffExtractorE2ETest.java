package ddv.application.pipeline;

import ddv.adapters.out.jgit.GitFixtures;
import ddv.adapters.out.jgit.JGitAdapter;
import ddv.application.scope.ScopeResolver;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static ddv.assertions.DdvAssertions.assertScopeThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * End-to-end: UC1 (ScopeResolver) + UC2 (DiffExtractor) con JGit real sobre
 * repositorios temporales — sin mocks en ningún punto del camino.
 */
class DiffExtractorE2ETest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-01-04T00:00:00Z");
    private static final Instant T5 = Instant.parse("2026-01-05T00:00:00Z");

    private final JGitAdapter git = new JGitAdapter();
    private final ScopeResolver scopeResolver = new ScopeResolver(git);
    private final DiffExtractor extractor =
            new DiffExtractor(git, SourceFileFilter.javaFiles());

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("The full chain resolves the range and extracts one delta per pair")
    void fullChainResolvesRangeAndExtractsDeltas() throws Exception {
        var repo = historyWithAddModifyAndDelete();

        var scope = scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10));
        var sequence = extractor.extract(scope);

        assertThat(scope.size()).isEqualTo(5);
        assertThat(sequence.size()).isEqualTo(4);
        assertThat(sequence.deltas().get(0).files())
                .extracting(CommitDelta.FileChange::path, CommitDelta.FileChange::type)
                .contains(tuple("src/A.java", CommitDelta.ChangeType.ADDED));
        assertThat(sequence.deltas().get(1).files())
                .extracting(CommitDelta.FileChange::path, CommitDelta.FileChange::type)
                .contains(tuple("src/B.java", CommitDelta.ChangeType.ADDED));
        assertThat(sequence.deltas().get(2).files())
                .extracting(CommitDelta.FileChange::path, CommitDelta.FileChange::type)
                .contains(tuple("src/A.java", CommitDelta.ChangeType.MODIFIED));
        assertThat(sequence.deltas().get(3).files())
                .extracting(CommitDelta.FileChange::path, CommitDelta.FileChange::type)
                .contains(
                        tuple("src/B.java", CommitDelta.ChangeType.DELETED),
                        tuple("README.md", CommitDelta.ChangeType.ADDED));
    }

    @Test
    @DisplayName("Relevant changes across the range are only the Java files, in order")
    void relevantChangesAcrossRangeAreOnlyJavaFiles() throws Exception {
        var repo = historyWithAddModifyAndDelete();

        var scope = scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10));
        var sequence = extractor.extract(scope);

        assertThat(extractor.relevantChanges(sequence))
                .extracting(CommitDelta.FileChange::path)
                .containsExactly("src/A.java", "src/B.java", "src/A.java", "src/B.java");
    }

    @Test
    @DisplayName("A missing repository fails in UC1 before any delta is extracted")
    void missingRepositoryFailsInUc1() {
        var scope = RepoScope.lastN("/definitely/missing/repo", 5);

        assertScopeThrows(() -> extractor.extract(scopeResolver.resolve(scope)),
                ScopeException.Code.REPO_NOT_FOUND);
    }

    /** c1 (log) → c2 (+A) → c3 (+B) → c4 (mod A) → c5 (−B, +README). */
    private Path historyWithAddModifyAndDelete() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A {}");
        GitFixtures.commit(repo, "c2", T2);
        GitFixtures.write(repo, "src/B.java", "class B {}");
        GitFixtures.commit(repo, "c3", T3);
        GitFixtures.write(repo, "src/A.java", "class A { int x; }");
        GitFixtures.commit(repo, "c4", T4);
        GitFixtures.remove(repo, "src/B.java");
        GitFixtures.write(repo, "README.md", "# demo");
        GitFixtures.commit(repo, "c5", T5);
        return repo;
    }
}
