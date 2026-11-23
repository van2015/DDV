package ddv.adapters.out.jgit;

import ddv.domain.exception.DiffException;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;
import ddv.ports.RepositoryHandle;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@link GitSourcePort} implementado con JGit.
 *
 * <p>Traducción de errores de infraestructura a {@link ScopeException}:
 * ruta local inexistente o que no es repositorio → {@code REPO_NOT_FOUND};
 * cualquier fallo al abrir un remoto → {@code NO_ACCESS} (sin parsear mensajes
 * para distinguir "no existe" de "sin autenticación").</p>
 */
public class JGitAdapter implements GitSourcePort {

    private static final String UNSUPPORTED_HANDLE =
            "Handle de repositorio no soportado por JGitAdapter";

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

    // El puerto es opaco: solo este adaptador conoce el tipo real del handle,
    // así que uno ajeno se rechaza con INVALID_ARGUMENT tipado por el caso de uso
    private static Optional<JGitHandle> jgitHandleOf(RepositoryHandle repository) {
        return repository instanceof JGitHandle handle ? Optional.of(handle) : Optional.empty();
    }

    @Override
    public AnalysisScope resolveRange(RepositoryHandle repository, RepoScope scope) {
        var handle = jgitHandleOf(repository).orElseThrow(
                () -> new ScopeException(ScopeException.Code.INVALID_ARGUMENT, UNSUPPORTED_HANDLE));
        try {
            var commits = switch (scope.range()) {
                case RepoScope.Range.Between between -> resolveBetween(handle.git, between);
                case RepoScope.Range.LastN lastN -> resolveLastN(handle.git, lastN.n());
            };
            return new AnalysisScope(scope, commits);
        } catch (Exception e) {
            throw noAccess(e);
        }
    }

    /** Conserva los fallos de alcance ya tipados; traduce el resto a {@code NO_ACCESS}. */
    private static ScopeException noAccess(Exception cause) {
        return cause instanceof ScopeException scope ? scope
                : new ScopeException(ScopeException.Code.NO_ACCESS,
                        "No se pudo resolver el rango de commits", cause);
    }

    private static List<Commit> resolveLastN(Git git, int n) throws Exception {
        if (git.getRepository().resolve(Constants.HEAD) == null) {
            throw new ScopeException(ScopeException.Code.EMPTY_RANGE,
                    "El repositorio no tiene commits (HEAD sin crear)");
        }
        return toChronological(git.log().setMaxCount(n));
    }

    private static List<Commit> resolveBetween(Git git, RepoScope.Range.Between range) throws Exception {
        var repository = git.getRepository();
        var fromId = requireResolvable(repository, range.from());
        var toId = requireResolvable(repository, range.to());

        var commits = new ArrayList<Commit>();
        commits.add(requireAncestralStart(repository, range, fromId, toId));
        commits.addAll(toChronological(git.log().add(toId).not(fromId)));
        return commits;
    }

    private static ObjectId requireResolvable(Repository repository, String revision)
            throws IOException {
        var id = repository.resolve(revision);
        if (id == null) {
            throw new ScopeException(ScopeException.Code.COMMIT_NOT_FOUND,
                    "El commit no existe: " + revision);
        }
        return id;
    }

    private static Commit requireAncestralStart(Repository repository, RepoScope.Range.Between range,
            ObjectId fromId, ObjectId toId) throws IOException {
        var walk = new RevWalk(repository);
        try {
            var from = parseCommit(walk, fromId, range.from());
            var to = parseCommit(walk, toId, range.to());
            if (!walk.isMergedInto(from, to)) {
                throw new ScopeException(ScopeException.Code.NOT_ANCESTOR,
                        range.from() + " no es ancestro de " + range.to());
            }
            return toDomain(from);
        } finally {
            walk.dispose();
        }
    }

