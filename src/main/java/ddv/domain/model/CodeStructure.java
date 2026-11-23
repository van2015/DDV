package ddv.domain.model;

import ddv.domain.exception.ParseException;

import java.util.List;
import java.util.Objects;

import static ddv.domain.exception.ParseException.requireNotBlank;

/**
 * Instantánea de la estructura de un archivo fuente — salida del parser (UC3).
 *
 * <p>Representación neutra, obtenida sin ejecutar el código: qué clases declara
 * el archivo, qué miembros tienen y qué tipos usan. Es la entrada de la resta
 * viejo vs. nuevo que produce {@link StructuralChange}.</p>
 *
 * @param imports declaraciones {@code import} del archivo
 * @param classes clases, interfaces, enums, records y anotaciones declarados
 */
public record CodeStructure(List<String> imports, List<ClassStructure> classes) {

    public CodeStructure {
        Objects.requireNonNull(imports, "Los imports no pueden ser nulos");
        Objects.requireNonNull(classes, "Las clases no pueden ser nulas");
        imports = List.copyOf(imports);
        classes = List.copyOf(classes);
    }

    /**
     * @param name       nombre calificado dentro del archivo ({@code Outer.Inner})
     * @param kind       tipo de estructura declarada
     * @param superTypes tipos de las cláusulas {@code extends}/{@code implements}
     * @param members    métodos y atributos de la clase
     * @param typeUsages tipos usados en firmas, atributos y cuerpos (sin herencia)
     */
    public record ClassStructure(String name, ClassKind kind, List<String> superTypes,
            List<MemberStructure> members, List<String> typeUsages) {

        public ClassStructure {
            requireNotBlank(name, "El nombre de la clase no puede estar vacío");
            Objects.requireNonNull(kind, "El tipo de clase no puede ser nulo");
            Objects.requireNonNull(superTypes, "Los super tipos no pueden ser nulos");
            Objects.requireNonNull(members, "Los miembros no pueden ser nulos");
            Objects.requireNonNull(typeUsages, "Los tipos usados no pueden ser nulos");
            superTypes = List.copyOf(superTypes);
            members = List.copyOf(members);
            typeUsages = List.copyOf(typeUsages);
        }
    }

    /**
     * @param name           nombre del método o atributo
     * @param parameterTypes tipos de los parámetros (vacío para atributos)
     */
    public record MemberStructure(String name, MemberKind kind, List<String> parameterTypes) {

        public MemberStructure {
            requireNotBlank(name, "El nombre del miembro no puede estar vacío");
            Objects.requireNonNull(kind, "El tipo de miembro no puede ser nulo");
            Objects.requireNonNull(parameterTypes, "Los parámetros no pueden ser nulos");
            parameterTypes = List.copyOf(parameterTypes);
        }
    }

    public enum ClassKind {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD,
        ANNOTATION
    }

    public enum MemberKind {
        METHOD,
        FIELD
    }
}
