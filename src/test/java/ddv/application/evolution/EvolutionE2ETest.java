package ddv.application.evolution;

import ddv.adapters.out.ast.AstParserAdapter;
import ddv.adapters.out.file.FileStoreAdapter;
import ddv.adapters.out.jgit.GitFixtures;
import ddv.adapters.out.jgit.JGitAdapter;
import ddv.adapters.out.mermaid.MermaidRenderer;
import ddv.application.pipeline.AnalysisRunner;
import ddv.application.pipeline.DiagramExporter;
import ddv.application.pipeline.DiagramGenerator;
import ddv.application.pipeline.DiffExtractor;
import ddv.application.pipeline.SourceFileFilter;
import ddv.application.pipeline.StructuralAnalyzer;
import ddv.application.scope.ScopeResolver;
import ddv.domain.exception.EvolutionException;
import ddv.domain.model.RepoScope;
import ddv.domain.model.StructuralChange.ChangeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static ddv.assertions.DdvAssertions.assertEvolutionThrows;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: UC1 → UC4 con persistencia real (JSON en disco) y después
 * UC5 — recarga desde {@code states.json}, trayectoria de la clase y salida —
 * sin mocks en ningún punto de la cadena.
 */
class EvolutionE2ETest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-01-04T00:00:00Z");
    private static final Instant T5 = Instant.parse("2026-01-05T00:00:00Z");

    private final JGitAdapter git = new JGitAdapter();
    private final ScopeResolver scopeResolver = new ScopeResolver(git);
    private final MermaidRenderer renderer = new MermaidRenderer();

    @TempDir
    Path tempDir;

    private Path output;
    private FileStoreAdapter store;
    private List<Path> exported;
    private String addedCommit;
    private String modifiedCommit;
    private String enrichedCommit;
    private String removedCommit;
    private EvolutionComparer comparer;
    private EvolutionReporter reporter;

    @BeforeEach
    void runPipelineAndPrepareUc5() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A { void oldMethod() {} }");
        addedCommit = GitFixtures.commit(repo, "c2", T2);
        GitFixtures.write(repo, "src/A.java",
                "class A { void oldMethod() {} void newMethod() {} }");
        modifiedCommit = GitFixtures.commit(repo, "c3", T3);
        GitFixtures.write(repo, "src/A.java", """
                import java.util.List;

                class A {
                    List<String> names;
                    void oldMethod() {}
                    void newMethod() {}
                }
                """);
        enrichedCommit = GitFixtures.commit(repo, "c4", T4);
        GitFixtures.remove(repo, "src/A.java");
        removedCommit = GitFixtures.commit(repo, "c5", T5);

        output = tempDir.resolve("ddv-out");
        var filter = SourceFileFilter.javaFiles();
        store = new FileStoreAdapter(output);
        var runner = new AnalysisRunner(
                new DiffExtractor(git, filter),
                new StructuralAnalyzer(git, new AstParserAdapter(), filter),
                new DiagramGenerator(),
                new DiagramExporter(renderer, store));
        var scope = scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10));
        exported = runner.run(scope);

        comparer = new EvolutionComparer(store);
        reporter = new EvolutionReporter(renderer);
    }

    @Test
    @DisplayName("The pipeline persists every state to disk for UC5 to read")
    void pipelinePersistsStatesToDisk() {
        assertThat(exported).hasSize(4);
        assertThat(output.resolve("states.json")).exists();
        assertThat(store.loadStates()).hasSize(4);
    }

    @Test
    @DisplayName("The trajectory is rebuilt from the states reloaded from disk")
    void trajectoryIsRebuiltFromReloadedStates() {
        var trajectory = comparer.compare("A");

        assertThat(trajectory.className()).isEqualTo("A");
        assertThat(trajectory.points())
                .extracting(point -> point.commit().id())
                .containsExactly(addedCommit, modifiedCommit, enrichedCommit, removedCommit);
        assertThat(trajectory.points())
                .extracting(point -> point.change().kind())
                .containsExactly(ChangeKind.ADDED, ChangeKind.MODIFIED, ChangeKind.MODIFIED,
                        ChangeKind.REMOVED);
    }

    @Test
    @DisplayName("The summary and diagram are produced from the reloaded states")
    void summaryAndDiagramComeFromReloadedStates() {
        var trajectory = comparer.compare("A");

        assertThat(reporter.summary(trajectory))
                .contains("Evolución de la clase A (4 puntos temporales)")
                .contains("añadida")
                .contains("+ oldMethod()")
                .contains("+ newMethod()")
                .contains("+ names")
                .contains("+ java.util.List")
                .contains("modificada")
                .contains("eliminada");
        assertThat(reporter.diagram(trajectory))
                .contains("flowchart LR")
                .contains(" -->")
                .contains("añadida");
    }

    @Test
    @DisplayName("A class absent from the whole range throws CLASS_NOT_FOUND")
    void absentClassThrowsClassNotFound() {
        assertEvolutionThrows(() -> comparer.compare("NoExiste"),
                EvolutionException.Code.CLASS_NOT_FOUND);
    }

    @Test
    @DisplayName("Every commit of the range left its markdown on disk")
    void everyCommitLeftItsMarkdownOnDisk() {
        assertThat(exported).allSatisfy(path -> assertThat(path).exists());
    }
}
