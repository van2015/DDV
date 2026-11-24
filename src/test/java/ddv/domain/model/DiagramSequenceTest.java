package ddv.domain.model;

import ddv.domain.exception.DiagramException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertDiagramThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagramSequenceTest {

    private static StructuralSequence structuralSequence() {
        var c1 = Commit.of("c1");
        var c2 = Commit.of("c2");
        var c3 = Commit.of("c3");
        var first = new CommitDelta(c1, c2, List.of());
        var second = new CommitDelta(c2, c3, List.of());
        var deltas = new DeltaSequence(
                new AnalysisScope(RepoScope.lastN("/repo", 3), List.of(c1, c2, c3)),
                List.of(first, second));
        return new StructuralSequence(deltas,
                List.of(new StructuralChange(first, List.of()),
                        new StructuralChange(second, List.of())));
    }

    private static List<DiagramSpec> diagramsOf(StructuralSequence sequence) {
        return sequence.changes().stream().map(DiagramSpec::from).toList();
    }

    @Test
    @DisplayName("One diagram per Sᵢ, aligned with its commits")
    void oneDiagramPerChangeInOrder() {
        var sequence = structuralSequence();

        var result = new DiagramSequence(sequence, diagramsOf(sequence));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.diagrams()).extracting(DiagramSpec::current)
                .extracting(Commit::id)
                .containsExactly("c2", "c3");
    }

    @Test
    @DisplayName("A count different from the number of Sᵢ throws INVALID_INPUT")
    void countMustMatchChanges() {
        var sequence = structuralSequence();

        assertDiagramThrows(() -> new DiagramSequence(sequence,
                List.of(diagramsOf(sequence).get(0))),
                DiagramException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A diagram whose commits differ from its Sᵢ throws INVALID_INPUT")
    void diagramWithWrongCommitsIsRejected() {
        var sequence = structuralSequence();
        var foreign = new DiagramSpec(Commit.of("x1"), Commit.of("x2"), List.of(), List.of());
        var wrong = List.of(foreign, diagramsOf(sequence).get(1));

        assertDiagramThrows(() -> new DiagramSequence(sequence, wrong),
                DiagramException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("Null sequence or diagrams throw NullPointerException")
    void nullsThrowNullPointerException() {
        var sequence = structuralSequence();

        assertThatThrownBy(() -> new DiagramSequence(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DiagramSequence(sequence, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The diagram list is an immutable defensive copy")
    void diagramListIsImmutable() {
        var sequence = structuralSequence();
        var external = new ArrayList<>(diagramsOf(sequence));
        var result = new DiagramSequence(sequence, external);

        external.clear();

        assertThat(result.size()).isEqualTo(2);
        assertThatThrownBy(() -> result.diagrams().add(diagramsOf(sequence).get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
