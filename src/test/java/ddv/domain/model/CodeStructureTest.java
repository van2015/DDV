package ddv.domain.model;

import ddv.domain.exception.ParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertParseThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeStructureTest {

    private static CodeStructure.ClassStructure classStructure(String name) {
        return new CodeStructure.ClassStructure(name, CodeStructure.ClassKind.CLASS,
                List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("Null imports or classes throw NullPointerException")
    void nullListsThrowNullPointerException() {
        assertThatThrownBy(() -> new CodeStructure(null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CodeStructure(List.of(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("A blank class name throws INVALID_INPUT")
    void blankClassNameThrowsInvalidInput() {
        assertParseThrows(() -> classStructure("  "), ParseException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A blank member name throws INVALID_INPUT")
    void blankMemberNameThrowsInvalidInput() {
        assertParseThrows(() -> new CodeStructure.MemberStructure("",
                CodeStructure.MemberKind.METHOD, List.of()), ParseException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A null class kind throws NullPointerException")
    void nullClassKindThrowsNullPointerException() {
        assertThatThrownBy(() -> new CodeStructure.ClassStructure("A", null,
                List.of(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Every list is an immutable defensive copy")
    void listsAreImmutableDefensiveCopies() {
        var imports = new ArrayList<>(List.of("java.util.List"));
        var members = new ArrayList<>(List.of(
                new CodeStructure.MemberStructure("run", CodeStructure.MemberKind.METHOD, List.of())));
        var structure = new CodeStructure(imports,
                List.of(new CodeStructure.ClassStructure("A", CodeStructure.ClassKind.CLASS,
                        List.of("Runnable"), members, List.of("String"))));

        imports.clear();
        members.clear();

        assertThat(structure.imports()).containsExactly("java.util.List");
        assertThat(structure.classes().get(0).members()).hasSize(1);
        assertThatThrownBy(() -> structure.classes().add(classStructure("B")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> structure.imports().add("java.util.Map"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Structures are compared by value")
    void structuresAreComparedByValue() {
        var first = new CodeStructure(List.of("java.util.List"), List.of(classStructure("A")));
        var second = new CodeStructure(List.of("java.util.List"), List.of(classStructure("A")));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}
