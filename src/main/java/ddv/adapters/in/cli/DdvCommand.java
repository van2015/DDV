package ddv.adapters.in.cli;

import ddv.adapters.out.jgit.JGitAdapter;
import ddv.application.uc1.ScopeResolver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

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
        var resolver = new ScopeResolver(new JGitAdapter());
        return new CommandLine(new DdvCommand())
                .addSubcommand("analyze", new AnalyzeCommand(resolver))
                .execute(args);
    }
}
