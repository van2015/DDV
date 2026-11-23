package ddv.ports;

import ddv.domain.exception.ParseException;
import ddv.domain.model.CodeStructure;

/**
 * Contrato de parseo de código fuente: los adaptadores (p. ej. JavaParser) lo
 * implementan; el núcleo solo conoce esta interfaz y {@link CodeStructure}.
 *
 * <p>Los adaptadores traducen errores de sintaxis a {@link ParseException} con
 * {@link ParseException.Code#SYNTAX_ERROR}.</p>
 */
public interface ParserPort {

    /**
     * Estructura declarada por un archivo fuente, obtenida sin ejecutar el código.
     *
     * @param path   ruta del archivo; solo se usa para los mensajes de error
     * @param source código fuente completo (vacío → estructura vacía)
     */
    CodeStructure parse(String path, String source);
}
