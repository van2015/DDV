package ddv.adapters.out.file;

import ddv.domain.exception.EvolutionException;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.StructuralChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertEvolutionThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStoreAdapterStatesTest {

    @TempDir
    Path tempDir;

    private static StructuralChange statesFixture() {
        var previous = new Commit("c1c1c1c1c1c1", null, "c1",
                Instant.parse("2026-01-01T00:00:00Z"));
        var current = new Commit("c2c2c2c2c2c2", null, "c2",
                Instant.parse("2026-01-02T00:00:00Z"));
        var delta = new CommitDelta(previous, current, List.of(
                new CommitDelta.FileChange("src/A.java", CommitDelta.ChangeType.MODIFIED)));
        var classChange = new StructuralChange.ClassChange("A",
                StructuralChange.ChangeKind.MODIFIED,
                List.of(new MemberStructure("newMethod", MemberKind.METHOD, List.of("String"))),
                List.of(),
                List.of("java.util.List"),
                List.of());
        return new StructuralChange(delta, List.of(
                new StructuralChange.FileStructureChange("src/A.java", List.of(classChange))));
    }

    @Test
    @DisplayName("States round-trip through JSON preserving every value")
    void statesRoundTripThroughJson() {
        var adapter = new FileStoreAdapter(tempDir.resolve("out"));
        var states = List.of(statesFixture());

        adapter.saveStates(states);
        var loaded = adapter.loadStates();

        assertThat(loaded).isEqualTo(states);
        assertThat(tempDir.resolve("out").resolve("states.json")).exists();
    }

    @Test
    @DisplayName("Loading without a saved file throws READ_ERROR")
    void loadingWithoutFileThrowsReadError() {
        var adapter = new FileStoreAdapter(tempDir.resolve("missing"));

        assertEvolutionThrows(adapter::loadStates, EvolutionException.Code.READ_ERROR);
    }

    @Test
    @DisplayName("Loading corrupted JSON throws READ_ERROR")
    void loadingCorruptedJsonThrowsReadError() throws IOException {
        var root = tempDir.resolve("out");
        Files.createDirectories(root);
        Files.writeString(root.resolve("states.json"), "{ not json",
                StandardCharsets.UTF_8);
        var adapter = new FileStoreAdapter(root);

        assertEvolutionThrows(adapter::loadStates, EvolutionException.Code.READ_ERROR);
    }

    @Test
    @DisplayName("Loading an empty file throws READ_ERROR")
    void loadingEmptyFileThrowsReadError() throws IOException {
        var root = tempDir.resolve("out");
        Files.createDirectories(root);
        Files.writeString(root.resolve("states.json"), "null", StandardCharsets.UTF_8);
        var adapter = new FileStoreAdapter(root);

        assertEvolutionThrows(adapter::loadStates, EvolutionException.Code.READ_ERROR);
    }

    @Test
    @DisplayName("A blocked root throws WRITE_ERROR when saving states")
    void blockedRootThrowsWriteError() throws IOException {
        var blocked = Files.createFile(tempDir.resolve("blocked"));
        var adapter = new FileStoreAdapter(blocked);

        assertEvolutionThrows(() -> adapter.saveStates(List.of(statesFixture())),
                EvolutionException.Code.WRITE_ERROR);
    }

    @Test
    @DisplayName("The loaded list is immutable")
    void loadedListIsImmutable() {
        var adapter = new FileStoreAdapter(tempDir.resolve("out"));
        adapter.saveStates(List.of(statesFixture()));

        var loaded = adapter.loadStates();

        assertThatThrownBy(() -> loaded.add(statesFixture()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
