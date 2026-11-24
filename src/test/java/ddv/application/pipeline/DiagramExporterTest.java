package ddv.application.pipeline;

import ddv.domain.exception.DiagramException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.DeltaSequence;
import ddv.domain.model.DiagramSequence;
import ddv.domain.model.DiagramSpec;
import ddv.domain.model.RepoScope;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralSequence;
import ddv.ports.RendererPort;
import ddv.ports.StorePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertDiagramThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagramExporterTest {

    private final RendererPort rendererPort = mock(RendererPort.class);
    private final StorePort storePort = mock(StorePort.class);
    private final DiagramExporter exporter = new DiagramExporter(rendererPort, storePort);

    private static DiagramSpec spec(String from, String to) {
        return new DiagramSpec(Commit.of(from), Commit.of(to), List.of(), List.of());
    }

    /** Secuencia válida encadenando los commits de los diagramas dados. */
    private static DiagramSequence sequenceOf(DiagramSpec... diagrams) {
        var commits = new ArrayList<Commit>();
        commits.add(diagrams[0].previous());
        for (var diagram : diagrams) {
            commits.add(diagram.current());
        }
        var deltas = new ArrayList<CommitDelta>();
        var changes = new ArrayList<StructuralChange>();
        var previous = diagrams[0].previous();
        for (var diagram : diagrams) {
            var delta = new CommitDelta(previous, diagram.current(), List.of());
            deltas.add(delta);
            changes.add(new StructuralChange(delta, List.of()));
            previous = diagram.current();
        }
        var structural = new StructuralSequence(
                new DeltaSequence(new AnalysisScope(
                        RepoScope.lastN("/repo", commits.size()), List.copyOf(commits)), deltas),
                changes);
        return new DiagramSequence(structural, List.of(diagrams));
    }

    @Test
    @DisplayName("Renders every diagram and stores it under its commit id, in order")
    void rendersAndStoresEveryDiagramInOrder() {
        var sequence = sequenceOf(spec("c1", "c2"), spec("c2", "c3"));
        when(rendererPort.render(any())).thenAnswer(invocation -> {
            var diagram = (DiagramSpec) invocation.getArgument(0);
            return "doc-" + diagram.current().id();
        });
        when(storePort.saveDiagram(anyString(), anyString()))
                .thenReturn(Path.of("out/diagrams"));

        var paths = exporter.export(sequence);

        assertThat(paths).hasSize(2);
        var order = inOrder(rendererPort, storePort);
        order.verify(rendererPort).render(any());
        order.verify(storePort).saveDiagram(eq("c2"), eq("doc-c2"));
        order.verify(rendererPort).render(any());
        order.verify(storePort).saveDiagram(eq("c3"), eq("doc-c3"));
        verify(storePort).saveStates(any());
    }

    @Test
    @DisplayName("A store failure propagates as WRITE_ERROR")
    void storeFailurePropagates() {
        var sequence = sequenceOf(spec("c1", "c2"));
        when(rendererPort.render(any())).thenReturn("document");
        when(storePort.saveDiagram(anyString(), anyString()))
                .thenThrow(new DiagramException(DiagramException.Code.WRITE_ERROR, "disk full"));

        assertDiagramThrows(() -> exporter.export(sequence),
                DiagramException.Code.WRITE_ERROR);
    }

    @Test
    @DisplayName("An empty sequence exports nothing")
    void emptySequenceExportsNothing() {
        var single = new AnalysisScope(RepoScope.lastN("/repo", 1), List.of(Commit.of("c1")));
        var sequence = new DiagramSequence(
                new StructuralSequence(new DeltaSequence(single, List.of()), List.of()),
                List.of());

        var paths = exporter.export(sequence);

        assertThat(paths).isEmpty();
        verify(rendererPort, never()).render(any());
        verify(storePort, never()).saveDiagram(anyString(), anyString());
        verify(storePort).saveStates(List.of());
    }
}
