package ddv.domain.model;

import ddv.domain.exception.DiffException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertDiffThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuralSequenceTest {

    private static DeltaSequence sequenceOf(String... ids) {
        var commits = Arrays.stream(ids).map(Commit::of).toList();
        var scope = new AnalysisScope(RepoScope.lastN("/repo", commits.size()), commits);
        var deltas = new ArrayList<CommitDelta>();
        for (int i = 1; i < commits.size(); i++) {
            deltas.add(new CommitDelta(commits.get(i - 1), commits.get(i), List.of()));
        }
        return new DeltaSequence(scope, deltas);
    }

    private static StructuralChange changeFor(CommitDelta delta) {
        return new StructuralChange(delta, List.of());
    }

    @Test
    @DisplayName("One change per delta, in the same order")
    void oneChangePerDeltaInOrder() {
        var sequence = sequenceOf("c1", "c2", "c3");

        var changes = sequence.deltas().stream().map(StructuralSequenceTest::changeFor).toList();
        var result = new StructuralSequence(sequence, changes);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.changes())
                .extracting(StructuralChange::delta)
                .isEqualTo(sequence.deltas());
    }

    @Test
    @DisplayName("A count different from the number of deltas throws INVALID_ARGUMENT")
    void countMustMatchDeltas() {
        var sequence = sequenceOf("c1", "c2", "c3");

        assertDiffThrows(() -> new StructuralSequence(sequence,
                List.of(changeFor(sequence.deltas().get(0)))),
                DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("A change whose delta differs from its position throws INVALID_ARGUMENT")
    void outOfOrderChangeIsRejected() {
        var sequence = sequenceOf("c1", "c2", "c3");
        var swapped = List.of(
                changeFor(sequence.deltas().get(1)),
                changeFor(sequence.deltas().get(0)));

        assertDiffThrows(() -> new StructuralSequence(sequence, swapped),
                DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("Null sequence or changes throw NullPointerException")
    void nullsThrowNullPointerException() {
        var sequence = sequenceOf("c1", "c2");

        assertThatThrownBy(() -> new StructuralSequence(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StructuralSequence(sequence, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The change list is an immutable defensive copy")
    void changeListIsImmutable() {
        var sequence = sequenceOf("c1", "c2");
        var external = new ArrayList<>(List.of(changeFor(sequence.deltas().get(0))));
        var result = new StructuralSequence(sequence, external);

        external.clear();

        assertThat(result.size()).isEqualTo(1);
        assertThatThrownBy(() -> result.changes()
                .add(changeFor(sequence.deltas().get(0))))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
