package ddv.adapters.out.jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;

/** Repositorios de prueba compartidos entre los tests del adaptador JGit. */
final class GitFixtures {

    private GitFixtures() {
    }

    static Path initRepo(Path parent, String name) throws Exception {
        var dir = Files.createDirectory(parent.resolve(name));
        try (var git = Git.init().setDirectory(dir.toFile()).call()) {
            return dir;
        }
    }

    static Path initRepoWithSingleCommit(Path parent, String name) throws Exception {
        var dir = initRepo(parent, name);
        commit(dir, "initial commit", Instant.parse("2026-01-01T00:00:00Z"));
        return dir;
    }

    /** Añade una línea a un archivo versionado y confirma con fecha determinista; devuelve el SHA. */
    static String commit(Path repo, String message, Instant when) throws Exception {
        Files.writeString(repo.resolve("log.txt"), message + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        try (var git = Git.open(repo.toFile())) {
            git.add().addFilepattern(".").call();
            var identity = new PersonIdent("ddv-test", "ddv-test@example.com",
                    Date.from(when), TimeZone.getTimeZone("UTC"));
            return git.commit()
                    .setMessage(message)
                    .setAuthor(identity)
                    .setCommitter(identity)
                    .call()
                    .getName();
        }
    }
}
