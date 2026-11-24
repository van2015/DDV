package ddv.adapters.out.mermaid;

import ddv.domain.model.ClassTrajectory;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.DiagramSpec;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.ChangeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MermaidRendererTest {

    private final MermaidRenderer renderer = new MermaidRenderer();

    private static DiagramSpec spec(List<DiagramSpec.Node> nodes, List<DiagramSpec.Edge> edges) {
        return new DiagramSpec(Commit.of("aaa1111"), Commit.of("bbb2222"), nodes, edges);
    }

    private static MemberStructure method(String name, String... parameterTypes) {
        return new MemberStructure(name, MemberKind.METHOD, List.of(parameterTypes));
    }

    private static DiagramSpec.Node node(String name, ChangeKind state,
            MemberStructure... members) {
        return new DiagramSpec.Node(name, state, List.of(members));
    }

    @Test
    @DisplayName("Renders a class with its added members and a dependency edge")
    void rendersClassWithMembersAndDependencyEdge() {
        var document = renderer.render(spec(
                List.of(node("A", ChangeKind.ADDED, method("run"), method("build", "String"))),
                List.of(new DiagramSpec.Edge("A", "List"))));

        assertThat(document)
                .startsWith("# Diagrama estructural — bbb2222")
                .contains("```mermaid")
                .containsOnlyOnce("classDiagram")
                .contains("class A{")
                .contains("<<added>>")
                .contains("\n        run()\n")
                .contains("build(String)")
                .contains("A ..> List : usa")
                .endsWith("```\n");
    }

    @Test
    @DisplayName("Every change state is rendered as its stereotype")
    void everyChangeStateIsRenderedAsStereotype() {
        var document = renderer.render(spec(List.of(
                node("A", ChangeKind.ADDED),
                node("B", ChangeKind.MODIFIED),
                node("C", ChangeKind.REMOVED)), List.of()));

        assertThat(document)
                .contains("<<added>>")
                .contains("<<modified>>")
                .contains("<<removed>>");
    }

    @Test
    @DisplayName("An empty spec renders the placeholder without diagram code")
    void emptySpecRendersPlaceholder() {
        var document = renderer.render(spec(List.of(), List.of()));

        assertThat(document)
                .startsWith("# Diagrama estructural — bbb2222")
                .contains("Sin cambios estructurales en este commit.")
                .doesNotContain("```mermaid");
    }

    @Test
    @DisplayName("Names and generic types are sanitized for Mermaid")
    void namesAndTypesAreSanitized() {
        var document = renderer.render(spec(
                List.of(node("Outer.Inner", ChangeKind.MODIFIED,
                        method("run", "List<String>", "Map<String, Integer>"))),
                List.of(new DiagramSpec.Edge("Outer.Inner", "java.util.List"))));

        assertThat(document)
                .contains("class Outer_Inner{")
                .contains("run(List~String~, Map~String Integer~)")
                .contains("Outer_Inner ..> java_util_List : usa");
    }

    @Test
    @DisplayName("Fields render without parentheses and the title carries the commit message")
    void fieldsRenderWithoutParentheses() {
        var commit = new Commit("c1c1c1c1c1c1", null, "Añadir clase A", null);
        var document = renderer.render(new DiagramSpec(Commit.of("aaa1111"), commit,
                List.of(node("A", ChangeKind.ADDED,
                        new MemberStructure("names", MemberKind.FIELD, List.of()))),
                List.of()));

        assertThat(document)
                .startsWith("# Diagrama estructural — c1c1c1c1: Añadir clase A")
                .contains("\n        names\n")
                .doesNotContain("names()");
    }

    @Test
    @DisplayName("Renders the trajectory as a flowchart chain labelled with states")
    void rendersTrajectoryAsFlowchartChain() {
        var trajectory = new ClassTrajectory("A", List.of(
                trajectoryPoint("c2c2c2c2c2c2", ChangeKind.ADDED, "oldMethod"),
                trajectoryPoint("c3c3c3c3c3c3", ChangeKind.MODIFIED, "newMethod")));

        var document = renderer.renderTrajectory(trajectory);

        assertThat(document)
                .startsWith("# Trayectoria de A")
                .contains("```mermaid")
                .contains("flowchart LR")
                .contains("c2c2c2c2[\"c2c2c2c2 · añadida<br/>+ oldMethod()\"]")
                .contains(" -->\n    c3c3c3c3[")
                .contains("c3c3c3c3 · modificada<br/>+ newMethod()")
                .endsWith("```\n");
    }

    private static ClassTrajectory.Point trajectoryPoint(String commitId, ChangeKind kind,
            String member) {
        var change = new StructuralChange.ClassChange("A", kind, List.of(method(member)),
                List.of(), List.of(), List.of());
        return new ClassTrajectory.Point(Commit.of(commitId), change);
    }
}
