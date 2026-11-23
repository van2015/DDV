package ddv.assertions;

import ddv.domain.exception.DiffException;
import ddv.domain.exception.ParseException;
import ddv.domain.exception.ScopeException;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;

/**
 * Assertions for the typed domain exceptions.
 *
 * <p>Every failure of the pipeline is reported through a {@code Code}, so tests
 * state the expected code instead of repeating the cast-and-extract chain.</p>
 */
public final class DdvAssertions {

    private DdvAssertions() {
    }

    /** Asserts the call throws a {@link ScopeException} carrying the expected code. */
    public static void assertScopeThrows(ThrowableAssert.ThrowingCallable shouldRaise,
            ScopeException.Code expected) {
        Assertions.assertThatThrownBy(shouldRaise)
                .isInstanceOfSatisfying(ScopeException.class,
                        actual -> Assertions.assertThat(actual.code()).isEqualTo(expected));
    }

    /** Asserts the call throws a {@link DiffException} carrying the expected code. */
    public static void assertDiffThrows(ThrowableAssert.ThrowingCallable shouldRaise,
            DiffException.Code expected) {
        Assertions.assertThatThrownBy(shouldRaise)
                .isInstanceOfSatisfying(DiffException.class,
                        actual -> Assertions.assertThat(actual.code()).isEqualTo(expected));
    }

    /** Asserts the call throws a {@link ParseException} carrying the expected code. */
    public static void assertParseThrows(ThrowableAssert.ThrowingCallable shouldRaise,
            ParseException.Code expected) {
        Assertions.assertThatThrownBy(shouldRaise)
                .isInstanceOfSatisfying(ParseException.class,
                        actual -> Assertions.assertThat(actual.code()).isEqualTo(expected));
    }
}
