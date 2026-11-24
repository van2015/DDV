package ddv.application.pipeline;

import ddv.adapters.out.ast.AstParserAdapter;
import ddv.adapters.out.jgit.GitFixtures;
import ddv.adapters.out.jgit.JGitAdapter;
import ddv.application.scope.ScopeResolver;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.domain.model.RepoScope;
import ddv.domain.model.StructuralChange;
import ddv.domain.model.StructuralChange.ChangeKind;
import ddv.domain.model.StructuralChange.FileStructureChange;
import ddv.domain.model.StructuralSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: UC1 (ScopeResolver) + UC2 (DiffExtractor) + UC3
 * (StructuralAnalyzer) con JGit real y JavaParser real sobre un repositorio
 * temporal cuyas clases evolucionan — sin mocks en ningún punto del camino.
 */
class StructuralAnalyzerE2ETest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant T3 = Instant.parse("2026-01-03T00:00:00Z");
    private static final Instant T4 = Instant.parse("2026-01-04T00:00:00Z");
    private static final Instant T5 = Instant.parse("2026-01-05T00:00:00Z");

    private final JGitAdapter git = new JGitAdapter();
    private final ScopeResolver scopeResolver = new ScopeResolver(git);
    private final DiffExtractor extractor = new DiffExtractor(git, SourceFileFilter.javaFiles());
    private final StructuralAnalyzer analyzer = new StructuralAnalyzer(
            git, new AstParserAdapter(), SourceFileFilter.javaFiles());

    @TempDir
    Path tempDir;

    private static FileStructureChange fileChangeOf(StructuralSequence sequence, int index,
            String path) {
        return sequence.changes().get(index).files().stream()
                .filter(file -> file.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + path + " in the Sᵢ of delta " + index));
    }

    @Test
    @DisplayName("The full chain reports added, modified and removed classes")
    void fullChainReportsClassLifecycle() throws Exception {
        var repo = historyWithEvolvingClass();

        var scope = scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10));
        var deltas = extractor.extract(scope);
        var sequence = analyzer.analyze(deltas);

        assertThat(scope.size()).isEqualTo(5);
        assertThat(deltas.size()).isEqualTo(4);
        assertThat(sequence.size()).isEqualTo(4);

        var added = fileChangeOf(sequence, 0, "src/A.java");
        assertThat(added.classes()).singleElement().satisfies(classChange -> {
            assertThat(classChange.name()).isEqualTo("A");
            assertThat(classChange.kind()).isEqualTo(ChangeKind.ADDED);
            assertThat(classChange.addedMembers())
                    .extracting(MemberStructure::name)
                    .containsExactly("oldMethod");
        });

        var modified = fileChangeOf(sequence, 1, "src/A.java");
        assertThat(modified.classes()).singleElement().satisfies(classChange -> {
            assertThat(classChange.kind()).isEqualTo(ChangeKind.MODIFIED);
            assertThat(classChange.addedMembers())
                    .extracting(MemberStructure::name)
                    .containsExactly("newMethod");
        });

        var removed = fileChangeOf(sequence, 3, "src/A.java");
        assertThat(removed.classes()).singleElement().satisfies(classChange -> {
            assertThat(classChange.kind()).isEqualTo(ChangeKind.REMOVED);
            assertThat(classChange.removedMembers())
                    .extracting(MemberStructure::name)
                    .containsExactlyInAnyOrder("names", "oldMethod", "newMethod");
        });
    }

    @Test
    @DisplayName("A new import and field are reported as members and dependencies")
    void newImportAndFieldAreReported() throws Exception {
        var repo = historyWithEvolvingClass();

        var sequence = analyzer.analyze(
                extractor.extract(scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10))));

        var withImport = fileChangeOf(sequence, 2, "src/A.java");
        assertThat(withImport.classes()).singleElement().satisfies(classChange -> {
            assertThat(classChange.kind()).isEqualTo(ChangeKind.MODIFIED);
            assertThat(classChange.addedMembers())
                    .extracting(MemberStructure::name)
                    .contains("names");
            assertThat(classChange.addedDependencies())
                    .contains("java.util.List", "List");
        });
    }

    @Test
    @DisplayName("Only Java files reach the structural analysis")
    void onlyJavaFilesReachTheAnalysis() throws Exception {
        var repo = historyWithEvolvingClass();

        var sequence = analyzer.analyze(
                extractor.extract(scopeResolver.resolve(RepoScope.lastN(repo.toString(), 10))));

        assertThat(sequence.changes())
                .flatMap(StructuralChange::files)
                .allSatisfy(file -> assertThat(file.path()).endsWith(".java"));
    }

    /** c1 (log) → c2 (+A) → c3 (+newMethod) → c4 (+import, +campo) → c5 (−A). */
    private Path historyWithEvolvingClass() throws Exception {
        var repo = GitFixtures.initRepo(tempDir, "repo");
        GitFixtures.commit(repo, "c1", T1);
        GitFixtures.write(repo, "src/A.java", "class A { void oldMethod() {} }");
        GitFixtures.commit(repo, "c2", T2);
        GitFixtures.write(repo, "src/A.java",
                "class A { void oldMethod() {} void newMethod() {} }");
        GitFixtures.commit(repo, "c3", T3);
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
        return repo;
    }
}
