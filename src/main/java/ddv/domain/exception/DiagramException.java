package ddv.domain.exception;

/**
 * Excepción tipada del dominio para la generación de diagramas (UC4).
 *
 * <p>Separa los fallos de diagrama de {@link ScopeException} (alcance, UC1),
 * {@link DiffException} (Δ, UC2) y {@link ParseException} (sintaxis, UC3): cada
 * excepción nombra la preocupación que representa. Porta un {@link Code} para
 * que el pipeline traduzca el error sin parsear texto.</p>
 */
public class DiagramException extends RuntimeException {

    /** Causas posibles de fallo al generar o exportar un diagrama. */
    public enum Code {
        /** Entradas inválidas del diagrama (nombres o aristas en blanco). */
        INVALID_INPUT,
        /** No se pudo escribir el diagrama en disco. */
        WRITE_ERROR
    }

    private final Code code;

    public DiagramException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public DiagramException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
