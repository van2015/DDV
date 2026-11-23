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

/** Repositorios de prueba compartidos entre los tests del proyecto. */
public final class GitFixtures {

    private GitFixtures() {
    }

    public static Path initRepo(Path parent, String name) throws Exception {
        var dir = Files.createDirectory(parent.resolve(name));
        try (var git = Git.init().setDirectory(dir.toFile()).call()) {
            return dir;
        }
    }

    public static Path initRepoWithSingleCommit(Path parent, String name) throws Exception {
        var dir = initRepo(parent, name);
        commit(dir, "initial commit", Instant.parse("2026-01-01T00:00:00Z"));
        return dir;
    }

    /** Añade una línea a un archivo versionado y confirma con fecha determinista; devuelve el SHA. */
    public static String commit(Path repo, String message, Instant when) throws Exception {
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

    public static void write(Path repo, String path, String content) throws Exception {
        writeBytes(repo, path, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeBytes(Path repo, String path, byte[] content) throws Exception {
        var target = repo.resolve(path);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    /** Borra del índice y del árbol de trabajo (git rm) para que el diff lo vea. */
    public static void remove(Path repo, String path) throws Exception {
        try (var git = Git.open(repo.toFile())) {
            git.rm().addFilepattern(path).call();
        }
    }

    /** Mueve un archivo vía git rm + add: contenido idéntico, detectable como rename. */
    public static void rename(Path repo, String from, String to) throws Exception {
        var content = Files.readAllBytes(repo.resolve(from));
        remove(repo, from);
        writeBytes(repo, to, content);
        try (var git = Git.open(repo.toFile())) {
            git.add().addFilepattern(to).call();
        }
    }
}
