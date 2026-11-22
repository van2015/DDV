package ddv.adapters.in.cli;

import ddv.adapters.out.jgit.GitFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: CLI real + ensamblaje real (JGitAdapter + ScopeResolver) contra
 * repositorios Git temporales. Cubre el camino completo de UC1.
 */
class DdvCommandE2ETest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("analyze --last resolves a real repository end to end")
    void analyzeLastResolvesRealRepository() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.commit(repo, "c2", T2);
        GitFixtures.commit(repo, "c3", T3);

        var exit = execute("analyze", "--repo", repo.toString(), "--last", "2");

        assertThat(exit).isZero();
        assertThat(out.toString())
                .contains("Repositorio: " + repo)
                .contains("Rango: últimos 2")
                .contains("Commits: 2");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("analyze --range resolves an inclusive A..B range end to end")
    void analyzeRangeResolvesInclusiveRange() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        GitFixtures.commit(repo, "c2", T2);
        var c3 = GitFixtures.commit(repo, "c3", T3);

        var exit = execute("analyze", "--repo", repo.toString(),
                "--range", c1 + ".." + c3);

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("Rango: " + c1 + ".." + c3).contains("Commits: 3");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("analyze reports a missing repository with exit code 1")
    void analyzeReportsMissingRepository() {
        var exit = execute("analyze", "--repo", "/definitely/missing", "--last", "1");

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("Error [REPO_NOT_FOUND]");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    @DisplayName("ddv without subcommand prints usage")
    void rootWithoutSubcommandPrintsUsage() {
        var exit = execute();

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("Usage: ddv").contains("analyze");
    }

    private int execute(String... args) {
        return DdvCommand.execute(args, new PrintWriter(out, true), new PrintWriter(err, true));
    }
}
