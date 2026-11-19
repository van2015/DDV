package ddv.domain.exception;

/**
 * Excepción tipada del dominio para errores de definición y validación del
 * alcance del repositorio (UC1).
 *
 * <p>Porta un {@link Code} para que la capa de aplicación/CLI pueda traducir el
 * error a mensajes o códigos de salida sin parsear texto.</p>
 */
public class ScopeException extends RuntimeException {

    /** Causas posibles de fallo del alcance. */
    public enum Code {
        /** Argumentos inválidos (vacíos, nulos, malformados). */
        INVALID_ARGUMENT,
        /** El repositorio no existe o la ruta/URL es incorrecta. */
        REPO_NOT_FOUND,
        /** El repositorio existe pero no hay acceso (permisos/red). */
        NO_ACCESS,
        /** Un commit del rango (A, B o intermedio) no existe. */
        COMMIT_NOT_FOUND,
        /** A no es ancestro de B en el rango A..B. */
        NOT_ANCESTOR,
        /** El rango de análisis no contiene commits. */
        EMPTY_RANGE
    }

    private final Code code;

    public ScopeException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ScopeException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
