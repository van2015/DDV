package ddv.domain.model;

import ddv.domain.model.CodeStructure.ClassKind;
import ddv.domain.model.CodeStructure.ClassStructure;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.domain.model.StructuralChange.FileStructureChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileStructureChangeTest {

    private static MemberStructure method(String name) {
        return new MemberStructure(name, MemberKind.METHOD, List.of());
    }

    private static CodeStructure structure(List<String> imports, ClassStructure... classes) {
        return new CodeStructure(imports, List.of(classes));
    }

    private static ClassStructure clazz(String name, List<MemberStructure> members,
            List<String> superTypes, List<String> usages) {
        return new ClassStructure(name, ClassKind.CLASS, superTypes, members, usages);
    }

    @Test
    @DisplayName("A new file yields an added class with all its members")
    void addedFileYieldsAddedClass() {
        var after = structure(List.of(),
                clazz("A", List.of(method("m"), method("n")), List.of(), List.of()));

        var change = FileStructureChange.between("src/A.java", structure(List.of()), after);

        var added = change.classes().get(0);
        assertThat(added.name()).isEqualTo("A");
        assertThat(added.kind()).isEqualTo(ChangeKind.ADDED);
        assertThat(added.addedMembers())
                .extracting(MemberStructure::name)
                .containsExactly("m", "n");
        assertThat(added.removedMembers()).isEmpty();
        assertThat(added.removedDependencies()).isEmpty();
    }

    @Test
    @DisplayName("A deleted file yields a removed class with all its members")
    void removedFileYieldsRemovedClass() {
        var before = structure(List.of(),
                clazz("A", List.of(method("m")), List.of(), List.of()));

        var change = FileStructureChange.between("src/A.java", before, structure(List.of()));

        var removed = change.classes().get(0);
        assertThat(removed.kind()).isEqualTo(ChangeKind.REMOVED);
        assertThat(removed.removedMembers())
                .extracting(MemberStructure::name)
                .containsExactly("m");
        assertThat(removed.addedMembers()).isEmpty();
        assertThat(removed.addedDependencies()).isEmpty();
    }

    @Test
    @DisplayName("A new method marks the class as modified")
    void addedMemberMarksClassAsModified() {
        var before = structure(List.of(), clazz("A", List.of(method("old")), List.of(), List.of()));
        var after = structure(List.of(),
                clazz("A", List.of(method("old"), method("added")), List.of(), List.of()));

        var modified = FileStructureChange.between("src/A.java", before, after).classes().get(0);

        assertThat(modified.kind()).isEqualTo(ChangeKind.MODIFIED);
        assertThat(modified.addedMembers())
                .extracting(MemberStructure::name)
                .containsExactly("added");
        assertThat(modified.removedMembers()).isEmpty();
    }

    @Test
    @DisplayName("A deleted method marks the class as modified")
    void removedMemberMarksClassAsModified() {
        var before = structure(List.of(),
                clazz("A", List.of(method("gone"), method("stay")), List.of(), List.of()));
        var after = structure(List.of(), clazz("A", List.of(method("stay")), List.of(), List.of()));

        var modified = FileStructureChange.between("src/A.java", before, after).classes().get(0);

        assertThat(modified.kind()).isEqualTo(ChangeKind.MODIFIED);
        assertThat(modified.removedMembers())
                .extracting(MemberStructure::name)
                .containsExactly("gone");
        assertThat(modified.addedMembers()).isEmpty();
    }

    @Test
    @DisplayName("A changed signature is a removed member plus an added one")
    void changedSignatureIsRemovePlusAdd() {
        var before = structure(List.of(), clazz("A",
                List.of(new MemberStructure("m", MemberKind.METHOD, List.of("String"))),
                List.of(), List.of()));
        var after = structure(List.of(), clazz("A",
                List.of(new MemberStructure("m", MemberKind.METHOD, List.of("int"))),
                List.of(), List.of()));

        var modified = FileStructureChange.between("src/A.java", before, after).classes().get(0);

        assertThat(modified.addedMembers()).extracting(MemberStructure::parameterTypes)
                .containsExactly(List.of("int"));
        assertThat(modified.removedMembers()).extracting(MemberStructure::parameterTypes)
                .containsExactly(List.of("String"));
    }

    @Test
    @DisplayName("A new type usage is reported as an added dependency")
    void newTypeUsageIsAddedDependency() {
        var before = structure(List.of(), clazz("A", List.of(), List.of(), List.of()));
        var after = structure(List.of(), clazz("A", List.of(), List.of(), List.of("List")));

        var modified = FileStructureChange.between("src/A.java", before, after).classes().get(0);

        assertThat(modified.kind()).isEqualTo(ChangeKind.MODIFIED);
        assertThat(modified.addedDependencies()).containsExactly("List");
    }

    @Test
    @DisplayName("File imports become dependencies of every class")
    void fileImportsBecomeClassDependencies() {
        var before = structure(List.of(), clazz("A", List.of(), List.of(), List.of()));
        var after = structure(List.of("java.util.List"),
                clazz("A", List.of(), List.of(), List.of()));

        var modified = FileStructureChange.between("src/A.java", before, after).classes().get(0);

        assertThat(modified.addedDependencies()).containsExactly("java.util.List");
    }

    @Test
    @DisplayName("An unchanged structure yields no class change")
    void unchangedStructureYieldsNoClassChange() {
        var source = structure(List.of("java.util.List"),
                clazz("A", List.of(method("m")), List.of("Runnable"), List.of("String")));

        var change = FileStructureChange.between("src/A.java", source, source);

        assertThat(change.classes()).isEmpty();
    }

    @Test
    @DisplayName("SuperTypes are dependencies but are not repeated as usages")
    void superTypesAreDependenciesOnce() {
        var before = structure(List.of(), clazz("A", List.of(), List.of(), List.of()));
        var after = structure(List.of(),
                clazz("A", List.of(), List.of("Base"), List.of()));

        var modified = FileStructureChange.between("src/A.java", before, after).classes().get(0);

        assertThat(modified.addedDependencies()).containsExactly("Base");
    }
}
