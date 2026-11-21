package ddv.adapters.out.jgit;

import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;
import ddv.ports.RepositoryHandle;
import org.eclipse.jgit.api.Git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * {@link GitSourcePort} implementado con JGit.
 *
 * <p>Traducción de errores de infraestructura a {@link ScopeException}:
 * ruta local inexistente o que no es repositorio → {@code REPO_NOT_FOUND};
 * cualquier fallo al abrir un remoto → {@code NO_ACCESS} (sin parsear mensajes
 * para distinguir "no existe" de "sin autenticación").</p>
 */
public class JGitAdapter implements GitSourcePort {

    @Override
    public RepositoryHandle open(RepoScope scope) {
        if (scope.isRemote()) {
            return openRemote(scope.location());
        }
        return openLocal(scope.location());
    }

    private RepositoryHandle openLocal(String location) {
        var path = Path.of(location);
        if (!Files.exists(path)) {
            throw new ScopeException(ScopeException.Code.REPO_NOT_FOUND,
                    "La ruta no existe: " + location);
        }
        try {
            return new JGitHandle(Git.open(path.toFile()), null);
        } catch (IOException e) {
            throw new ScopeException(ScopeException.Code.REPO_NOT_FOUND,
                    "No es un repositorio Git accesible: " + location, e);
        }
    }

    private RepositoryHandle openRemote(String url) {
        Path workspace;
        try {
            workspace = Files.createTempDirectory("ddv-clone-");
        } catch (IOException e) {
            throw new ScopeException(ScopeException.Code.NO_ACCESS,
                    "No se pudo crear el espacio de trabajo para el clon", e);
        }
        try {
            var git = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(workspace.toFile())
                    .call();
            return new JGitHandle(git, workspace);
        } catch (Exception e) {
            deleteRecursively(workspace);
            throw new ScopeException(ScopeException.Code.NO_ACCESS,
                    "No se pudo abrir el repositorio remoto: " + url, e);
        }
    }

    @Override
    public AnalysisScope resolveRange(RepositoryHandle repository, RepoScope scope) {
        throw new UnsupportedOperationException("resolveRange se implementa en T5");
    }

    @Override
    public CommitDelta diff(RepositoryHandle repository, Commit previous, Commit current) {
        throw new UnsupportedOperationException("diff se implementa en el pipeline UC2");
    }

    private static final class JGitHandle implements RepositoryHandle {

        private final Git git;
        // null cuando el repositorio es local: no hay workspace que limpiar
        private final Path clonedWorkspace;

        private JGitHandle(Git git, Path clonedWorkspace) {
            this.git = git;
            this.clonedWorkspace = clonedWorkspace;
        }

        @Override
        public void close() {
            git.close();
            if (clonedWorkspace != null) {
                deleteRecursively(clonedWorkspace);
            }
        }
    }

    private static void deleteRecursively(Path root) {
        // limpieza de mejor esfuerzo: close() no debe fallar por errores de borrado
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
        }
    }
}
