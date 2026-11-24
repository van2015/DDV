package ddv.adapters.out.file;

import ddv.domain.exception.DiagramException;
import ddv.ports.StorePort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link StorePort} que escribe cada diagrama como {@code <commitId>.md} bajo
 * un directorio raíz, creándolo si no existe.
 */
public class FileStoreAdapter implements StorePort {

    private static final String EXTENSION = ".md";

    private final Path root;

    public FileStoreAdapter(Path root) {
        this.root = root;
    }

    @Override
    public Path saveDiagram(String commitId, String document) {
        requireValidName(commitId);
        var target = root.resolve(commitId + EXTENSION);
        try {
            Files.createDirectories(root);
            return Files.writeString(target, document, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DiagramException(DiagramException.Code.WRITE_ERROR,
                    "No se pudo escribir el diagrama en " + target, e);
        }
    }

    // Path.resolve acepta rutas absolutas (ignorando la raíz) y separadores:
    // un commitId con esos caracteres saldría del directorio de salida
    private static void requireValidName(String commitId) {
        if (commitId == null || commitId.isBlank()
                || commitId.contains("/") || commitId.contains("\\")) {
            throw new DiagramException(DiagramException.Code.INVALID_INPUT,
                    "Identificador de commit no válido como nombre de archivo: " + commitId);
        }
    }
}
