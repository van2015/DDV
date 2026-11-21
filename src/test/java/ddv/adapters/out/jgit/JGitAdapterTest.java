package ddv.adapters.out.jgit;

import ddv.domain.exception.ScopeException;
import ddv.domain.model.RepoScope;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JGitAdapterTest {

    private static final String CLONE_PREFIX = "ddv-clone-";

    private final JGitAdapter adapter = new JGitAdapter();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Opening an existing local repository returns a closable handle")
    void openLocalRepositoryReturnsClosableHandle() throws Exception {
        var repo = initEmptyRepo("local-repo");

        try (var handle = adapter.open(RepoScope.lastN(repo.toString(), 5))) {
            assertThat(handle).isNotNull();
        }
    }

    @Test
    @DisplayName("Missing local path throws REPO_NOT_FOUND")
    void missingLocalPathThrowsRepoNotFound() {
        assertThatThrownBy(() -> adapter.open(RepoScope.lastN("/definitely/missing/path", 1)))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.REPO_NOT_FOUND);
    }

    @Test
    @DisplayName("Existing non-git directory throws REPO_NOT_FOUND")
    void nonGitDirectoryThrowsRepoNotFound() throws IOException {
        var notARepo = Files.createDirectory(tempDir.resolve("not-a-repo"));

        assertThatThrownBy(() -> adapter.open(RepoScope.lastN(notARepo.toString(), 1)))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.REPO_NOT_FOUND);
    }

    @Test
    @DisplayName("Remote file:// URL clones into a temp workspace cleaned up on close")
    void remoteCloneCleansUpWorkspaceOnClose() throws Exception {
        var source = initRepoWithCommit("source-repo");
        var clonesBefore = tempCloneDirs();

        try (var handle = adapter.open(RepoScope.lastN(source.toUri().toString(), 5))) {
            assertThat(handle).isNotNull();
            assertThat(tempCloneDirs()).hasSize(clonesBefore.size() + 1);
        }

        assertThat(tempCloneDirs()).isEqualTo(clonesBefore);
    }

    @Test
    @DisplayName("Unreachable remote throws NO_ACCESS and leaves no workspace behind")
    void unreachableRemoteThrowsNoAccess() throws IOException {
        var clonesBefore = tempCloneDirs();

        assertThatThrownBy(() -> adapter.open(
                RepoScope.lastN("file:///tmp/ddv-repo-that-does-not-exist.git", 1)))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.NO_ACCESS);

        assertThat(tempCloneDirs()).isEqualTo(clonesBefore);
    }

    private Path initEmptyRepo(String name) throws Exception {
        var dir = Files.createDirectory(tempDir.resolve(name));
        try (var git = Git.init().setDirectory(dir.toFile()).call()) {
            return dir;
        }
    }

    private Path initRepoWithCommit(String name) throws Exception {
        var dir = initEmptyRepo(name);
        Files.writeString(dir.resolve("README.md"), "# fixture");
        try (var git = Git.open(dir.toFile())) {
            git.add().addFilepattern(".").call();
            var identity = new PersonIdent("ddv-test", "ddv-test@example.com");
            git.commit()
                    .setMessage("initial commit")
                    .setAuthor(identity)
                    .setCommitter(identity)
                    .call();
        }
        return dir;
    }

    private static List<String> tempCloneDirs() throws IOException {
        try (var stream = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return stream
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith(CLONE_PREFIX))
                    .sorted()
                    .toList();
        }
    }
}
