package ddv.adapters.out.jgit;

import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;
import ddv.ports.RepositoryHandle;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
        if (!(repository instanceof JGitHandle handle)) {
            throw new ScopeException(ScopeException.Code.INVALID_ARGUMENT,
                    "Handle de repositorio no soportado por JGitAdapter");
        }
        try {
            var commits = switch (scope.range()) {
                case RepoScope.Range.Between between -> resolveBetween(handle.git, between);
                case RepoScope.Range.LastN lastN -> resolveLastN(handle.git, lastN.n());
            };
            return new AnalysisScope(scope, commits);
        } catch (ScopeException e) {
            throw e;
        } catch (Exception e) {
            throw new ScopeException(ScopeException.Code.NO_ACCESS,
                    "No se pudo resolver el rango de commits", e);
        }
    }

    private static List<Commit> resolveLastN(Git git, int n) throws Exception {
        if (git.getRepository().resolve(Constants.HEAD) == null) {
            throw new ScopeException(ScopeException.Code.EMPTY_RANGE,
                    "El repositorio no tiene commits (HEAD sin crear)");
        }
        var commits = new ArrayList<Commit>();
        for (var rev : git.log().setMaxCount(n).call()) {
            commits.add(toDomain(rev));
        }
        // git.log() entrega lo más reciente primero; AnalysisScope exige orden ascendente
        Collections.reverse(commits);
        return commits;
    }

    private static List<Commit> resolveBetween(Git git, RepoScope.Range.Between range) throws Exception {
        var repository = git.getRepository();
        var fromId = repository.resolve(range.from());
        var toId = repository.resolve(range.to());
        if (fromId == null) {
            throw new ScopeException(ScopeException.Code.COMMIT_NOT_FOUND,
                    "El commit no existe: " + range.from());
        }
        if (toId == null) {
            throw new ScopeException(ScopeException.Code.COMMIT_NOT_FOUND,
                    "El commit no existe: " + range.to());
        }

        Commit fromDomain;
        var walk = new RevWalk(repository);
        try {
            var from = parseCommit(walk, fromId, range.from());
            var to = parseCommit(walk, toId, range.to());
            if (!walk.isMergedInto(from, to)) {
                throw new ScopeException(ScopeException.Code.NOT_ANCESTOR,
                        range.from() + " no es ancestro de " + range.to());
            }
            fromDomain = toDomain(from);
        } finally {
            walk.dispose();
        }

        var exclusive = new ArrayList<Commit>();
        for (var rev : git.log().add(toId).not(fromId).call()) {
            exclusive.add(toDomain(rev));
        }
        Collections.reverse(exclusive);

        var commits = new ArrayList<Commit>();
        commits.add(fromDomain);
        commits.addAll(exclusive);
        return commits;
    }

    // JGit resuelve SHA inexistentes sin fallar; el error real aparece al parsear
    private static RevCommit parseCommit(RevWalk walk, AnyObjectId id, String label) throws IOException {
        try {
            return walk.parseCommit(id);
        } catch (MissingObjectException | IncorrectObjectTypeException e) {
            throw new ScopeException(ScopeException.Code.COMMIT_NOT_FOUND,
                    "El commit no existe: " + label, e);
        }
    }

    private static Commit toDomain(RevCommit commit) {
        return new Commit(commit.getName(), null, commit.getShortMessage(),
                commit.getAuthorIdent().getWhen().toInstant());
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
