package ddv.adapters.in.cli;

import ddv.application.scope.ScopeResolver;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyzeCommandTest {

    private final ScopeResolver resolver = mock(ScopeResolver.class);
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final CommandLine commandLine = new CommandLine(new AnalyzeCommand(resolver))
            .setOut(new PrintWriter(out, true))
            .setErr(new PrintWriter(err, true));

    @Test
    @DisplayName("Resolves last-N scope and prints the summary with exit code 0")
    void resolvesLastNAndPrintsSummary() {
        var scope = RepoScope.lastN("/repo", 2);
        when(resolver.resolve(scope)).thenReturn(resolved(scope, "aaaa1111", "bbbb2222"));

        var exit = commandLine.execute("--repo", "/repo", "--last", "2");

        assertThat(exit).isZero();
        assertThat(out.toString())
                .contains("Repositorio: /repo")
                .contains("Rango: últimos 2")
                .contains("Commits: 2 (aaaa1111..bbbb2222)");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    @DisplayName("Resolves A..B range and prints it in the summary")
    void resolvesBetweenRange() {
        var scope = RepoScope.between("/repo", "aaaa1111", "bbbb2222");
        when(resolver.resolve(scope)).thenReturn(resolved(scope, "aaaa1111", "bbbb2222"));

        var exit = commandLine.execute("--repo", "/repo", "--range", "aaaa1111..bbbb2222");

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("Rango: aaaa1111..bbbb2222");
    }

    @Test
    @DisplayName("Scope failures are printed to stderr with exit code 1")
    void scopeFailurePrintsErrorAndReturnsExitCodeOne() {
        when(resolver.resolve(any())).thenThrow(
                new ScopeException(ScopeException.Code.REPO_NOT_FOUND, "No existe: /nope"));

        var exit = commandLine.execute("--repo", "/nope", "--last", "3");

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("Error [REPO_NOT_FOUND]: No existe: /nope");
        assertThat(out.toString()).isEmpty();
    }

    @Test
    @DisplayName("Missing range option is a usage error and never reaches the use case")
    void missingRangeOptionIsUsageError() {
        var exit = commandLine.execute("--repo", "/repo");

        assertThat(exit).isEqualTo(2);
        verify(resolver, never()).resolve(any());
    }

    @Test
    @DisplayName("Range and last are mutually exclusive")
    void rangeAndLastAreMutuallyExclusive() {
        var exit = commandLine.execute("--repo", "/repo",
                "--range", "a..b", "--last", "3");

        assertThat(exit).isEqualTo(2);
        verify(resolver, never()).resolve(any());
    }

    @Test
    @DisplayName("Missing repository option is a usage error")
    void missingRepositoryOptionIsUsageError() {
        var exit = commandLine.execute("--last", "3");

        assertThat(exit).isEqualTo(2);
        verify(resolver, never()).resolve(any());
    }

    @Test
    @DisplayName("Malformed range without A..B separator is a usage error")
    void malformedRangeIsUsageError() {
        var exit = commandLine.execute("--repo", "/repo", "--range", "aaaa-bbbb");

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).contains("Formato de --range inválido");
        verify(resolver, never()).resolve(any());
    }

    @Test
    @DisplayName("Non-positive last N is rejected by the domain with exit code 1")
    void nonPositiveLastNIsRejectedByDomain() {
        var exit = commandLine.execute("--repo", "/repo", "--last", "0");

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("Error [INVALID_ARGUMENT]");
        verify(resolver, never()).resolve(any());
    }

    private static AnalysisScope resolved(RepoScope scope, String firstId, String lastId) {
        return new AnalysisScope(scope, List.of(Commit.of(firstId), Commit.of(lastId)));
    }
}
