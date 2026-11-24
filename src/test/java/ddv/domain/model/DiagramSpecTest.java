package ddv.domain.model;

import ddv.domain.exception.DiagramException;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.StructuralChange.ChangeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertDiagramThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagramSpecTest {

    private static final Commit C1 = Commit.of("c1");
    private static final Commit C2 = Commit.of("c2");

    private static MemberStructure method(String name) {
        return new MemberStructure(name, MemberKind.METHOD, List.of());
    }

    private static DiagramSpec.Node node(String name) {
        return new DiagramSpec.Node(name, ChangeKind.MODIFIED, List.of());
    }

    @Test
    @DisplayName("Null commits or lists throw NullPointerException")
    void nullsThrowNullPointerException() {
        assertThatThrownBy(() -> new DiagramSpec(null, C2, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DiagramSpec(C1, C2, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DiagramSpec(C1, C2, List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("A blank node name throws INVALID_INPUT")
    void blankNodeNameThrowsInvalidInput() {
        assertDiagramThrows(() -> new DiagramSpec.Node("  ", ChangeKind.ADDED, List.of()),
                DiagramException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A blank edge endpoint throws INVALID_INPUT")
    void blankEdgeEndpointThrowsInvalidInput() {
        assertDiagramThrows(() -> new DiagramSpec.Edge("", "List"),
                DiagramException.Code.INVALID_INPUT);
        assertDiagramThrows(() -> new DiagramSpec.Edge("A", " "),
                DiagramException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A null node state throws NullPointerException")
    void nullNodeStateThrowsNullPointerException() {
        assertThatThrownBy(() -> new DiagramSpec.Node("A", null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Every list is an immutable defensive copy")
    void listsAreImmutableDefensiveCopies() {
        var nodes = new ArrayList<>(List.of(node("A")));
        var edges = new ArrayList<>(List.of(new DiagramSpec.Edge("A", "List")));
        var members = new ArrayList<>(List.of(method("added")));

        var spec = new DiagramSpec(C1, C2, nodes, edges);

        nodes.clear();
        edges.clear();

        assertThat(spec.nodes()).hasSize(1);
        assertThat(spec.edges()).hasSize(1);
        assertThatThrownBy(() -> spec.nodes().add(node("B")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> spec.edges().add(new DiagramSpec.Edge("B", "C")))
                .isInstanceOf(UnsupportedOperationException.class);

        var withMembers = new DiagramSpec.Node("A", ChangeKind.ADDED, members);
        members.clear();
        assertThat(withMembers.addedMembers()).hasSize(1);
        assertThatThrownBy(() -> withMembers.addedMembers().add(method("other")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Specs are compared by value")
    void specsAreComparedByValue() {
        var first = new DiagramSpec(C1, C2, List.of(node("A")), List.of());
        var second = new DiagramSpec(C1, C2, List.of(node("A")), List.of());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("An empty spec represents a commit without structural changes")
    void emptySpecIsAllowed() {
        var spec = new DiagramSpec(C1, C2, List.of(), List.of());

        assertThat(spec.nodes()).isEmpty();
        assertThat(spec.edges()).isEmpty();
    }
}
