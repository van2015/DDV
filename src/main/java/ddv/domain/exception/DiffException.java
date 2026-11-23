package ddv.domain.exception;

/**
 * Excepción tipada del dominio para la detección de diferencias (UC2).
 *
 * <p>Separada de {@link ScopeException}, que describe problemas del alcance del
 * repositorio (UC1): cada excepción nombra la preocupación que representa.
 * Porta un {@link Code} para que el pipeline traduzca el error sin parsear
 * texto.</p>
 */
public class DiffException extends RuntimeException {

    /** Causas posibles de fallo al extraer una Δ. */
    public enum Code {
        /** Entradas inválidas (ruta vacía, secuencia desalineada). */
        INVALID_ARGUMENT,
        /** Fallo de lectura del repositorio al calcular el diff. */
        IO_ERROR,
        /** Tipo de cambio no soportado por el MVP (p. ej. submódulo). */
        UNSUPPORTED
    }

    private final Code code;

    public DiffException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public DiffException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
