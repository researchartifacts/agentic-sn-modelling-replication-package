package org.snrmbench.netbench.util;

import org.snrmbench.netbench.schema.Column;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** formatting and parsing. */
class NumbersTest {

    private static Locale saved;

    @BeforeAll
    static void germanDefaultLocale() {
        saved = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterAll
    static void restoreLocale() {
        Locale.setDefault(saved);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @ParameterizedTest
    @CsvSource({
            "1.8, 1.80",
            "0.05, 0.05",
            "1.800, 1.80",
            "12, 12.00",
            "1234567.5, 1234567.50",
            "-0.3, -0.30"})
    void decimal2ColumnsHaveExactlyTwoDecimals(String value, String expected) {
        assertThat(Numbers.decimal2(bd(value))).isEqualTo(expected);
        assertThat(Numbers.format(bd(value), Column.Kind.DECIMAL2)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "1200, 1200",
            "1200.00, 1200",
            "1E+3, 1000",
            "1234567, 1234567",
            "-5, -5"})
    void integerColumnsHaveNoDecimalPointAndNoThousandsSeparator(String value, String expected) {
        assertThat(Numbers.integer(bd(value))).isEqualTo(expected);
        assertThat(Numbers.format(bd(value), Column.Kind.INTEGER)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.0", "0E+2"})
    void zeroIsTheLiteral0InEveryKind(String zero) {
        for (Column.Kind kind : Column.Kind.values()) {
            assertThat(Numbers.format(bd(zero), kind)).as(kind.name()).isEqualTo("0");
        }
        assertThat(Numbers.plain(bd(zero))).isEqualTo("0");
    }

    @Test
    void nullIsABlankCell() {
        assertThat(Numbers.format(null, Column.Kind.INTEGER)).isEmpty();
        assertThat(Numbers.format(null, Column.Kind.DECIMAL2)).isEmpty();
    }

    @Test
    void aValueThatDoesNotFitItsColumnIsAGeneratorBug() {
        assertThatThrownBy(() -> Numbers.integer(bd("1.5"))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Numbers.decimal2(bd("0.125"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void plainIsTheShortestFormWithoutExponent() {
        assertThat(Numbers.plain(bd("1.50"))).isEqualTo("1.5");
        assertThat(Numbers.plain(bd("10"))).isEqualTo("10");
        assertThat(Numbers.plain(bd("1E+3"))).isEqualTo("1000");
        assertThat(Numbers.plain(bd("0.000001"))).isEqualTo("0.000001");
    }

    @ParameterizedTest
    @CsvSource({
            "-3, -3",
            "2.50, 2.50",
            "7.0, 7.0",
            "+4, 4",
            "' 12 ', 12",
            "0.05, 0.05",
            "1200, 1200"})
    void parseAcceptsSignDigitsAndAnOptionalFraction(String cell, String expected) {
        assertThat(Numbers.parse(cell)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1,200", "n/a", "500 units", ".5", "1e3", "1E3", "7.", "1.2.3", "1 200", "0x10",
            "NaN", "Infinity", "", "  ", "-", "1,5", "١٢", "１２"})
    void parseRejectsEverythingElse(String cell) {
        assertThat(Numbers.parse(cell)).isEmpty();
    }

    @Test
    void parseOfNullIsEmpty() {
        assertThat(Numbers.parse(null)).isEmpty();
    }

    @Test
    void theDefaultLocaleReallyIsGerman() {
        assertThat(String.format("%.2f", 1.8)).isEqualTo("1,80");
        assertThat(Numbers.decimal2(bd("1.8"))).isEqualTo("1.80");
    }
}
