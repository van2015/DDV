package ddv.application.evolution;

import ddv.domain.exception.EvolutionException;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.ports.StorePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertEvolutionThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvolutionComparerTest {

    private static final Commit C2 = Commit.of("c2c2c2c2c2c2");
    private static final Commit C3 = Commit.of("c3c3c3c3c3c3");
    private static final Commit C4 = Commit.of("c4c4c4c4c4c4");

    private final StorePort storePort = mock(StorePort.class);
    private final EvolutionComparer comparer = new EvolutionComparer(storePort);

    private static StructuralChange stateOf(Commit previous, Commit current, String className,
            ChangeKind kind, String... members) {
        var delta = new CommitDelta(previous, current, List.of(
                new CommitDelta.FileChange("src/" + className + ".java",
                        CommitDelta.ChangeType.MODIFIED)));
        var classMembers = Arrays.stream(members)
                .map(member -> new MemberStructure(member, MemberKind.METHOD, List.of()))
                .toList();
        var classChange = new StructuralChange.ClassChange(className, kind, classMembers,
                List.of(), List.of(), List.of());
        return new StructuralChange(delta, List.of(
                new StructuralChange.FileStructureChange("src/" + className + ".java",
                        List.of(classChange))));
    }

    @Test
    @DisplayName("Collects the points of the class in chronological order, skipping others")
    void collectsPointsInOrderSkippingOtherClasses() {
        when(storePort.loadStates()).thenReturn(List.of(
                stateOf(Commit.of("c1"), C2, "A", ChangeKind.ADDED, "oldMethod"),
                stateOf(C2, C3, "B", ChangeKind.ADDED, "other"),
                stateOf(C3, C4, "A", ChangeKind.MODIFIED, "newMethod")));

        var trajectory = comparer.compare("A");

        assertThat(trajectory.className()).isEqualTo("A");
        assertThat(trajectory.points())
                .extracting(point -> point.commit().id())
                .containsExactly(C2.id(), C4.id());
        assertThat(trajectory.points())
                .extracting(point -> point.change().kind())
                .containsExactly(ChangeKind.ADDED, ChangeKind.MODIFIED);
        assertThat(trajectory.points().get(0).change().addedMembers())
                .extracting(MemberStructure::name)
                .containsExactly("oldMethod");
    }

    @Test
    @DisplayName("A class absent from the whole range throws CLASS_NOT_FOUND")
    void absentClassThrowsClassNotFound() {
        when(storePort.loadStates()).thenReturn(List.of(
                stateOf(Commit.of("c1"), C2, "B", ChangeKind.ADDED, "other")));

        assertEvolutionThrows(() -> comparer.compare("A"),
                EvolutionException.Code.CLASS_NOT_FOUND);
    }

    @Test
    @DisplayName("A blank class name throws INVALID_INPUT before reading any state")
    void blankNameThrowsBeforeReadingStates() {
        assertEvolutionThrows(() -> comparer.compare("  "),
                EvolutionException.Code.INVALID_INPUT);

        verify(storePort, never()).loadStates();
    }

    @Test
    @DisplayName("The class name is matched trimmed")
    void classNameIsMatchedTrimmed() {
        when(storePort.loadStates()).thenReturn(List.of(
                stateOf(Commit.of("c1"), C2, "A", ChangeKind.ADDED)));

        var trajectory = comparer.compare("  A  ");

        assertThat(trajectory.className()).isEqualTo("A");
        assertThat(trajectory.points()).hasSize(1);
    }
}
