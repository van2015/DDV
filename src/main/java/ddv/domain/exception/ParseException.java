package ddv.domain.exception;

/**
 * Excepción tipada del dominio para el parseo de código fuente (UC3).
 *
 * <p>Separa los errores de sintaxis de {@link ScopeException} (alcance del
 * repositorio) y de {@link DiffException} (detección de diferencias): cada
 * excepción nombra la preocupación que representa. Porta un {@link Code} para
 * que el pipeline traduzca el error sin parsear texto.</p>
 */
public class ParseException extends RuntimeException {

    /** Causas posibles de fallo al parsear. */
    public enum Code {
        /** El código fuente tiene errores de sintaxis. */
        SYNTAX_ERROR,
        /** Entrada o estructura resultante inválida (vacía o en blanco). */
        INVALID_INPUT
    }

    private final Code code;

    public ParseException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ParseException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    /** Valida un texto del parseo: {@code null} o en blanco es {@code INVALID_INPUT}. */
    public static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ParseException(Code.INVALID_INPUT, message);
        }
    }
}
