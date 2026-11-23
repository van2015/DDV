package ddv.adapters.out.jgit;

import ddv.domain.exception.DiffException;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static ddv.assertions.DdvAssertions.assertDiffThrows;
import static org.assertj.core.api.Assertions.assertThat;

class JGitAdapterDiffTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");

    private final JGitAdapter adapter = new JGitAdapter();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("A new file is reported as ADDED")
    void addedFileIsReportedAsAdded() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A {}");
        var c2 = GitFixtures.commit(repo, "c2", T2);

        var delta = diff(repo, c1, c2);

        assertThat(typeOf(delta, "src/A.java")).isEqualTo(CommitDelta.ChangeType.ADDED);
        assertThat(typeOf(delta, "log.txt")).isEqualTo(CommitDelta.ChangeType.MODIFIED);
    }

    @Test
    @DisplayName("A changed file is reported as MODIFIED")
    void modifiedFileIsReportedAsModified() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A {}");
        var c2 = GitFixtures.commit(repo, "c2", T2);
        GitFixtures.write(repo, "src/A.java", "class A { int x; }");
        var c3 = GitFixtures.commit(repo, "c3", T3);

        var delta = diff(repo, c2, c3);

        assertThat(typeOf(delta, "src/A.java")).isEqualTo(CommitDelta.ChangeType.MODIFIED);
    }

    @Test
    @DisplayName("A removed file is reported as DELETED")
    void deletedFileIsReportedAsDeleted() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A {}");
        var c2 = GitFixtures.commit(repo, "c2", T2);
        GitFixtures.remove(repo, "src/A.java");
        var c3 = GitFixtures.commit(repo, "c3", T3);

        var delta = diff(repo, c2, c3);

        assertThat(typeOf(delta, "src/A.java")).isEqualTo(CommitDelta.ChangeType.DELETED);
    }

    @Test
    @DisplayName("A rename is reported as DELETED origin plus ADDED destination")
    void renamedFileIsReportedAsDeleteAndAdd() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "notes.txt", "una nota\n");
        var c2 = GitFixtures.commit(repo, "c2", T2);
        GitFixtures.rename(repo, "notes.txt", "docs/notes.md");
        var c3 = GitFixtures.commit(repo, "c3", T3);

        var delta = diff(repo, c2, c3);

        assertThat(typeOf(delta, "notes.txt")).isEqualTo(CommitDelta.ChangeType.DELETED);
        assertThat(typeOf(delta, "docs/notes.md")).isEqualTo(CommitDelta.ChangeType.ADDED);
    }

    @Test
    @DisplayName("A binary file is reported with its change type")
    void binaryFileIsReported() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        GitFixtures.writeBytes(repo, "assets/logo.bin", new byte[]{0x00, 0x01, 0x02, 0x03});
        var c2 = GitFixtures.commit(repo, "c2", T2);

        var delta = diff(repo, c1, c2);

        assertThat(typeOf(delta, "assets/logo.bin")).isEqualTo(CommitDelta.ChangeType.ADDED);
    }

    @Test
    @DisplayName("Diffing a commit against itself yields no changes")
    void diffAgainstSameCommitYieldsNoChanges() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);

        var delta = diff(repo, c1, c1);

        assertThat(delta.files()).isEmpty();
    }

    @Test
    @DisplayName("A foreign handle throws INVALID_ARGUMENT")
    void foreignHandleThrowsInvalidArgument() {
        var commit = Commit.of("1111111111111111111111111111111111111111");
        ddv.ports.RepositoryHandle foreign = () -> { };

        assertDiffThrows(() -> adapter.diff(foreign, commit, commit),
                DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("An unknown commit id throws IO_ERROR")
    void unknownCommitThrowsIoError() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        var c1 = GitFixtures.commit(repo, "c1", T1);
        var unknown = "0000000000000000000000000000000000000001";

        try (var handle = adapter.open(RepoScope.lastN(repo.toString(), 5))) {
            assertDiffThrows(() -> adapter.diff(handle, Commit.of(unknown), Commit.of(c1)),
                    DiffException.Code.IO_ERROR);
        }
    }

    private CommitDelta diff(Path repo, String previousId, String currentId) {
        try (var handle = adapter.open(RepoScope.lastN(repo.toString(), 5))) {
            return adapter.diff(handle, Commit.of(previousId), Commit.of(currentId));
        }
    }

    private static CommitDelta.ChangeType typeOf(CommitDelta delta, String path) {
        return delta.files().stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No change recorded for " + path
                        + " in " + delta.files()))
                .type();
    }
}
