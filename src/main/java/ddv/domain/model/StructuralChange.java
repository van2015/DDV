package ddv.domain.model;

import ddv.domain.exception.ParseException;
import ddv.domain.model.CodeStructure.ClassStructure;
import ddv.domain.model.CodeStructure.MemberStructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static ddv.domain.exception.ParseException.requireNotBlank;

/**
 * $S_i$ — cambio estructural de un commit: salida del UC3 y entrada de UC4.
 *
 * <p>Agrupa, por archivo del Δ, las clases dadas de alta o de baja, sus miembros
 * añadidos o eliminados y las dependencias que aparecen o desaparecen. Los
 * ficheros analizados se listan aunque no haya cambios estructurales, para
 * conservar la trazabilidad con el {@link CommitDelta}.</p>
 *
 * @param delta Δ del que deriva este cambio (procedencia y alineación)
 * @param files cambios por archivo, en el orden del Δ
 */
public record StructuralChange(CommitDelta delta, List<FileStructureChange> files) {

    public StructuralChange {
        Objects.requireNonNull(delta, "La Δ de origen no puede ser nula");
        Objects.requireNonNull(files, "La lista de archivos no puede ser nula");
        files = List.copyOf(files);
    }

    public record FileStructureChange(String path, List<ClassChange> classes) {

        public FileStructureChange {
            requireNotBlank(path, "La ruta del archivo no puede estar vacía");
            Objects.requireNonNull(classes, "Las clases no pueden ser nulas");
            classes = List.copyOf(classes);
        }

        /** Resta la estructura vieja contra la nueva de un archivo. */
        public static FileStructureChange between(String path, CodeStructure before,
                CodeStructure after) {
            return new FileStructureChange(path, classChanges(before, after));
        }

        private static List<ClassChange> classChanges(CodeStructure before, CodeStructure after) {
            var changes = new ArrayList<ClassChange>();
            for (var current : after.classes()) {
                var previous = find(before, current.name());
                if (previous.isEmpty()) {
                    changes.add(added(current, after));
                } else {
                    modified(current, previous.get(), after, before).ifPresent(changes::add);
                }
            }
            for (var previous : before.classes()) {
                if (find(after, previous.name()).isEmpty()) {
                    changes.add(removed(previous, before));
                }
            }
            return changes;
        }

        private static Optional<ClassStructure> find(CodeStructure structure, String name) {
            return structure.classes().stream()
                    .filter(clazz -> clazz.name().equals(name))
                    .findFirst();
        }

        private static ClassChange added(ClassStructure current, CodeStructure file) {
            return new ClassChange(current.name(), ChangeKind.ADDED, current.members(), List.of(),
                    List.copyOf(dependencies(current, file)), List.of());
        }

        private static ClassChange removed(ClassStructure previous, CodeStructure file) {
            return new ClassChange(previous.name(), ChangeKind.REMOVED, List.of(), previous.members(),
                    List.of(), List.copyOf(dependencies(previous, file)));
        }

        private static Optional<ClassChange> modified(ClassStructure current,
                ClassStructure previous, CodeStructure afterFile, CodeStructure beforeFile) {
            var currentDependencies = dependencies(current, afterFile);
            var previousDependencies = dependencies(previous, beforeFile);
            var addedMembers = difference(current.members(), previous.members());
            var removedMembers = difference(previous.members(), current.members());
            var addedDependencies = difference(currentDependencies, previousDependencies);
            var removedDependencies = difference(previousDependencies, currentDependencies);
            if (addedMembers.isEmpty() && removedMembers.isEmpty()
                    && addedDependencies.isEmpty() && removedDependencies.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ClassChange(current.name(), ChangeKind.MODIFIED, addedMembers,
                    removedMembers, addedDependencies, removedDependencies));
        }

        // Dependencias (decisión 4): superTypes + tipos usados + los imports del
        // archivo, que se atribuyen a todas sus clases
        private static Set<String> dependencies(ClassStructure clazz, CodeStructure file) {
            var dependencies = new HashSet<String>(clazz.superTypes());
            dependencies.addAll(clazz.typeUsages());
            dependencies.addAll(file.imports());
            return dependencies;
        }

        private static <T> List<T> difference(Collection<T> left, Collection<T> right) {
            var remainder = new LinkedHashSet<>(left);
            remainder.removeAll(right);
            return List.copyOf(remainder);
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
