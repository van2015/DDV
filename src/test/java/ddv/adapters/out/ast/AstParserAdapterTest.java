package ddv.adapters.out.ast;

import ddv.domain.exception.ParseException;
import ddv.domain.model.CodeStructure;
import ddv.domain.model.CodeStructure.ClassKind;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static ddv.assertions.DdvAssertions.assertParseThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AstParserAdapterTest {

    private final AstParserAdapter adapter = new AstParserAdapter();

    private static MemberStructure member(CodeStructure.ClassStructure clazz, String name) {
        return clazz.members().stream()
                .filter(member -> member.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No member named " + name));
    }

    @Test
    @DisplayName("A class is parsed with its imports, fields and methods")
    void parsesClassWithImportsFieldsAndMethods() {
        var source = """
                package demo;

                import java.util.List;

                public class Greeter {
                    private final List<String> names;

                    public String greet(String name) {
                        return name;
                    }
                }
                """;

        var structure = adapter.parse("src/Greeter.java", source);

        assertThat(structure.imports()).containsExactly("java.util.List");
        var clazz = structure.classes().get(0);
        assertThat(clazz.name()).isEqualTo("Greeter");
        assertThat(clazz.kind()).isEqualTo(ClassKind.CLASS);
        assertThat(clazz.members())
                .extracting(MemberStructure::name)
                .containsExactlyInAnyOrder("names", "greet");
        assertThat(member(clazz, "greet").kind()).isEqualTo(MemberKind.METHOD);
        assertThat(member(clazz, "greet").parameterTypes()).containsExactly("String");
        assertThat(member(clazz, "names").kind()).isEqualTo(MemberKind.FIELD);
    }

    @Test
    @DisplayName("Interface, enum, record and annotation kinds are detected")
    void detectsEveryClassKind() {
        var structure = adapter.parse("src/All.java", """
                interface Signal { }
                enum Color { RED, GREEN }
                record Point(int x, int y) { }
                @interface Marker { }
                """);

        assertThat(structure.classes())
                .extracting(CodeStructure.ClassStructure::name, CodeStructure.ClassStructure::kind)
                .containsExactly(
                        tuple("Signal", ClassKind.INTERFACE),
                        tuple("Color", ClassKind.ENUM),
                        tuple("Point", ClassKind.RECORD),
                        tuple("Marker", ClassKind.ANNOTATION));
        assertThat(structure.classes().get(1).members())
                .extracting(MemberStructure::name)
                .containsExactlyInAnyOrder("RED", "GREEN");
        assertThat(structure.classes().get(2).members())
                .extracting(MemberStructure::name)
                .containsExactlyInAnyOrder("x", "y");
    }

    @Test
    @DisplayName("Nested classes are reported with qualified names")
    void nestedClassesUseQualifiedNames() {
        var structure = adapter.parse("src/Outer.java", """
                class Outer {
                    class Inner {
                        void run() { }
                    }
                }
                """);

        assertThat(structure.classes())
                .extracting(CodeStructure.ClassStructure::name)
                .containsExactly("Outer", "Outer.Inner");
    }

    @Test
    @DisplayName("SuperTypes come from extends and implements, usages from signatures and bodies")
    void superTypesAndUsagesAreCollected() {
        var structure = adapter.parse("src/B.java", """
                import java.util.List;

                class B extends A implements I {
                    void m(List<String> items) {
                        var builder = new StringBuilder();
                    }
                }
                """);

        var clazz = structure.classes().get(0);
        assertThat(clazz.superTypes()).containsExactly("A", "I");
        assertThat(clazz.typeUsages())
                .contains("List", "String", "StringBuilder")
                .doesNotContain("A", "I");
    }

    @Test
    @DisplayName("Wildcard and static imports keep their full name")
    void wildcardAndStaticImportsAreKept() {
        var structure = adapter.parse("src/C.java", """
                import java.util.*;
                import static java.util.Collections.emptyList;

                class C { }
                """);

        assertThat(structure.imports())
                .containsExactly("java.util.*", "java.util.Collections.emptyList");
    }

    @Test
    @DisplayName("Members of nested classes do not leak into the outer class")
    void nestedMembersDoNotLeakIntoOuterClass() {
        var structure = adapter.parse("src/Outer.java", """
                class Outer {
                    void outerMethod() { }
                    class Inner {
                        void innerMethod() { }
                    }
                }
                """);

        var outer = structure.classes().get(0);
        var inner = structure.classes().get(1);
        assertThat(outer.members()).extracting(MemberStructure::name)
                .containsExactly("outerMethod");
        assertThat(inner.members()).extracting(MemberStructure::name)
                .containsExactly("innerMethod");
        assertThat(outer.typeUsages()).isEmpty();
    }

    @Test
    @DisplayName("A syntax error throws SYNTAX_ERROR")
    void syntaxErrorThrowsSyntaxError() {
        assertParseThrows(() -> adapter.parse("src/Broken.java", "class A { void x( }"),
                ParseException.Code.SYNTAX_ERROR);
    }

    @Test
    @DisplayName("A blank path throws INVALID_INPUT")
    void blankPathThrowsInvalidInput() {
        assertParseThrows(() -> adapter.parse(" ", "class A { }"),
                ParseException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("An empty source yields an empty structure")
    void emptySourceYieldsEmptyStructure() {
        var structure = adapter.parse("src/Empty.java", "");

        assertThat(structure.imports()).isEmpty();
        assertThat(structure.classes()).isEmpty();
    }
}
