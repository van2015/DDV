package ddv.application.pipeline;

import ddv.domain.exception.ParseException;
import ddv.domain.exception.ScopeException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StructuralAnalyzerTest {

    private static final RepoScope SOURCE = RepoScope.lastN("/repo", 2);

    private final Commit c1 = Commit.of("c1");
    private final Commit c2 = Commit.of("c2");
    private final Commit c3 = Commit.of("c3");

    private final GitSourcePort gitSourcePort = mock(GitSourcePort.class);
    private final ParserPort parserPort = mock(ParserPort.class);
    private final RepositoryHandle handle = mock(RepositoryHandle.class);
    private final StructuralAnalyzer analyzer = new StructuralAnalyzer(
            gitSourcePort, parserPort, SourceFileFilter.javaFiles());

    private static CommitDelta delta(Commit previous, Commit current, String... paths) {
        var files = Arrays.stream(paths)
                .map(path -> new CommitDelta.FileChange(path, CommitDelta.ChangeType.MODIFIED))
                .toList();
        return new CommitDelta(previous, current, files);
    }

    private static CodeStructure structureWith(String className, String... methodNames) {
        var members = Arrays.stream(methodNames)
                .map(name -> new MemberStructure(name, MemberKind.METHOD, List.of()))
                .toList();
        return new CodeStructure(List.of(),
                List.of(new ClassStructure(className, ClassKind.CLASS, List.of(), members, List.of())));
    }

    private static DeltaSequence sequenceOf(CommitDelta... deltas) {
        var commits = new ArrayList<Commit>();
        commits.add(deltas[0].previous());
        for (var delta : deltas) {
            commits.add(delta.current());
        }
        var scope = new AnalysisScope(SOURCE, List.copyOf(commits));
        return new DeltaSequence(scope, List.of(deltas));
    }

    @Test
    @DisplayName("Both versions are parsed and a new member marks the class modified")
    void parsesBothVersionsAndReportsNewMembers() {
        var delta = delta(c1, c2, "src/A.java", "README.md");
        var sequence = sequenceOf(delta);
        when(gitSourcePort.open(SOURCE)).thenReturn(handle);
        when(gitSourcePort.content(handle, c1, "src/A.java")).thenReturn(Optional.of("old source"));
        when(gitSourcePort.content(handle, c2, "src/A.java")).thenReturn(Optional.of("new source"));
        when(parserPort.parse("src/A.java", "old source"))
                .thenReturn(structureWith("A", "oldMethod"));
        when(parserPort.parse("src/A.java", "new source"))
                .thenReturn(structureWith("A", "oldMethod", "newMethod"));

        var result = analyzer.analyze(sequence);

        assertThat(result.size()).isEqualTo(1);
        var fileChange = result.changes().get(0).files().get(0);
        assertThat(fileChange.path()).isEqualTo("src/A.java");
        assertThat(fileChange.classes()).singleElement().satisfies(classChange -> {
            assertThat(classChange.name()).isEqualTo("A");
            assertThat(classChange.kind()).isEqualTo(ChangeKind.MODIFIED);
            assertThat(classChange.addedMembers())
                    .extracting(MemberStructure::name)
                    .containsExactly("newMethod");
        });
        verify(handle).close();
    }

    @Test
    @DisplayName("A file that did not exist yields an added class")
    void addedFileYieldsAddedClass() {
        var delta = new CommitDelta(c1, c2, List.of(
                new CommitDelta.FileChange("src/New.java", CommitDelta.ChangeType.ADDED)));
        var sequence = sequenceOf(delta);
        when(gitSourcePort.open(SOURCE)).thenReturn(handle);
        when(gitSourcePort.content(handle, c1, "src/New.java")).thenReturn(Optional.empty());
        when(gitSourcePort.content(handle, c2, "src/New.java"))
                .thenReturn(Optional.of("source"));
        when(parserPort.parse("src/New.java", "source"))
                .thenReturn(structureWith("New", "run"));

        var classChange = analyzer.analyze(sequence).changes().get(0).files().get(0)
                .classes().get(0);

        assertThat(classChange.kind()).isEqualTo(ChangeKind.ADDED);
        assertThat(classChange.addedMembers()).extracting(MemberStructure::name)
                .containsExactly("run");
    }

    @Test
    @DisplayName("A file deleted by the delta yields a removed class")
    void removedFileYieldsRemovedClass() {
        var delta = new CommitDelta(c1, c2, List.of(
                new CommitDelta.FileChange("src/Old.java", CommitDelta.ChangeType.DELETED)));
        var sequence = sequenceOf(delta);
        when(gitSourcePort.open(SOURCE)).thenReturn(handle);
        when(gitSourcePort.content(handle, c1, "src/Old.java")).thenReturn(Optional.of("source"));
        when(gitSourcePort.content(handle, c2, "src/Old.java")).thenReturn(Optional.empty());
        when(parserPort.parse("src/Old.java", "source"))
                .thenReturn(structureWith("Old", "gone"));

        var classChange = analyzer.analyze(sequence).changes().get(0).files().get(0)
                .classes().get(0);

        assertThat(classChange.kind()).isEqualTo(ChangeKind.REMOVED);
        assertThat(classChange.removedMembers()).extracting(MemberStructure::name)
                .containsExactly("gone");
    }

    @Test
    @DisplayName("Files without source extension are never read nor parsed")
    void irrelevantFilesAreNeverRead() {
        var sequence = sequenceOf(delta(c1, c2, "README.md"));
        when(gitSourcePort.open(SOURCE)).thenReturn(handle);

        var result = analyzer.analyze(sequence);

        assertThat(result.changes().get(0).files()).isEmpty();
        verify(gitSourcePort, never()).content(any(), any(), eq("README.md"));
        verify(parserPort, never()).parse(eq("README.md"), anyString());
        verify(handle).close();
    }

    @Test
    @DisplayName("A parse failure propagates and still closes the handle")
    void parseFailureClosesHandle() {
        var sequence = sequenceOf(delta(c1, c2, "src/A.java"));
        when(gitSourcePort.open(SOURCE)).thenReturn(handle);
        when(gitSourcePort.content(handle, c1, "src/A.java")).thenReturn(Optional.of("broken"));
        when(gitSourcePort.content(handle, c2, "src/A.java")).thenReturn(Optional.of("broken"));
        when(parserPort.parse(eq("src/A.java"), anyString()))
                .thenThrow(new ParseException(ParseException.Code.SYNTAX_ERROR, "boom"));

        assertThatThrownBy(() -> analyzer.analyze(sequence))
                .isInstanceOf(ParseException.class);

        verify(handle).close();
    }

    @Test
    @DisplayName("An open failure propagates without reading any content")
    void openFailurePropagates() {
        var sequence = sequenceOf(delta(c1, c2, "src/A.java"));
        when(gitSourcePort.open(SOURCE))
                .thenThrow(new ScopeException(ScopeException.Code.REPO_NOT_FOUND, "missing"));

        assertThatThrownBy(() -> analyzer.analyze(sequence))
                .isInstanceOf(ScopeException.class);

        verify(gitSourcePort, never()).content(any(), any(), anyString());
    }

    @Test
    @DisplayName("Every delta is analyzed and kept in order with its provenance")
    void everyDeltaIsAnalyzedInOrder() {
        var first = delta(c1, c2, "src/A.java");
        var second = delta(c2, c3, "src/A.java");
        var sequence = sequenceOf(first, second);
        when(gitSourcePort.open(SOURCE)).thenReturn(handle);
        when(gitSourcePort.content(eq(handle), any(), eq("src/A.java")))
                .thenReturn(Optional.of("class A { }"));
        when(parserPort.parse("src/A.java", "class A { }"))
                .thenReturn(structureWith("A"));

        var result = analyzer.analyze(sequence);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.changes())
                .extracting(StructuralChange::delta)
                .containsExactly(first, second);
        verify(handle).close();
    }
}
