package ddv.adapters.in.cli;

import ddv.application.evolution.EvolutionComparer;
import ddv.application.evolution.EvolutionReporter;
import ddv.application.pipeline.AnalysisRunner;
import ddv.application.scope.ScopeResolver;
import ddv.domain.exception.DiagramException;
import ddv.domain.exception.DiffException;
import ddv.domain.exception.EvolutionException;
import ddv.domain.exception.ParseException;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.RepoScope;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.concurrent.Callable;

/**
 * Subcomando {@code ddv analyze} — adaptador de entrada de UC1 + pipeline.
 *
 * <p>Traduce los argumentos CLI a {@link RepoScope}, resuelve el alcance,
 * ejecuta el pipeline de diagramas (UC2→UC4) y, con {@code --track}, añade la
 * comparación de evolución de una clase (UC5). Los errores tipados de los cinco
 * casos de uso salen por stderr con su código y exit 1.</p>
 */
@Command(name = "analyze", mixinStandardHelpOptions = true,
        description = "Analiza el repositorio: alcance, diagramas por commit "
                + "y, con --track, la evolución de una clase.")
public class AnalyzeCommand implements Callable<Integer> {

    /** Códigos de salida de la CLI. */
    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;

    private final ScopeResolver scopeResolver;
    private final AnalysisRunner analysisRunner;
    private final EvolutionComparer evolutionComparer;
    private final EvolutionReporter evolutionReporter;

    @Spec
    CommandSpec spec;

    @Option(names = "--repo", required = true, paramLabel = "<ruta|URL>",
            description = "Repositorio Git: ruta local o URL remota.")
    String repo;

    @ArgGroup(exclusive = true, multiplicity = "1")
    RangeArgs rangeArgs;

    @Option(names = "--track", paramLabel = "<clase>",
            description = "Compara además la evolución de la clase a lo largo del rango (UC5).")
    String track;

    public AnalyzeCommand(ScopeResolver scopeResolver, AnalysisRunner analysisRunner,
            EvolutionComparer evolutionComparer, EvolutionReporter evolutionReporter) {
        this.scopeResolver = scopeResolver;
        this.analysisRunner = analysisRunner;
        this.evolutionComparer = evolutionComparer;
        this.evolutionReporter = evolutionReporter;
    }

    @Override
    public Integer call() {
        try {
            var resolved = scopeResolver.resolve(toRepoScope());
            printScope(resolved);
            var exported = analysisRunner.run(resolved);
            spec.commandLine().getOut().printf("Diagramas exportados: %d%n", exported.size());
            if (track != null) {
                printEvolution(track);
            }
            return EXIT_OK;
        } catch (ScopeException e) {
            return fail(e.code(), e.getMessage());
        } catch (DiffException e) {
            return fail(e.code(), e.getMessage());
        } catch (ParseException e) {
            return fail(e.code(), e.getMessage());
        } catch (DiagramException e) {
            return fail(e.code(), e.getMessage());
        } catch (EvolutionException e) {
            return fail(e.code(), e.getMessage());
        }
    }

    private void printScope(AnalysisScope resolved) {
        var out = spec.commandLine().getOut();
        out.printf("Repositorio: %s%n", resolved.source().location());
        out.printf("Rango: %s%n", describe(resolved.source().range()));
        out.printf("Commits: %d (%s..%s)%n", resolved.size(),
                resolved.first().shortId(), resolved.last().shortId());
    }

    private void printEvolution(String className) {
        var trajectory = evolutionComparer.compare(className);
        var out = spec.commandLine().getOut();
        out.print(evolutionReporter.summary(trajectory));
        out.println();
        out.print(evolutionReporter.diagram(trajectory));
    }

    private int fail(Enum<?> code, String message) {
        spec.commandLine().getErr().printf("Error [%s]: %s%n", code, message);
        return EXIT_ERROR;
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
