package ddv.adapters.out.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import ddv.domain.exception.ParseException;
import ddv.domain.model.CodeStructure;
import ddv.domain.model.CodeStructure.ClassKind;
import ddv.domain.model.CodeStructure.ClassStructure;
import ddv.domain.model.CodeStructure.MemberKind;
import ddv.domain.model.CodeStructure.MemberStructure;
import ddv.ports.ParserPort;

import java.util.ArrayList;
import java.util.List;

import static ddv.domain.exception.ParseException.requireNotBlank;

/**
 * {@link ParserPort} implementado con JavaParser.
 *
 * <p>Recorre el AST y produce la {@link CodeStructure} neutra: imports, clases
 * con sus miembros (métodos, constructores, atributos, constantes de enum y
 * componentes de record), tipos de las cláusulas {@code extends}/
 * {@code implements} y tipos usados en firmas y cuerpos — sin resolución de
 * símbolos: todo por nombre.</p>
 */
public class AstParserAdapter implements ParserPort {

    // sin el nivel de lenguaje explícito JavaParser rechaza records (necesita ≥14)
    private final JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_25));

    @Override
    public CodeStructure parse(String path, String source) {
        requireNotBlank(path, "La ruta del archivo no puede estar vacía");
        var unit = compilationUnitOf(path, source);
        var classes = new ArrayList<ClassStructure>();
        for (var type : unit.getTypes()) {
            collect(type, "", classes);
        }
        return new CodeStructure(importsOf(unit), classes);
    }

    private CompilationUnit compilationUnitOf(String path, String source) {
        var result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new ParseException(ParseException.Code.SYNTAX_ERROR,
                    "Error de sintaxis en " + path + ": " + describe(result));
        }
        return result.getResult().get();
    }

    private static String describe(ParseResult<CompilationUnit> result) {
        var problems = result.getProblems();
        if (problems.isEmpty()) {
            return "no se pudo analizar el código";
        }
        var first = problems.get(0);
        var location = first.getLocation()
                .flatMap(range -> range.getBegin().getRange())
                .map(Object::toString)
                .orElse("");
        var extra = problems.size() > 1
                ? " y " + (problems.size() - 1) + " problema(s) más"
                : "";
        return first.getMessage() + " " + location + extra;
    }

    private static List<String> importsOf(CompilationUnit unit) {
        return unit.getImports().stream().map(declaration -> {
            var name = declaration.getNameAsString();
            return declaration.isAsterisk() ? name + ".*" : name;
        }).toList();
    }

    private static void collect(TypeDeclaration<?> type, String prefix, List<ClassStructure> out) {
        var name = prefix.isEmpty() ? type.getNameAsString() : prefix + "." + type.getNameAsString();
        out.add(structureOf(type, name));
        for (var nested : direct(type, TypeDeclaration.class)) {
            collect(nested, name, out);
        }
    }

    // La jerarquía de TypeDeclaration es de un tercero y está abierta: el
    // default ruidoso hace fallar ante un tipo nuevo en vez de ignorarlo
    // en silencio (los tipos propios van sealed, sin default — ver refactors.md C)
    private static ClassStructure structureOf(TypeDeclaration<?> type, String name) {
        return switch (type) {
            case ClassOrInterfaceDeclaration declaration -> new ClassStructure(name,
                    declaration.isInterface() ? ClassKind.INTERFACE : ClassKind.CLASS,
                    superTypes(declaration), membersOf(type), usagesOf(type));
            case EnumDeclaration declaration -> new ClassStructure(name, ClassKind.ENUM,
                    typesOf(declaration.getImplementedTypes()), membersOf(type), usagesOf(type));
            case RecordDeclaration declaration -> new ClassStructure(name, ClassKind.RECORD,
                    typesOf(declaration.getImplementedTypes()), membersOf(type), usagesOf(type));
            case AnnotationDeclaration declaration -> new ClassStructure(name, ClassKind.ANNOTATION,
                    List.of(), membersOf(type), usagesOf(type));
            default -> throw new ParseException(ParseException.Code.SYNTAX_ERROR,
                    "Tipo de declaración no soportado: " + type.getClass().getSimpleName());
        };
    }

    private static List<String> superTypes(ClassOrInterfaceDeclaration declaration) {
        var types = new ArrayList<String>(typesOf(declaration.getExtendedTypes()));
        types.addAll(typesOf(declaration.getImplementedTypes()));
        return types;
    }

    private static List<String> typesOf(NodeList<ClassOrInterfaceType> types) {
        return types.stream().map(ClassOrInterfaceType::getNameAsString).toList();
    }

    private static List<MemberStructure> membersOf(TypeDeclaration<?> type) {
        var members = new ArrayList<MemberStructure>();
        direct(type, MethodDeclaration.class).forEach(method -> members.add(
                new MemberStructure(method.getNameAsString(), MemberKind.METHOD,
                        parameterTypes(method))));
        direct(type, ConstructorDeclaration.class).forEach(constructor -> members.add(
                new MemberStructure(type.getNameAsString(), MemberKind.METHOD,
                        parameterTypes(constructor))));
        direct(type, FieldDeclaration.class).forEach(field -> field.getVariables()
                .forEach(variable -> members.add(new MemberStructure(variable.getNameAsString(),
                        MemberKind.FIELD, List.of()))));
        direct(type, EnumConstantDeclaration.class).forEach(constant -> members.add(
                new MemberStructure(constant.getNameAsString(), MemberKind.FIELD, List.of())));
        direct(type, Parameter.class).forEach(component -> members.add(
                new MemberStructure(component.getNameAsString(), MemberKind.FIELD, List.of())));
        return members;
    }

    private static List<String> parameterTypes(CallableDeclaration<?> callable) {
        return callable.getParameters().stream()
                .map(parameter -> parameter.getType().asString())
                .toList();
    }

    // Tipos usados por la clase, sin contar los de las clases anidadas (que se
    // procesan aparte) ni los de extends/implements (ya registrados como superTypes)
    private static List<String> usagesOf(TypeDeclaration<?> type) {
        return type.findAll(ClassOrInterfaceType.class, node ->
                        declaredWithin(node, type)
                                && node.getParentNode().map(parent -> parent != type).orElse(false))
                .stream()
                .map(ClassOrInterfaceType::getNameAsString)
                .distinct()
                .toList();
    }

    private static boolean declaredWithin(Node node, TypeDeclaration<?> owner) {
        for (var parent = node.getParentNode(); parent.isPresent(); parent = parent.get().getParentNode()) {
            var candidate = parent.get();
            if (candidate == owner) {
                return true;
            }
            if (candidate instanceof TypeDeclaration<?>) {
                return false;
            }
        }
        return false;
    }

    /** Hijos directos de un tipo: evita mezclar los miembros de las clases anidadas. */
    private static <T extends Node> List<T> direct(TypeDeclaration<?> type, Class<T> kind) {
        return type.findAll(kind,
                node -> node.getParentNode().map(parent -> parent == type).orElse(false));
    }
}
