package ddv.domain.model;

import ddv.domain.exception.ParseException;
import ddv.domain.model.CodeStructure.MemberStructure;

import java.util.List;
import java.util.Objects;

import static ddv.domain.exception.ParseException.requireNotBlank;

/**
 * $S_i$ — cambio estructural de un commit: salida del UC3 y entrada de UC4.
 *
 * <p>Agrupa, por archivo del Δ, las clases dadas de alta o de baja, sus miembros
 * añadidos o eliminados y las dependencias que aparecen o desaparecen. Los
 * ficheros analizados se listan aunque no haya cambios estructurales, para
 * conservar la trazabilidad con el {@link CommitDelta}.</p>
 *
 * @param files cambios por archivo, en el orden del Δ
 */
public record StructuralChange(List<FileStructureChange> files) {

    public StructuralChange {
        Objects.requireNonNull(files, "La lista de archivos no puede ser nula");
        files = List.copyOf(files);
    }

    public record FileStructureChange(String path, List<ClassChange> classes) {

        public FileStructureChange {
            requireNotBlank(path, "La ruta del archivo no puede estar vacía");
            Objects.requireNonNull(classes, "Las clases no pueden ser nulas");
            classes = List.copyOf(classes);
        }
    }

    /**
     * @param kind               ADDED (clase nueva), REMOVED (clase borrada) o
     *                           MODIFIED (cambió algún miembro o dependencia)
     * @param addedMembers       miembros dados de alta (todos, si la clase es ADDED)
     * @param removedMembers     miembros dados de baja (todos, si la clase es REMOVED)
     * @param addedDependencies  tipos que la clase pasa a usar
     * @param removedDependencies tipos que la clase deja de usar
     */
    public record ClassChange(String name, ChangeKind kind,
            List<MemberStructure> addedMembers, List<MemberStructure> removedMembers,
            List<String> addedDependencies, List<String> removedDependencies) {

        public ClassChange {
            requireNotBlank(name, "El nombre de la clase no puede estar vacío");
            Objects.requireNonNull(kind, "El tipo de cambio no puede ser nulo");
            Objects.requireNonNull(addedMembers, "Los miembros añadidos no pueden ser nulos");
            Objects.requireNonNull(removedMembers, "Los miembros eliminados no pueden ser nulos");
            Objects.requireNonNull(addedDependencies, "Las dependencias añadidas no pueden ser nulas");
            Objects.requireNonNull(removedDependencies, "Las dependencias eliminadas no pueden ser nulas");
            addedMembers = List.copyOf(addedMembers);
            removedMembers = List.copyOf(removedMembers);
            addedDependencies = List.copyOf(addedDependencies);
            removedDependencies = List.copyOf(removedDependencies);
        }
    }

    public enum ChangeKind {
        ADDED,
        MODIFIED,
        REMOVED
    }
}
