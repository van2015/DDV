package ddv.ports;

import ddv.domain.exception.DiffException;
import ddv.domain.exception.ScopeException;
import ddv.domain.model.AnalysisScope;
import ddv.domain.model.Commit;
import ddv.domain.model.CommitDelta;
import ddv.domain.model.RepoScope;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acceso a Git: los adaptadores (p. ej. JGit) lo implementan;
 * el núcleo solo conoce esta interfaz y los tipos de dominio.
 *
 * <p>Los adaptadores traducen errores de infraestructura a excepciones tipadas
 * del dominio: {@link ScopeException} para apertura y resolución del alcance
 * ({@code REPO_NOT_FOUND}, {@code NO_ACCESS}, {@code COMMIT_NOT_FOUND},
 * {@code NOT_ANCESTOR}) y {@link DiffException} para lectura de datos
 * ({@code INVALID_ARGUMENT}, {@code IO_ERROR}).</p>
 */
public interface GitSourcePort {

    /** Valida existencia/acceso del repositorio del alcance y devuelve su handle (cerrable). */
    RepositoryHandle open(RepoScope scope);

    /** Commits $C_1..C_n$ del rango, orden cronológico ascendente; lanza {@code EMPTY_RANGE} si no hay ninguno. */
    AnalysisScope resolveRange(RepositoryHandle repository, RepoScope scope);

    /** Δ entre dos commits consecutivos del rango. */
    CommitDelta diff(RepositoryHandle repository, Commit previous, Commit current);

    /**
     * Contenido de un archivo en un commit concreto, en UTF-8 — lo usa UC3 para
     * parsear la versión vieja y la nueva de cada archivo del Δ.
     *
     * @return el contenido, o vacío si el archivo no existía en ese commit
     */
    Optional<String> content(RepositoryHandle repository, Commit commit, String path);
}
