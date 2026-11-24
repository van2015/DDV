package ddv.application.evolution;

import ddv.domain.model.ClassTrajectory;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.ports.RendererPort;

/**
 * Salida del caso de uso UC5: resumen textual de la trayectoria y su diagrama
 * (la notación la decide el {@link RendererPort} inyectado).
 */
public class EvolutionReporter {

    private final RendererPort rendererPort;

    public EvolutionReporter(RendererPort rendererPort) {
        this.rendererPort = rendererPort;
    }

    /** Resumen textual: un punto por commit con sus altas y bajas. */
    public String summary(ClassTrajectory trajectory) {
        var text = new StringBuilder("Evolución de la clase ")
                .append(trajectory.className())
                .append(" (").append(trajectory.points().size())
                .append(" puntos temporales)\n");
        for (var point : trajectory.points()) {
            text.append("\n• ").append(point.commit().shortId())
                    .append(" — ").append(point.change().kind().label())
                    .append('\n');
            point.change().addedMembers()
                    .forEach(member -> text.append("    + ").append(label(member)).append('\n'));
            point.change().removedMembers()
                    .forEach(member -> text.append("    - ").append(label(member)).append('\n'));
            point.change().addedDependencies()
                    .forEach(dependency -> text.append("    + ").append(dependency).append('\n'));
            point.change().removedDependencies()
                    .forEach(dependency -> text.append("    - ").append(dependency).append('\n'));
        }
        return text.toString();
    }

    /** Diagrama de trayectoria en la notación del adaptador inyectado. */
    public String diagram(ClassTrajectory trajectory) {
        return rendererPort.renderTrajectory(trajectory);
    }

    private static String label(MemberStructure member) {
        return switch (member.kind()) {
            case METHOD -> member.name() + "(" + String.join(", ", member.parameterTypes()) + ")";
            case FIELD -> member.name();
        };
    }
}
