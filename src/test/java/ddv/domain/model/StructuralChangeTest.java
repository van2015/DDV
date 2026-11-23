package ddv.domain.model;

import ddv.domain.exception.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertParseThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuralChangeTest {

    private static final CommitDelta DELTA = new CommitDelta(
            Commit.of("c1"), Commit.of("c2"), List.of());

    private static StructuralChange.ClassChange classChange(String name) {
        return new StructuralChange.ClassChange(name, StructuralChange.ChangeKind.ADDED,
                List.of(), List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("A null origin delta throws NullPointerException")
    void nullDeltaThrowsNullPointerException() {
        assertThatThrownBy(() -> new StructuralChange(null, List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The origin delta is kept for traceability")
    void originDeltaIsKept() {
        var change = new StructuralChange(DELTA, List.of());

        assertThat(change.delta()).isEqualTo(DELTA);
    }

    @Test
    @DisplayName("A null file list throws NullPointerException")
    void nullFilesThrowNullPointerException() {
        assertThatThrownBy(() -> new StructuralChange(DELTA, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("A file without structure changes is still recorded for traceability")
    void emptyFileListIsAllowed() {
        assertThat(new StructuralChange(DELTA, List.of()).files()).isEmpty();
    }

    @Test
    @DisplayName("A blank path throws INVALID_INPUT")
    void blankPathThrowsInvalidInput() {
        assertParseThrows(() -> new StructuralChange.FileStructureChange(" ", List.of()),
                ParseException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A blank class name in a change throws INVALID_INPUT")
    void blankClassNameThrowsInvalidInput() {
        assertParseThrows(() -> classChange(""), ParseException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A null change kind throws NullPointerException")
    void nullChangeKindThrowsNullPointerException() {
        assertThatThrownBy(() -> new StructuralChange.ClassChange("A", null,
                List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Every list is an immutable defensive copy")
    void listsAreImmutableDefensiveCopies() {
        var files = new ArrayList<>(List.of(
                new StructuralChange.FileStructureChange("src/A.java", List.of())));
        var change = new StructuralChange(DELTA, files);

        files.clear();

        assertThat(change.files()).hasSize(1);
        assertThatThrownBy(() -> change.files().add(
                new StructuralChange.FileStructureChange("src/B.java", List.of())))
                .isInstanceOf(UnsupportedOperationException.class);

        var added = new ArrayList<>(List.of(new CodeStructure.MemberStructure("run",
                CodeStructure.MemberKind.METHOD, List.of())));
        var classChange = new StructuralChange.ClassChange("A", StructuralChange.ChangeKind.MODIFIED,
                added, List.of(), List.of(), List.of());
        added.clear();

        assertThat(classChange.addedMembers()).hasSize(1);
        assertThatThrownBy(() -> classChange.addedDependencies().add("java.util.List"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
