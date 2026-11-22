package ddv.adapters.in.cli;

import ddv.adapters.out.jgit.JGitAdapter;
import ddv.application.scope.ScopeResolver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;

/**
 * Comando raíz {@code ddv} y punto de entrada de la aplicación.
 *
 * <p>Único lugar donde se ensamblan adaptadores y caso de uso (raíz de
 * composición): la CLI no sabe nada de JGit más allá de esta clase.</p>
 */
@Command(name = "ddv", mixinStandardHelpOptions = true,
        description = "DDV — detección de diferencias versionadas en la evolución del código.")
public class DdvCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        // sin subcomando se muestra la ayuda en lugar de terminar en silencio
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
        var resolver = new ScopeResolver(new JGitAdapter());
        var root = new CommandLine(new DdvCommand())
                .addSubcommand("analyze", new AnalyzeCommand(resolver));
        root.setOut(out).setErr(err);
        // picocli no propaga los writers a los subcomandos: cada uno tiene su CommandLine
        root.getSubcommands().values().forEach(sub -> sub.setOut(out).setErr(err));
        return root.execute(args);
    }
}
