package ddv.domain.model;

import ddv.domain.exception.ScopeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoScopeTest {

    @Test
    @DisplayName("between() crea el alcance con rango A..B recortado")
    void betweenCreaRangoEntreCommits() {
        var scope = RepoScope.between("/repo/x", " abc123 ", "def456");

        assertThat(scope.location()).isEqualTo("/repo/x");
        assertThat(scope.range()).isInstanceOf(RepoScope.Range.Between.class);
        var between = (RepoScope.Range.Between) scope.range();
        assertThat(between.from()).isEqualTo("abc123");
        assertThat(between.to()).isEqualTo("def456");
    }

    @Test
    @DisplayName("lastN() crea el alcance con rango de últimos N commits")
    void lastNCreaRangoUltimosN() {
        var scope = RepoScope.lastN("/repo/x", 10);

        assertThat(scope.range()).isEqualTo(new RepoScope.Range.LastN(10));
    }

    @Test
    @DisplayName("lastN() con N <= 0 lanza INVALID_ARGUMENT")
    void lastNNoPositivoLanzaError() {
        assertThatThrownBy(() -> RepoScope.lastN("/repo/x", 0))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("ubicación vacía o nula lanza INVALID_ARGUMENT")
    void ubicacionVaciaLanzaError() {
        assertThatThrownBy(() -> RepoScope.lastN("  ", 5))
                .isInstanceOf(ScopeException.class);
        assertThatThrownBy(() -> RepoScope.between(null, "a", "b"))
                .isInstanceOf(ScopeException.class);
    }

    @Test
    @DisplayName("rango A..B con extremos vacíos lanza INVALID_ARGUMENT")
    void rangoConExtremosVaciosLanzaError() {
        assertThatThrownBy(() -> RepoScope.between("/repo", "", "abc"))
                .isInstanceOf(ScopeException.class);
    }

    @Test
    @DisplayName("rango nulo lanza INVALID_ARGUMENT")
    void rangoNuloLanzaError() {
        assertThatThrownBy(() -> new RepoScope("/repo", null))
                .isInstanceOf(ScopeException.class);
    }

    @Test
    @DisplayName("isRemote() detecta URLs y scp-style; no rutas locales")
    void detectaFuenteRemota() {
        assertThat(RepoScope.between("https://github.com/x/y.git", "a", "b").isRemote()).isTrue();
        assertThat(RepoScope.between("git@github.com:x/y.git", "a", "b").isRemote()).isTrue();
        assertThat(RepoScope.lastN("/ruta/local", 3).isRemote()).isFalse();
        assertThat(RepoScope.lastN("./relativa", 3).isRemote()).isFalse();
    }
}
