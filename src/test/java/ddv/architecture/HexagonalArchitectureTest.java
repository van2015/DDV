package ddv.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces AGENTS.md rule 2: the core (domain, application, ports) must never
 * depend on frameworks or adapters.
 *
 * <p>Scans compiled classes (not sources) so Javadoc/comment mentions do not
 * count as dependencies — only real bytecode references do.</p>
 */
class HexagonalArchitectureTest {

    private static final List<String> CORE_OUTPUT_DIRS = List.of(
            "target/classes/ddv/domain",
            "target/classes/ddv/application",
            "target/classes/ddv/ports");

    /** Class-file references use '/' separators (e.g. org/eclipse/jgit/Repository). */
    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "org/eclipse/jgit",
            "picocli",
            "com/github/javaparser",
            "ddv/adapters");

    @Test
    @DisplayName("Core classes must not reference frameworks or adapters")
    void coreMustNotDependOnFrameworksOrAdapters() throws IOException {
        var violations = new ArrayList<String>();

        for (var outputDir : CORE_OUTPUT_DIRS) {
            var root = Path.of(outputDir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> classes = Files.walk(root)) {
                for (var classFile : classes.filter(p -> p.toString().endsWith(".class")).toList()) {
                    var bytes = Files.readAllBytes(classFile);
                    var content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                    for (var reference : FORBIDDEN_REFERENCES) {
                        if (content.contains(reference)) {
                            violations.add(classFile + " references " + reference);
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("Hexagonal rule violation: core must not depend on frameworks/adapters")
                .isEmpty();
    }
}