    // git.log() entrega lo más reciente primero; AnalysisScope exige orden ascendente
    private static List<Commit> toChronological(LogCommand log) throws GitAPIException {
        var commits = new ArrayList<Commit>();
        for (var rev : log.call()) {
            commits.add(toDomain(rev));
        }
        Collections.reverse(commits);
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
        var handle = jgitHandleOf(repository).orElseThrow(
                () -> new DiffException(DiffException.Code.INVALID_ARGUMENT, UNSUPPORTED_HANDLE));
        try {
            var files = scanChanges(handle.git.getRepository(), previous.id(), current.id());
            return new CommitDelta(previous, current, files);
        } catch (Exception e) {
            throw ioError(e, "No se pudo calcular el diff entre " + previous.shortId()
                    + " y " + current.shortId());
        }
    }

    @Override
    public Optional<String> content(RepositoryHandle repository, Commit commit, String path) {
        var handle = jgitHandleOf(repository).orElseThrow(
                () -> new DiffException(DiffException.Code.INVALID_ARGUMENT, UNSUPPORTED_HANDLE));
        var gitRepository = handle.git.getRepository();
        try (var walk = new RevWalk(gitRepository)) {
            return blobAt(gitRepository, walk, commit.id(), path);
        } catch (Exception e) {
            throw ioError(e, "No se pudo leer " + path + " en el commit " + commit.shortId());
        }
    }

    /** Conserva los fallos de diff/lectura ya tipados; traduce el resto a {@code IO_ERROR}. */
    private static DiffException ioError(Exception cause, String detail) {
        return cause instanceof DiffException diff ? diff
                : new DiffException(DiffException.Code.IO_ERROR, detail, cause);
    }

    private static Optional<String> blobAt(Repository repository, RevWalk walk,
            String commitId, String path) throws IOException {
        var entry = TreeWalk.forPath(repository, path, treeOf(walk, repository, commitId));
        if (entry == null) {
            return Optional.empty();
        }
        try (entry) {
            // un directorio (u otro objeto no-blob) no tiene contenido de archivo
            if (entry.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
                return Optional.empty();
            }
            var blob = repository.open(entry.getObjectId(0));
            return Optional.of(new String(blob.getBytes(), StandardCharsets.UTF_8));
        }
    }

    private static List<CommitDelta.FileChange> scanChanges(Repository repository,
            String previousId, String currentId) throws Exception {
        try (var walk = new RevWalk(repository);
             var formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);

            var changes = new ArrayList<CommitDelta.FileChange>();
            for (var entry : formatter.scan(treeOf(walk, repository, previousId),
                    treeOf(walk, repository, currentId))) {
                changes.addAll(toFileChanges(entry));
            }
            return changes;
        }
    }

    private static RevTree treeOf(RevWalk walk, Repository repository, String id) throws IOException {
        var objectId = repository.resolve(id);
        if (objectId == null) {
            throw new DiffException(DiffException.Code.IO_ERROR,
                    "El commit no existe en el repositorio: " + id);
        }
        return walk.parseCommit(objectId).getTree();
    }

    // Un rename se registra como la baja del origen y el alta del destino
    private static List<CommitDelta.FileChange> toFileChanges(DiffEntry entry) {
        return switch (entry.getChangeType()) {
            case ADD -> List.of(new CommitDelta.FileChange(entry.getNewPath(),
                    CommitDelta.ChangeType.ADDED));
            case MODIFY -> List.of(new CommitDelta.FileChange(entry.getNewPath(),
                    CommitDelta.ChangeType.MODIFIED));
            case DELETE -> List.of(new CommitDelta.FileChange(entry.getOldPath(),
                    CommitDelta.ChangeType.DELETED));
            case RENAME -> List.of(
                    new CommitDelta.FileChange(entry.getOldPath(), CommitDelta.ChangeType.DELETED),
                    new CommitDelta.FileChange(entry.getNewPath(), CommitDelta.ChangeType.ADDED));
            case COPY -> throw new DiffException(DiffException.Code.UNSUPPORTED,
                    "Tipo de cambio no soportado por el MVP: COPY (" + entry.getNewPath() + ")");
        };
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
