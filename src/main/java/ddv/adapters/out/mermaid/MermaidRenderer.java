package ddv.adapters.out.mermaid;

import ddv.domain.model.ClassTrajectory;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.DiagramSpec;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.ports.RendererPort;

import java.util.stream.Collectors;

/**
 * {@link RendererPort} que emite un documento Markdown con un bloque Mermaid
 * {@code classDiagram}.
 *
 * <p>Sin visibilidad en los miembros —el modelo no la conoce y en Mermaid es
 * opcional— y con sanitizado: Mermaid admite solo alfanuméricos, {@code _} y
 * {@code -} en nombres de clase y usa {@code ~} para genéricos.</p>
 */
public class MermaidRenderer implements RendererPort {

    private static final String PLACEHOLDER = "Sin cambios estructurales en este commit.";

    @Override
    public String render(DiagramSpec spec) {
        var title = title(spec);
        if (spec.nodes().isEmpty() && spec.edges().isEmpty()) {
            return title + "\n\n" + PLACEHOLDER + "\n";
        }
        return title + "\n\n```mermaid\n" + classDiagram(spec) + "```\n";
    }

    @Override
    public String renderTrajectory(ClassTrajectory trajectory) {
        var points = trajectory.points();
        var diagram = new StringBuilder("```mermaid\nflowchart LR\n");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                diagram.append(" -->\n");
            }
            diagram.append("    ").append(node(points.get(i)));
        }
        return "# Trayectoria de " + trajectory.className() + "\n\n"
                + diagram + "\n```\n";
    }

    private static String node(ClassTrajectory.Point point) {
        var id = sanitize(point.commit().shortId());
        var label = new StringBuilder(id).append(" · ")
                .append(point.change().kind().label());
        point.change().addedMembers()
                .forEach(member -> label.append("<br/>+ ").append(member(member)));
        point.change().removedMembers()
                .forEach(member -> label.append("<br/>- ").append(member(member)));
        point.change().addedDependencies()
                .forEach(dependency -> label.append("<br/>+ ").append(sanitize(dependency)));
        point.change().removedDependencies()
                .forEach(dependency -> label.append("<br/>- ").append(sanitize(dependency)));
        return id + "[\"" + label + "\"]";
    }

    private static String title(DiagramSpec spec) {
        var message = spec.current().message();
        return "# Diagrama estructural — " + spec.current().shortId()
                + (message.isBlank() ? "" : ": " + message);
    }

    private static String classDiagram(DiagramSpec spec) {
        var diagram = new StringBuilder("classDiagram\n");
        for (var node : spec.nodes()) {
            appendNode(diagram, node);
        }
        for (var edge : spec.edges()) {
            diagram.append("    ").append(sanitize(edge.source()))
                    .append(" ..> ").append(sanitize(edge.target()))
                    .append(" : usa\n");
        }
        return diagram.toString();
    }

    private static void appendNode(StringBuilder diagram, DiagramSpec.Node node) {
        diagram.append("    class ").append(sanitize(node.name())).append("{\n");
        diagram.append("        <<").append(stereotype(node.state())).append(">>\n");
        for (var member : node.addedMembers()) {
            diagram.append("        ").append(member(member)).append("\n");
        }
        diagram.append("    }\n");
    }

    private static String stereotype(ChangeKind state) {
        return switch (state) {
            case ADDED -> "added";
            case MODIFIED -> "modified";
            case REMOVED -> "removed";
        };
    }

    private static String member(MemberStructure member) {
        return switch (member.kind()) {
            case METHOD -> member.name() + "(" + parameters(member) + ")";
            case FIELD -> member.name();
        };
    }

    private static String parameters(MemberStructure member) {
        return member.parameterTypes().stream()
                .map(MermaidRenderer::sanitize)
                .collect(Collectors.joining(", "));
    }

    // Mermaid: nombres alfanuméricos con _ y -; genéricos entre ~; sin comas
    private static String sanitize(String text) {
        return text.replace('<', '~').replace('>', '~').replace('.', '_')
                .replace(", ", " ").replace(',', ' ');
    }
}
