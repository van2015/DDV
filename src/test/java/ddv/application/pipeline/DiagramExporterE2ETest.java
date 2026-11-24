package ddv.application.pipeline;

import ddv.adapters.out.ast.AstParserAdapter;
import ddv.adapters.out.file.FileStoreAdapter;
import ddv.adapters.out.jgit.GitFixtures;
import ddv.adapters.out.jgit.JGitAdapter;
import ddv.adapters.out.mermaid.MermaidRenderer;
import ddv.application.scope.ScopeResolver;
import ddv.domain.model.RepoScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: UC1 (ScopeResolver) → UC2 (DiffExtractor) → UC3
 * (StructuralAnalyzer) → UC4 (DiagramGenerator + DiagramExporter) con JGit,
 * JavaParser, Mermaid y filesystem reales — sin mocks en ningún punto.
 */
class DiagramExporterE2ETest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-01-04T00:00:00Z");
    private static final Instant T5 = Instant.parse("2026-01-05T00:00:00Z");
    private static final Instant T6 = Instant.parse("2026-01-06T00:00:00Z");

    private final JGitAdapter git = new JGitAdapter();
    private final ScopeResolver scopeResolver = new ScopeResolver(git);
    private final DiffExtractor extractor = new DiffExtractor(git, SourceFileFilter.javaFiles());
    private final StructuralAnalyzer analyzer = new StructuralAnalyzer(
            git, new AstParserAdapter(), SourceFileFilter.javaFiles());
    private final DiagramGenerator generator = new DiagramGenerator();

    @TempDir
    Path tempDir;

    private Path output;
    private List<Path> paths;
    private String addedCommit;
    private String modifiedCommit;
    private String placeholderCommit;

    @BeforeEach
    void exportFullChain() throws Exception {
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
        GitFixtures.commit(repo, "c4", T4);
        GitFixtures.remove(repo, "src/A.java");
        GitFixtures.commit(repo, "c5", T5);
        placeholderCommit = GitFixtures.commit(repo, "c6", T6); // solo toca log.txt

        output = tempDir.resolve("diagrams");
        var exporter = new DiagramExporter(new MermaidRenderer(), new FileStoreAdapter(output));
        var scope = scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10));
        var sequence = generator.generate(analyzer.analyze(extractor.extract(scope)));
        paths = exporter.export(sequence);
    }

    @Test
    @DisplayName("The full chain writes one markdown file per commit")
    void fullChainWritesOneMarkdownPerCommit() throws Exception {
        assertThat(paths).hasSize(5).allMatch(path -> output.equals(path.getParent()));

        try (var files = Files.list(output)) {
            assertThat(files.filter(path -> path.toString().endsWith(".md"))).hasSize(5);
        }
    }

    @Test
    @DisplayName("A commit that adds a class renders its mermaid diagram")
    void addedClassRendersMermaidDiagram() throws Exception {
        var document = readDiagram(addedCommit);

        assertThat(document)
                .startsWith("# Diagrama estructural — " + shortId(addedCommit))
                .contains("```mermaid")
                .contains("class A{")
                .contains("<<added>>");
    }

    @Test
    @DisplayName("A commit that adds a method renders it as a modified class")
    void modifiedClassRendersNewMember() throws Exception {
        var document = readDiagram(modifiedCommit);

        assertThat(document)
                .contains("<<modified>>")
                .contains("newMethod()");
    }

    @Test
    @DisplayName("A commit without structural changes writes the placeholder")
    void commitWithoutChangesWritesPlaceholder() throws Exception {
        var document = readDiagram(placeholderCommit);

        assertThat(document)
                .startsWith("# Diagrama estructural — " + shortId(placeholderCommit))
                .contains("Sin cambios estructurales en este commit.")
                .doesNotContain("```mermaid");
    }

    private String readDiagram(String commitId) throws IOException {
        return Files.readString(output.resolve(shortId(commitId) + ".md"),
                StandardCharsets.UTF_8);
    }

    private static String shortId(String commitId) {
        return commitId.substring(0, 8);
    }
}
