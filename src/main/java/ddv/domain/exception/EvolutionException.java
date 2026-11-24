package ddv.domain.exception;

/**
 * Excepción tipada del dominio para la comparación de evolución (UC5).
 *
 * <p>Separa los fallos de UC5 de {@link ScopeException} (alcance, UC1),
 * {@link DiffException} (Δ, UC2), {@link ParseException} (sintaxis, UC3) y
 * {@link DiagramException} (diagramas, UC4): cada excepción nombra la
 * preocupación que representa. Porta un {@link Code} para que la CLI traduzca
 * el error sin parsear texto.</p>
 */
public class EvolutionException extends RuntimeException {

    /** Causas posibles de fallo al comparar la evolución. */
    public enum Code {
        /** Entradas inválidas (nombre de clase en blanco o trayectoria vacía). */
        INVALID_INPUT,
        /** La clase seleccionada no aparece en ningún commit del rango. */
        CLASS_NOT_FOUND,
        /** No se pudieron escribir los estados en disco. */
        WRITE_ERROR,
        /** No se pudieron leer los estados guardados. */
        READ_ERROR
    }

    private final Code code;

    public EvolutionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public EvolutionException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
