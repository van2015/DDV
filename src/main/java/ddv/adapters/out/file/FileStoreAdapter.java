package ddv.adapters.out.file;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import ddv.domain.exception.DiagramException;
import ddv.domain.exception.EvolutionException;
import ddv.domain.model.StructuralChange;
import ddv.ports.StorePort;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * {@link StorePort} de filesystem: cada diagrama como {@code <commitId>.md} y
 * todos los estados $S_1..S_n$ en un único {@code states.json}, bajo un
 * directorio raíz que se crea si no existe.
 */
public class FileStoreAdapter implements StorePort {

    private static final String EXTENSION = ".md";
    private static final String STATES_FILE = "states.json";

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .setPrettyPrinting()
            .create();
    private static final Type STATES_TYPE =
            new TypeToken<List<StructuralChange>>() { }.getType();

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

    @Override
    public void saveStates(List<StructuralChange> states) {
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve(STATES_FILE), GSON.toJson(states),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new EvolutionException(EvolutionException.Code.WRITE_ERROR,
                    "No se pudieron escribir los estados en " + root, e);
        }
    }

    @Override
    public List<StructuralChange> loadStates() {
        var file = root.resolve(STATES_FILE);
        if (!Files.exists(file)) {
            throw new EvolutionException(EvolutionException.Code.READ_ERROR,
                    "No hay estados guardados en " + file);
        }
        try {
            var states = GSON.<List<StructuralChange>>fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), STATES_TYPE);
            if (states == null) {
                throw new EvolutionException(EvolutionException.Code.READ_ERROR,
                        "El archivo de estados está vacío: " + file);
            }
            return List.copyOf(states);
        } catch (IOException | JsonParseException e) {
            throw new EvolutionException(EvolutionException.Code.READ_ERROR,
                    "No se pudieron leer los estados de " + file, e);
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

    /** Gson no trae adaptador para Instant: se persiste en ISO-8601. */
    private static final class InstantAdapter extends TypeAdapter<Instant> {

        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            return Instant.parse(in.nextString());
        }
    }
}
