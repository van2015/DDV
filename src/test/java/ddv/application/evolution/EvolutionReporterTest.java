package ddv.application.evolution;

import ddv.domain.model.ClassTrajectory;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.Commit;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.ports.RendererPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvolutionReporterTest {

    private static final Commit C2 = Commit.of("c2c2c2c2c2c2");
    private static final Commit C3 = Commit.of("c3c3c3c3c3c3");
    private static final Commit C5 = Commit.of("c5c5c5c5c5c5");

    private final RendererPort rendererPort = mock(RendererPort.class);
    private final EvolutionReporter reporter = new EvolutionReporter(rendererPort);

    private static MemberStructure method(String name) {
        return new MemberStructure(name, MemberKind.METHOD, List.of());
    }

    private static ClassTrajectory.Point point(Commit commit, ChangeKind kind,
            List<MemberStructure> added, List<MemberStructure> removed, List<String> addedDeps) {
        var change = new StructuralChange.ClassChange("A", kind, added, removed, addedDeps,
                List.of());
        return new ClassTrajectory.Point(commit, change);
    }

    private static ClassTrajectory trajectory() {
        return new ClassTrajectory("A", List.of(
                point(C2, ChangeKind.ADDED, List.of(method("oldMethod")), List.of(), List.of()),
                point(C3, ChangeKind.MODIFIED, List.of(method("newMethod")), List.of(),
                        List.of("java.util.List")),
                point(C5, ChangeKind.REMOVED, List.of(),
                        List.of(method("oldMethod"), method("newMethod")), List.of())));
    }

    @Test
    @DisplayName("The summary lists every point with its state, members and dependencies")
    void summaryListsEveryPointWithItsChanges() {
        var text = reporter.summary(trajectory());

        assertThat(text)
                .contains("Evolución de la clase A (3 puntos temporales)")
                .contains("• c2c2c2c2 — añadida")
                .contains("+ oldMethod()")
                .contains("• c3c3c3c3 — modificada")
                .contains("+ newMethod()")
                .contains("+ java.util.List")
                .contains("• c5c5c5c5 — eliminada")
                .contains("- oldMethod()")
                .contains("- newMethod()");
    }

    @Test
    @DisplayName("The diagram delegates to the injected renderer")
    void diagramDelegatesToRenderer() {
        var trajectory = trajectory();
        when(rendererPort.renderTrajectory(trajectory)).thenReturn("diagrama");

        assertThat(reporter.diagram(trajectory)).isEqualTo("diagrama");

        verify(rendererPort).renderTrajectory(trajectory);
    }
}
