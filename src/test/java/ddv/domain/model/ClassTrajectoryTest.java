package ddv.domain.model;

import ddv.domain.exception.EvolutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertEvolutionThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassTrajectoryTest {

    private static final Commit C1 = Commit.of("c1c1c1c1c1c1");
    private static final StructuralChange.ClassChange CHANGE = new StructuralChange.ClassChange(
            "A", StructuralChange.ChangeKind.ADDED, List.of(), List.of(), List.of(), List.of());

    private static ClassTrajectory.Point point() {
        return new ClassTrajectory.Point(C1, CHANGE);
    }

    @Test
    @DisplayName("A blank class name throws INVALID_INPUT")
    void blankClassNameThrowsInvalidInput() {
        assertEvolutionThrows(() -> new ClassTrajectory("  ", List.of(point())),
                EvolutionException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("A null point list throws NullPointerException")
    void nullPointsThrowNullPointerException() {
        assertThatThrownBy(() -> new ClassTrajectory("A", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("An empty trajectory throws INVALID_INPUT")
    void emptyTrajectoryThrowsInvalidInput() {
        assertEvolutionThrows(() -> new ClassTrajectory("A", List.of()),
                EvolutionException.Code.INVALID_INPUT);
    }

    @Test
    @DisplayName("Null point components throw NullPointerException")
    void nullPointComponentsThrowNullPointerException() {
        assertThatThrownBy(() -> new ClassTrajectory.Point(null, CHANGE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ClassTrajectory.Point(C1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("The point list is an immutable defensive copy")
    void pointListIsImmutable() {
        var external = new ArrayList<>(List.of(point()));
        var trajectory = new ClassTrajectory("A", external);

        external.clear();

        assertThat(trajectory.points()).hasSize(1);
        assertThatThrownBy(() -> trajectory.points().add(point()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Trajectories are compared by value")
    void trajectoriesAreComparedByValue() {
        var first = new ClassTrajectory("A", List.of(point()));
        var second = new ClassTrajectory("A", List.of(point()));

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    @DisplayName("The class name is trimmed")
    void classNameIsTrimmed() {
        assertThat(new ClassTrajectory("  A  ", List.of(point())).className())
                .isEqualTo("A");
    }
}
