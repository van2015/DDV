package ddv.application.pipeline;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.CodeStructure;
import ddv.domain.model.CodeStructure.ClassKind;
import ddv.domain.model.CodeStructure.ClassStructure;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.RepoScope;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.domain.model.StructuralSequence;
import ddv.ports.GitSourcePort;
import ddv.ports.ParserPort;
import ddv.ports.RepositoryHandle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks the UC3 → UC4 seam: the future {@code DiagramGenerator} must be able to
 * consume a {@link StructuralSequence} coming out of the real
 * {@link StructuralAnalyzer} — one diagram per delta, in order, labelled with
 * its commits and its added structures — with no glue code.
 *
 * <p>If this test breaks, UC4 has to be revisited together with UC3.</p>
 */
class StructuralSequenceContractTest {

    private static final RepoScope SOURCE = RepoScope.lastN("/repo", 3);

    private final Commit c1 = Commit.of("c1");
    private final Commit c2 = Commit.of("c2");
    private final Commit c3 = Commit.of("c3");

    private final GitSourcePort gitSourcePort = mock(GitSourcePort.class);
    private final ParserPort parserPort = mock(ParserPort.class);
    private final RepositoryHandle handle = mock(RepositoryHandle.class);
    private final StructuralAnalyzer analyzer = new StructuralAnalyzer(
            gitSourcePort, parserPort, SourceFileFilter.javaFiles());
    private final RecordingDiagramGenerator generator = new RecordingDiagramGenerator();

    private static CommitDelta delta(Commit previous, Commit current) {
        return new CommitDelta(previous, current, List.of(
                new CommitDelta.FileChange("src/A.java", CommitDelta.ChangeType.MODIFIED)));
    }

    private static DeltaSequence sequenceOf(CommitDelta... deltas) {
        var commits = new ArrayList<Commit>();
        commits.add(deltas[0].previous());
        for (var delta : deltas) {
            commits.add(delta.current());
        }
        return new DeltaSequence(new AnalysisScope(SOURCE, List.copyOf(commits)), List.of(deltas));
    }

    private static CodeStructure structureWith(List<String> usages, String... methodNames) {
        var members = Arrays.stream(methodNames)
                .map(name -> new MemberStructure(name, MemberKind.METHOD, List.of()))
                .toList();
        var clazz = new ClassStructure("A", ClassKind.CLASS, List.of(), members, usages);
        return new CodeStructure(List.of(), List.of(clazz));
    }

    private void scriptBothVersions() {
        when(gitSourcePort.open(any())).thenReturn(handle);
        when(gitSourcePort.content(handle, c1, "src/A.java")).thenReturn(Optional.of("old"));
        when(gitSourcePort.content(handle, c2, "src/A.java")).thenReturn(Optional.of("new"));
        when(gitSourcePort.content(handle, c3, "src/A.java")).thenReturn(Optional.of("newer"));
        when(parserPort.parse("src/A.java", "old"))
                .thenReturn(structureWith(List.of(), "oldMethod"));
        when(parserPort.parse("src/A.java", "new"))
                .thenReturn(structureWith(List.of("List"), "oldMethod", "newMethod"));
        when(parserPort.parse("src/A.java", "newer"))
                .thenReturn(structureWith(List.of("List"), "oldMethod", "newMethod", "newestMethod"));
    }

    @Test
    @DisplayName("The generator receives one diagram per delta, in order, labelled with its commits")
    void generatorReceivesOneDiagramPerDeltaInOrder() {
        scriptBothVersions();

        var sequence = analyzer.analyze(sequenceOf(delta(c1, c2), delta(c2, c3)));
        sequence.changes().forEach(generator::render);

        assertThat(generator.rendered).hasSize(2);
        assertThat(generator.rendered)
                .extracting(RenderedDiagram::previous)
                .extracting(Commit::id)
                .containsExactly("c1", "c2");
        assertThat(generator.rendered)
                .extracting(RenderedDiagram::current)
                .extracting(Commit::id)
                .containsExactly("c2", "c3");
    }

    @Test
    @DisplayName("Each diagram exposes the classes, members and dependencies added by its commit")
    void eachDiagramExposesItsAddedStructures() {
        scriptBothVersions();

        var sequence = analyzer.analyze(sequenceOf(delta(c1, c2), delta(c2, c3)));
        sequence.changes().forEach(generator::render);

        var first = generator.rendered.get(0);
        var second = generator.rendered.get(1);
        assertThat(first.addedClasses()).containsExactly("A");
        assertThat(first.addedMembers()).containsExactly("newMethod");
        assertThat(first.addedDependencies()).containsExactly("List");
        assertThat(second.addedMembers()).containsExactly("newestMethod");
        assertThat(second.addedDependencies()).isEmpty();
    }

    @Test
    @DisplayName("A single-commit range produces no diagrams")
    void singleCommitRangeProducesNoDiagrams() {
        when(gitSourcePort.open(any())).thenReturn(handle);
        var single = new DeltaSequence(
                new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(c1)), List.of());

        var sequence = analyzer.analyze(single);
        sequence.changes().forEach(generator::render);

        assertThat(sequence.size()).isZero();
        assertThat(generator.rendered).isEmpty();
    }

    private record RenderedDiagram(Commit previous, Commit current, List<String> addedClasses,
            List<String> addedMembers, List<String> addedDependencies) {
    }

    /** Simulacro de UC4: extrae de cada Sᵢ exactamente lo que un diagrama necesita. */
    private static final class RecordingDiagramGenerator {

        private final List<RenderedDiagram> rendered = new ArrayList<>();

        void render(StructuralChange change) {
            var addedClasses = new ArrayList<String>();
            var addedMembers = new ArrayList<String>();
            var addedDependencies = new ArrayList<String>();
            for (var file : change.files()) {
                for (var classChange : file.classes()) {
                    if (classChange.kind() != ChangeKind.REMOVED) {
                        addedClasses.add(classChange.name());
                    }
                    classChange.addedMembers()
                            .forEach(member -> addedMembers.add(member.name()));
                    addedDependencies.addAll(classChange.addedDependencies());
                }
            }
            rendered.add(new RenderedDiagram(change.delta().previous(),
                    change.delta().current(), addedClasses, addedMembers, addedDependencies));
        }
    }
}
