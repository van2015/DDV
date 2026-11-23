package ddv.domain.model;

import ddv.domain.exception.DiffException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeltaSequenceTest {

    private static final AnalysisScope SOURCE = new AnalysisScope(
            RepoScope.lastN("/repo", 3),
            List.of(Commit.of("c1"), Commit.of("c2"), Commit.of("c3")));

    private static CommitDelta delta(String previous, String current) {
        return new CommitDelta(Commit.of(previous), Commit.of(current), List.of());
    }

    @Test
    @DisplayName("Aligned sequence is accepted and reports its size")
    void alignedSequenceIsAccepted() {
        var sequence = new DeltaSequence(SOURCE,
                List.of(delta("c1", "c2"), delta("c2", "c3")));

        assertThat(sequence.size()).isEqualTo(2);
        assertThat(sequence.source()).isEqualTo(SOURCE);
    }

    @Test
    @DisplayName("Delta count different from the number of pairs throws INVALID_ARGUMENT")
    void deltaCountMustMatchConsecutivePairs() {
        assertThatThrownBy(() -> new DeltaSequence(SOURCE, List.of(delta("c1", "c2"))))
                .isInstanceOf(DiffException.class)
                .extracting(e -> ((DiffException) e).code())
                .isEqualTo(DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Delta out of pair order throws INVALID_ARGUMENT")
    void outOfOrderDeltasAreRejected() {
        assertThatThrownBy(() -> new DeltaSequence(SOURCE,
                List.of(delta("c2", "c3"), delta("c1", "c2"))))
                .isInstanceOf(DiffException.class)
                .extracting(e -> ((DiffException) e).code())
                .isEqualTo(DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Null deltas throw NullPointerException")
    void nullDeltasThrowNullPointerException() {
        assertThatThrownBy(() -> new DeltaSequence(SOURCE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The delta list is an immutable defensive copy")
    void deltaListIsImmutable() {
        var external = new ArrayList<>(List.of(delta("c1", "c2"), delta("c2", "c3")));
        var sequence = new DeltaSequence(SOURCE, external);

        external.clear();
        assertThat(sequence.size()).isEqualTo(2);

        assertThatThrownBy(() -> sequence.deltas().add(delta("c1", "c2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("A single-commit scope yields an empty sequence")
    void singleCommitScopeYieldsEmptySequence() {
        var single = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(Commit.of("c1")));

        var sequence = new DeltaSequence(single, List.of());

        assertThat(sequence.size()).isZero();
        assertThat(sequence.deltas()).isEmpty();
    }
}
