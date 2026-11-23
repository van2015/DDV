package ddv.application.pipeline;

import ddv.domain.exception.DiffException;
import ddv.domain.model.CommitDelta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFileFilterTest {

    private final SourceFileFilter filter = SourceFileFilter.javaFiles();

    private static CommitDelta.FileChange change(String path) {
        return new CommitDelta.FileChange(path, CommitDelta.ChangeType.MODIFIED);
    }

    @Test
    @DisplayName("Only .java files are selected")
    void onlyJavaFilesAreSelected() {
        var selected = filter.filter(List.of(
                change("src/A.java"),
                change("README.md"),
                change("img/logo.png"),
                change("Makefile"),
                change("docs/notes.txt")));

        assertThat(selected)
                .extracting(CommitDelta.FileChange::path)
                .containsExactly("src/A.java");
    }

    @Test
    @DisplayName("Extension matching is case-insensitive")
    void extensionMatchingIsCaseInsensitive() {
        assertThat(filter.isRelevant(change("src/B.JAVA"))).isTrue();
        assertThat(filter.isRelevant(change("src/c.Java"))).isTrue();
    }

    @Test
    @DisplayName("Files without a source extension are rejected")
    void filesWithoutSourceExtensionAreRejected() {
        assertThat(filter.isRelevant(change("Makefile"))).isFalse();
        assertThat(filter.isRelevant(change("java"))).isFalse();
        assertThat(filter.isRelevant(change("src/MyJavadoc"))).isFalse();
    }

    @Test
    @DisplayName("The filter is language-agnostic and accepts custom extensions")
    void customExtensionsAreSupported() {
        var python = new SourceFileFilter(Set.of(".py"));

        assertThat(python.isRelevant(change("mod/app.py"))).isTrue();
        assertThat(python.isRelevant(change("mod/app.java"))).isFalse();
    }

    @Test
    @DisplayName("An empty extension set throws INVALID_ARGUMENT")
    void emptyExtensionSetThrowsInvalidArgument() {
        assertThatThrownBy(() -> new SourceFileFilter(Set.of()))
                .isInstanceOf(DiffException.class)
                .extracting(e -> ((DiffException) e).code())
                .isEqualTo(DiffException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("A null extension set throws NullPointerException")
    void nullExtensionSetThrowsNullPointerException() {
        assertThatThrownBy(() -> new SourceFileFilter(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The filtered result is immutable")
    void filteredResultIsImmutable() {
        var selected = filter.filter(List.of(change("src/A.java")));

        assertThatThrownBy(() -> selected.add(change("src/B.java")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
