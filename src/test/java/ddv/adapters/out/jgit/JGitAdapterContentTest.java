package ddv.adapters.out.jgit;

import ddv.domain.exception.DiffException;
import ddv.domain.model.Commit;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static ddv.assertions.DdvAssertions.assertDiffThrows;
import static org.assertj.core.api.Assertions.assertThat;

class JGitAdapterContentTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");

    private final JGitAdapter adapter = new JGitAdapter();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Content is read at the requested commit, not at HEAD")
    void contentIsReadAtTheRequestedCommit() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.write(repo, "src/A.java", "class A { int x; }");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A { int y; }");
        var c2 = GitFixtures.commit(repo, "c2", T2);

        assertThat(content(repo, c1, "src/A.java")).hasValue("class A { int x; }");
        assertThat(content(repo, c2, "src/A.java")).hasValue("class A { int y; }");
    }

    @Test
    @DisplayName("A file that did not exist yet yields an empty optional")
    void fileAbsentAtTheCommitYieldsEmpty() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A {}");
        var c2 = GitFixtures.commit(repo, "c2", T2);

        assertThat(content(repo, c1, "src/A.java")).isEmpty();
        assertThat(content(repo, c2, "src/A.java")).hasValue("class A {}");
    }

    @Test
    @DisplayName("Non-ASCII content round-trips as UTF-8")
    void nonAsciiContentIsReadAsUtf8() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var source = "class A { // añadido: año, niño }";
        GitFixtures.write(repo, "src/A.java", source);
        var c1 = GitFixtures.commit(repo, "c1", T1);

        assertThat(content(repo, c1, "src/A.java")).hasValue(source);
    }

    @Test
    @DisplayName("A directory path yields an empty optional")
    void directoryPathYieldsEmpty() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.write(repo, "src/A.java", "class A {}");
        var c1 = GitFixtures.commit(repo, "c1", T1);

        assertThat(content(repo, c1, "src")).isEmpty();
    }

    @Test
    @DisplayName("A foreign handle throws INVALID_ARGUMENT")
    void foreignHandleThrowsInvalidArgument() {
        var commit = Commit.of("1111111111111111111111111111111111111111");
        ddv.ports.RepositoryHandle foreign = () -> { };

        assertDiffThrows(() -> adapter.content(foreign, commit, "src/A.java"),
                DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("An unknown commit id throws IO_ERROR")
    void unknownCommitThrowsIoError() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        var unknown = "0000000000000000000000000000000000000001";

        try (var handle = adapter.open(RepoScope.lastN(repo.toString(), 5))) {
            assertDiffThrows(() -> adapter.content(handle, Commit.of(unknown), "src/A.java"),
                    DiffException.Code.IO_ERROR);
        }
    }

    private Optional<String> content(Path repo, String commitId, String path) {
        try (var handle = adapter.open(RepoScope.lastN(repo.toString(), 5))) {
            return adapter.content(handle, Commit.of(commitId), path);
        }
    }
}
