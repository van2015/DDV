package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.DiagramSpec;
import ddv.domain.model.RepoScope;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagramGeneratorTest {

    private final DiagramGenerator generator = new DiagramGenerator();

    private static StructuralChange changeOf(Commit previous, Commit current, String className) {
        var delta = new CommitDelta(previous, current, List.of(
                new CommitDelta.FileChange("src/" + className + ".java",
                        CommitDelta.ChangeType.MODIFIED)));
        var classChange = new StructuralChange.ClassChange(className,
                StructuralChange.ChangeKind.ADDED, List.of(), List.of(), List.of(), List.of());
        return new StructuralChange(delta, List.of(
                new StructuralChange.FileStructureChange("src/" + className + ".java",
                        List.of(classChange))));
    }

    private static StructuralSequence sequenceOf(StructuralChange... changes) {
        var commits = new ArrayList<Commit>();
        commits.add(changes[0].delta().previous());
        for (var change : changes) {
            commits.add(change.delta().current());
        }
        var deltas = new ArrayList<CommitDelta>();
        for (var change : changes) {
            deltas.add(change.delta());
        }
        var sequence = new DeltaSequence(
                new AnalysisScope(RepoScope.lastN("/repo", commits.size()),
                        List.copyOf(commits)), deltas);
        return new StructuralSequence(sequence, List.of(changes));
    }

    @Test
    @DisplayName("Generates one diagram per Sᵢ, in order and with its nodes")
    void generatesOneDiagramPerChangeInOrder() {
        var sequence = sequenceOf(
                changeOf(Commit.of("c1"), Commit.of("c2"), "A"),
                changeOf(Commit.of("c2"), Commit.of("c3"), "B"));

        var result = generator.generate(sequence);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.diagrams()).extracting(DiagramSpec::current)
                .extracting(Commit::id)
                .containsExactly("c2", "c3");
        assertThat(result.diagrams().get(0).nodes())
                .extracting(DiagramSpec.Node::name)
                .containsExactly("A");
        assertThat(result.diagrams().get(1).nodes())
                .extracting(DiagramSpec.Node::name)
                .containsExactly("B");
    }

    @Test
    @DisplayName("A change without structural changes yields an empty spec for the placeholder")
    void changeWithoutStructureYieldsEmptySpec() {
        var delta = new CommitDelta(Commit.of("c1"), Commit.of("c2"), List.of());
        var sequence = new StructuralSequence(
                new DeltaSequence(
                        new AnalysisScope(RepoScope.lastN("/repo", 2),
                                List.of(Commit.of("c1"), Commit.of("c2"))),
                        List.of(delta)),
                List.of(new StructuralChange(delta, List.of())));

        var result = generator.generate(sequence);

        assertThat(result.diagrams()).singleElement().satisfies(diagram -> {
            assertThat(diagram.nodes()).isEmpty();
            assertThat(diagram.edges()).isEmpty();
        });
    }

    @Test
    @DisplayName("A sequence without changes yields no diagrams")
    void emptySequenceYieldsNoDiagrams() {
        var single = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(Commit.of("c1")));
        var sequence = new StructuralSequence(
                new DeltaSequence(single, List.of()), List.of());

        var result = generator.generate(sequence);

        assertThat(result.size()).isZero();
    }
}
