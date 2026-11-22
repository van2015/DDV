package ddv.adapters.in.cli;

import ddv.application.scope.ScopeResolver;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.RepoScope;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.concurrent.Callable;

/**
 * Subcomando {@code ddv analyze} — adaptador de entrada de UC1.
 *
 * <p>Traduce los argumentos CLI a {@link RepoScope}, invoca al caso de uso y
 * formatea la salida o el error tipado {@link ScopeException}.</p>
 */
@Command(name = "analyze", mixinStandardHelpOptions = true,
        description = "Resuelve el alcance de análisis (repositorio + rango de commits).")
public class AnalyzeCommand implements Callable<Integer> {

    /** Códigos de salida de la CLI. */
    static final int EXIT_OK = 0;
    static final int EXIT_SCOPE_ERROR = 1;

    private final ScopeResolver scopeResolver;

    @Spec
    CommandSpec spec;

    @Option(names = "--repo", required = true, paramLabel = "<ruta|URL>",
            description = "Repositorio Git: ruta local o URL remota.")
    String repo;

    @ArgGroup(exclusive = true, multiplicity = "1")
    RangeArgs rangeArgs;

    public AnalyzeCommand(ScopeResolver scopeResolver) {
        this.scopeResolver = scopeResolver;
    }

    @Override
    public Integer call() {
        try {
            var resolved = scopeResolver.resolve(toRepoScope());
            var out = spec.commandLine().getOut();
            out.printf("Repositorio: %s%n", resolved.source().location());
            out.printf("Rango: %s%n", describe(resolved.source().range()));
            out.printf("Commits: %d (%s..%s)%n", resolved.size(),
                    resolved.first().shortId(), resolved.last().shortId());
            return EXIT_OK;
        } catch (ScopeException e) {
            spec.commandLine().getErr()
                    .printf("Error [%s]: %s%n", e.code(), e.getMessage());
            return EXIT_SCOPE_ERROR;
        }
    }

    private RepoScope toRepoScope() {
        if (rangeArgs.last != null) {
            return RepoScope.lastN(repo, rangeArgs.last);
        }
        var parts = rangeArgs.range.split("\\.\\.", -1);
        if (parts.length != 2) {
            throw new ParameterException(spec.commandLine(),
                    "Formato de --range inválido: «" + rangeArgs.range + "» (se espera A..B)");
        }
        return RepoScope.between(repo, parts[0], parts[1]);
    }

    private static String describe(RepoScope.Range range) {
        return switch (range) {
            case RepoScope.Range.Between between -> between.from() + ".." + between.to();
            case RepoScope.Range.LastN lastN -> "últimos " + lastN.n();
        };
    }

    /** {@code --range A..B} y {@code --last N} son mutuamente excluyentes; exactamente uno. */
    static class RangeArgs {

        @Option(names = "--range", paramLabel = "<A..B>",
                description = "Rango inclusivo de commits (A debe ser ancestro de B).")
        String range;

        @Option(names = "--last", paramLabel = "<N>",
                description = "Los últimos N commits de la rama actual.")
        Integer last;
    }
}
