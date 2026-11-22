package ddv.application.scope;

import ddv.domain.model.AnalysisScope;
import ddv.domain.model.RepoScope;
import ddv.ports.GitSourcePort;

/**
 * Caso de uso UC1: conexión y definición del alcance del repositorio.
 *
 * <p>Orquesta el puerto de Git (abrir → resolver rango → cerrar) sin conocer
 * la infraestructura; las validaciones viven en el dominio y en el adaptador.</p>
 */
public class ScopeResolver {

    private final GitSourcePort gitSourcePort;

    public ScopeResolver(GitSourcePort gitSourcePort) {
        this.gitSourcePort = gitSourcePort;
    }

    public AnalysisScope resolve(RepoScope scope) {
        try (var handle = gitSourcePort.open(scope)) {
            return gitSourcePort.resolveRange(handle, scope);
        }
    }
}
