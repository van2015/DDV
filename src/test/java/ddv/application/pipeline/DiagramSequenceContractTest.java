package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.DiagramSequence;
import ddv.domain.model.RepoScope;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.domain.model.StructuralSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the UC4 → UC5 seam: the future {@code EvolutionComparer} must be able
 * to consume a {@link DiagramSequence} coming out of the real
 * {@link DiagramGenerator} — one point per commit in order, the trajectory of a
 * class across commits, a placeholder point for commits without structural
 * changes and full provenance back to the original range — with no glue code.
 *
 * <p>If this test breaks, UC5 has to be revisited together with UC4.</p>
 */
class DiagramSequenceContractTest {

    private static final Commit C1 = Commit.of("c1");
    private static final Commit C2 = Commit.of("c2");
    private static final Commit C3 = Commit.of("c3");
    private static final Commit C4 = Commit.of("c4");
    private static final Commit C5 = Commit.of("c5");

    private final DiagramGenerator generator = new DiagramGenerator();
    private final RecordingEvolutionComparer comparer = new RecordingEvolutionComparer();

    private static StructuralChange.ClassChange classChange(String name, ChangeKind kind,
            String... addedMembers) {
        var members = Arrays.stream(addedMembers)
                .map(member -> new MemberStructure(member, MemberKind.METHOD, List.of()))
                .toList();
        return new StructuralChange.ClassChange(name, kind, members, List.of(), List.of(),
                List.of());
    }

    private static StructuralChange changeOf(Commit previous, Commit current,
            StructuralChange.ClassChange... classes) {
        var delta = new CommitDelta(previous, current, List.of(
                new CommitDelta.FileChange("src/A.java", CommitDelta.ChangeType.MODIFIED)));
        var files = classes.length == 0
                ? List.<StructuralChange.FileStructureChange>of()
                : List.of(new StructuralChange.FileStructureChange("src/A.java",
                        List.of(classes)));
        return new StructuralChange(delta, files);
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
        return new StructuralSequence(
                new DeltaSequence(new AnalysisScope(
                        RepoScope.lastN("/repo", commits.size()), List.copyOf(commits)), deltas),
                List.of(changes));
    }

    @Test
    @DisplayName("The timeline keeps one point per commit, in order")
    void timelineKeepsOnePointPerCommitInOrder() {
        var sequence = generator.generate(sequenceOf(
                changeOf(C1, C2, classChange("A", ChangeKind.ADDED, "oldMethod")),
                changeOf(C2, C3, classChange("A", ChangeKind.MODIFIED, "newMethod")),
                changeOf(C3, C4, classChange("A", ChangeKind.REMOVED)),
                changeOf(C4, C5)));

        comparer.compare(sequence);

        assertThat(comparer.timeline).containsExactly("c2", "c3", "c4", "c5");
    }

    @Test
    @DisplayName("A class trajectory shows its states and members across commits")
    void classTrajectoryShowsStatesAcrossCommits() {
        var sequence = generator.generate(sequenceOf(
                changeOf(C1, C2, classChange("A", ChangeKind.ADDED, "oldMethod")),
                changeOf(C2, C3, classChange("A", ChangeKind.MODIFIED, "newMethod")),
                changeOf(C3, C4, classChange("A", ChangeKind.REMOVED)),
                changeOf(C4, C5)));

        comparer.compare(sequence);

        assertThat(comparer.trajectory)
                .extracting(TrajectoryPoint::commit)
                .containsExactly("c2", "c3", "c4");
        assertThat(comparer.trajectory)
                .extracting(TrajectoryPoint::state)
                .containsExactly(ChangeKind.ADDED, ChangeKind.MODIFIED, ChangeKind.REMOVED);
        assertThat(comparer.trajectory.get(1).addedMembers())
                .containsExactly("newMethod");
    }

    @Test
    @DisplayName("Commits without structural changes keep their placeholder point")
    void commitsWithoutChangesKeepPlaceholderPoint() {
        var sequence = generator.generate(sequenceOf(
                changeOf(C1, C2, classChange("A", ChangeKind.ADDED, "oldMethod")),
                changeOf(C2, C3)));

        comparer.compare(sequence);

        assertThat(comparer.placeholders).containsExactly("c3");
        assertThat(comparer.timeline).containsExactly("c2", "c3");
    }

    @Test
    @DisplayName("The provenance reaches back to the original commit range")
    void provenanceReachesTheOriginalRange() {
        var sequence = generator.generate(sequenceOf(
                changeOf(C1, C2, classChange("A", ChangeKind.ADDED, "oldMethod")),
                changeOf(C2, C3)));

        comparer.compare(sequence);

        assertThat(comparer.source).isSameAs(sequence.source());
        assertThat(comparer.source.source().source().first().id()).isEqualTo("c1");
        assertThat(comparer.source.source().source().last().id()).isEqualTo("c3");
    }

    private record TrajectoryPoint(String commit, ChangeKind state, List<String> addedMembers) {
    }

    /** Simulacro de UC5: recorre la secuencia como lo haría EvolutionComparer. */
    private static final class RecordingEvolutionComparer {

        private final List<String> timeline = new ArrayList<>();
        private final List<String> placeholders = new ArrayList<>();
        private final List<TrajectoryPoint> trajectory = new ArrayList<>();
        private StructuralSequence source;

        void compare(DiagramSequence sequence) {
            source = sequence.source();
            for (var diagram : sequence.diagrams()) {
                timeline.add(diagram.current().id());
                if (diagram.nodes().isEmpty()) {
                    placeholders.add(diagram.current().id());
                }
                for (var node : diagram.nodes()) {
                    if (node.name().equals("A")) {
                        trajectory.add(new TrajectoryPoint(diagram.current().id(), node.state(),
                                node.addedMembers().stream().map(MemberStructure::name).toList()));
                    }
                }
            }
        }
    }
}
