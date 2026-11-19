package ddv.domain.model;

import ddv.domain.exception.ScopeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisScopeTest {

    private static final RepoScope ORIGEN = RepoScope.lastN("/repo", 3);

    private static Commit commit(String sha) {
        return Commit.of(sha);
    }

    @Test
    @DisplayName("Sin commits lanza EMPTY_RANGE")
    void sinCommitsLanzaError() {
        assertThatThrownBy(() -> new AnalysisScope(ORIGEN, List.of()))
                .isInstanceOf(ScopeException.class)
                .extracting(e -> ((ScopeException) e).code())
                .isEqualTo(ScopeException.Code.EMPTY_RANGE);
    }

    @Test
    @DisplayName("Origen nulo lanza NullPointerException")
    void origenNuloLanzaError() {
        assertThatThrownBy(() -> new AnalysisScope(null, List.of(commit("a1"))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("La lista de commits es inmutable aunque se pase mutable")
    void listaDeCommitsEsInmutable() {
        var externa = new ArrayList<>(List.of(commit("a1"), commit("b2")));
        var scope = new AnalysisScope(ORIGEN, externa);

        externa.clear(); // mutar la fuente externa no afecta al dominio
        assertThat(scope.size()).isEqualTo(2);

        assertThatThrownBy(() -> scope.commits().add(commit("c3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("first()/last() devuelven extremos del rango")
    void extremosDelRango() {
        var scope = new AnalysisScope(ORIGEN,
                List.of(commit("aaa1111"), commit("bbb2222"), commit("ccc3333")));

        assertThat(scope.first().id()).isEqualTo("aaa1111");
        assertThat(scope.last().id()).isEqualTo("ccc3333");
        assertThat(scope.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("consecutivePairs() devuelve n-1 pares ordenados (Cᵢ₋₁, Cᵢ)")
    void paresConsecutivosParaElPipeline() {
        var scope = new AnalysisScope(ORIGEN,
                List.of(commit("c1"), commit("c2"), commit("c3"), commit("c4")));

        var pares = scope.consecutivePairs();

        assertThat(pares).hasSize(3);
        assertThat(pares.get(0).previous().id()).isEqualTo("c1");
        assertThat(pares.get(0).current().id()).isEqualTo("c2");
        assertThat(pares.get(2).previous().id()).isEqualTo("c3");
        assertThat(pares.get(2).current().id()).isEqualTo("c4");
    }

    @Test
    @DisplayName("consecutivePairs() con un solo commit devuelve vacío")
    void unSoloCommitSinPares() {
        var scope = new AnalysisScope(ORIGEN, List.of(commit("c1")));

        assertThat(scope.consecutivePairs()).isEmpty();
    }

    @Test
    @DisplayName("Commit.of() deriva shortId (8 chars) y tolera mensaje nulo vía defecto")
    void commitDerivaShortId() {
        var c = Commit.of("1234567890abcdef");

        assertThat(c.shortId()).isEqualTo("12345678");
        assertThat(c.message()).isEmpty();
        assertThat(c.authoredAt()).isNull();
    }

    @Test
    @DisplayName("Commit con id vacío lanza INVALID_ARGUMENT")
    void commitIdVacioLanzaError() {
        assertThatThrownBy(() -> Commit.of(" "))
                .isInstanceOf(ScopeException.class);
    }
}
