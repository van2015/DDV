package ddv.adapters.in.cli;

import ddv.adapters.out.ast.AstParserAdapter;
import ddv.adapters.out.file.FileStoreAdapter;
import ddv.adapters.out.jgit.JGitAdapter;
import ddv.adapters.out.mermaid.MermaidRenderer;
import ddv.application.evolution.EvolutionComparer;
import ddv.application.evolution.EvolutionReporter;
import ddv.application.pipeline.AnalysisRunner;
import ddv.application.pipeline.DiagramExporter;
import ddv.application.pipeline.DiagramGenerator;
import ddv.application.pipeline.DiffExtractor;
import ddv.application.pipeline.SourceFileFilter;
import ddv.application.pipeline.StructuralAnalyzer;
import ddv.application.scope.ScopeResolver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;

/**
 * Comando raíz {@code ddv} y punto de entrada de la aplicación.
 *
 * <p>Único lugar donde se ensamblan adaptadores y casos de uso (raíz de
 * composición): la CLI no sabe nada de JGit, JavaParser, Mermaid ni Gson más
 * allá de esta clase.</p>
 */
@Command(name = "ddv", mixinStandardHelpOptions = true,
        description = "DDV — detección de diferencias versionadas en la evolución del código.")
public class DdvCommand implements Runnable {

    private static final Path DEFAULT_OUTPUT = Path.of("ddv-out");

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        // el raíz solo agrupa subcomandos; sin ellos se muestra la ayuda
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    /** Ejecuta la CLI sin salir del JVM (usado por los tests). */
    static int execute(String[] args) {
        return execute(args, new PrintWriter(System.out, true), new PrintWriter(System.err, true));
    }

    static int execute(String[] args, PrintWriter out, PrintWriter err) {
        return execute(args, out, err, DEFAULT_OUTPUT);
    }

    static int execute(String[] args, PrintWriter out, PrintWriter err, Path output) {
        var root = new CommandLine(new DdvCommand())
                .addSubcommand("analyze", analyzeCommand(output));
        root.setOut(out).setErr(err);
        // picocli no propaga los writers a los subcomandos: cada uno tiene su CommandLine
        root.getSubcommands().values().forEach(sub -> sub.setOut(out).setErr(err));
        return root.execute(args);
    }

    private static AnalyzeCommand analyzeCommand(Path output) {
        var git = new JGitAdapter();
        var filter = SourceFileFilter.javaFiles();
        var store = new FileStoreAdapter(output);
        var renderer = new MermaidRenderer();
        var runner = new AnalysisRunner(
                new DiffExtractor(git, filter),
                new StructuralAnalyzer(git, new AstParserAdapter(), filter),
                new DiagramGenerator(),
                new DiagramExporter(renderer, store));
        return new AnalyzeCommand(new ScopeResolver(git), runner,
                new EvolutionComparer(store), new EvolutionReporter(renderer));
    }
}
