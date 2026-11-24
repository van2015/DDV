package ddv.adapters.out.file;

import ddv.domain.exception.DiagramException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static ddv.assertions.DdvAssertions.assertDiagramThrows;
import static org.assertj.core.api.Assertions.assertThat;

class FileStoreAdapterTest {

    private static final String DOCUMENT = "# Diagrama estructural — abc12345";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Writes the document as <commitId>.md under the root")
    void writesDocumentUnderRoot() throws IOException {
        var root = tempDir.resolve("out");
        var adapter = new FileStoreAdapter(root);

        var path = adapter.saveDiagram("abc12345", DOCUMENT);

        assertThat(path).isEqualTo(root.resolve("abc12345.md"));
        assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEqualTo(DOCUMENT);
    }

    @Test
    @DisplayName("Creates the missing directories of the root")
    void createsMissingDirectories() {
        var adapter = new FileStoreAdapter(tempDir.resolve("a/b/c"));

        var path = adapter.saveDiagram("abc12345", DOCUMENT);

        assertThat(path).exists();
    }

    @Test
    @DisplayName("A blank or path-like commit id throws INVALID_INPUT")
    void invalidCommitIdThrowsInvalidInput() {
        var adapter = new FileStoreAdapter(tempDir);

        assertDiagramThrows(() -> adapter.saveDiagram(" ", DOCUMENT),
                DiagramException.Code.INVALID_INPUT);
        assertDiagramThrows(() -> adapter.saveDiagram("../escape", DOCUMENT),
                DiagramException.Code.INVALID_INPUT);
        assertDiagramThrows(() -> adapter.saveDiagram("/etc/passwd", DOCUMENT),
                DiagramException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A root blocked by an existing file throws WRITE_ERROR")
    void blockedRootThrowsWriteError() throws IOException {
        var blocked = Files.createFile(tempDir.resolve("blocked"));
        var adapter = new FileStoreAdapter(blocked);

        assertDiagramThrows(() -> adapter.saveDiagram("abc12345", DOCUMENT),
                DiagramException.Code.WRITE_ERROR);
    }
}
