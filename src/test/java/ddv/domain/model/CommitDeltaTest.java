package ddv.domain.model;

import ddv.domain.exception.DiffException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertDiffThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommitDeltaTest {

    private final Commit previous = Commit.of("c1");
    private final Commit current = Commit.of("c2");

    @Test
    @DisplayName("FileChange with blank path throws INVALID_ARGUMENT")
    void blankPathThrowsInvalidArgument() {
        assertDiffThrows(() -> new CommitDelta.FileChange("  ", CommitDelta.ChangeType.ADDED),
                DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("FileChange trims the path")
    void pathIsTrimmed() {
        var change = new CommitDelta.FileChange("  src/A.java  ", CommitDelta.ChangeType.MODIFIED);

        assertThat(change.path()).isEqualTo("src/A.java");
    }

    @Test
    @DisplayName("FileChange with null type throws NullPointerException")
    void nullTypeThrowsNullPointerException() {
        assertThatThrownBy(() -> new CommitDelta.FileChange("src/A.java", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Delta requires both commits")
    void requiresBothCommits() {
        assertThatThrownBy(() -> new CommitDelta(null, current, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CommitDelta(previous, null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("File list is an immutable defensive copy")
    void fileListIsImmutable() {
        var external = new ArrayList<>(List.of(
                new CommitDelta.FileChange("a.java", CommitDelta.ChangeType.MODIFIED)));
        var delta = new CommitDelta(previous, current, external);

        external.clear();
        assertThat(delta.files()).hasSize(1);
        assertThatThrownBy(() -> delta.files().add(
                new CommitDelta.FileChange("b.java", CommitDelta.ChangeType.ADDED)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
