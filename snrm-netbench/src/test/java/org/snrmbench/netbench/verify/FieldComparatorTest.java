package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.verify.FieldComparator.Value;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** normalisation and value equality. */
class FieldComparatorTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1e-6");

    private static Value v(String value, String unit) {
        return new Value(value, unit, TimeUnit.DAY);
    }

    private static boolean eq(Field f, Value a, Value b) {
        return FieldComparator.equal(f, a, b, TOLERANCE);
    }

    @Test
    void namesNormaliseByTrimQuotesWhitespaceAndCase() {
        assertThat(Normaliser.name("  \"Rotterdam   II\" ")).isEqualTo("rotterdam ii");
        assertThat(Normaliser.name("'DCR-01'")).isEqualTo("dcr-01");
        assertThat(Normaliser.name("\"DCR-01'")).isEqualTo("\"dcr-01'");
        assertThat(Normaliser.name(null)).isEmpty();
    }

    @Test
    void blankUnitsResolveToThePeriodAndUnknownTokensToNothing() {
        assertThat(Normaliser.unit("", TimeUnit.HOUR)).contains(TimeUnit.HOUR);
        assertThat(Normaliser.unit(null, TimeUnit.WEEK)).contains(TimeUnit.WEEK);
        assertThat(Normaliser.unit("Weeks", TimeUnit.DAY)).contains(TimeUnit.WEEK);
        assertThat(Normaliser.unit("fortnight", TimeUnit.DAY)).isEmpty();
    }

    @Test
    void durationsCompareInSecondsAndRatesPerSecond() {
        assertThat(eq(Field.LINK_LEAD_TIME, v("3", "WEEK"), v("21", "days"))).isTrue();
        assertThat(eq(Field.LINK_LEAD_TIME, v("1", "DAY"), v("24", "h"))).isTrue();
        assertThat(eq(Field.LINK_LEAD_TIME, v("21", "DAY"), v("21", "HOUR"))).isFalse();
        assertThat(eq(Field.NODE_CAPACITY, v("7", "WEEK"), v("1", "DAY"))).isTrue();
        assertThat(eq(Field.NODE_CAPACITY, v("2000", "WEEK"), v("285.71", "DAY"))).isFalse();
        assertThat(eq(Field.LINK_LEAD_TIME, v("2", ""), new Value("48", "HOUR", TimeUnit.WEEK))).isTrue();
        assertThat(eq(Field.LINK_LEAD_TIME, v("2", ""), new Value("2", "", TimeUnit.WEEK))).isFalse();
    }

    @Test
    void wrongUnitNeedsEqualNumbersAndTwoKnownUnits() {
        assertThat(FieldComparator.sameNumberOtherUnit(Field.LINK_LEAD_TIME, v("21", "DAY"), v("21", "HOUR")))
                .isTrue();
        assertThat(FieldComparator.sameNumberOtherUnit(Field.LINK_LEAD_TIME, v("21", "DAY"), v("21", "fortnight")))
                .isFalse();
        assertThat(FieldComparator.sameNumberOtherUnit(Field.NODE_FIXED_COST, v("21", ""), v("21", ""))).isFalse();
    }

    @Test
    void integersAreExactAndOtherNumbersUseTheRelativeTolerance() {
        assertThat(eq(Field.NODE_FIXED_COST, v("1000000", ""), v("1000001", ""))).isFalse();
        assertThat(eq(Field.NODE_FIXED_COST, v("1000000", ""), v("1000000.0", ""))).isTrue();
        assertThat(eq(Field.NODE_VAR_COST, v("1.0", ""), v("1.0000001", ""))).isTrue();
        assertThat(eq(Field.NODE_VAR_COST, v("1.80", ""), v("1.81", ""))).isFalse();
        assertThat(eq(Field.NODE_VAR_COST, v("1.80", ""), v("n/a", ""))).isFalse();
    }

    @Test
    void geoComparesBothCoordinatesToTwoDecimals() {
        assertThat(eq(Field.NODE_GEO, v("52.374", "4.9"), v("52.37", "4.90"))).isTrue();
        assertThat(eq(Field.NODE_GEO, v("52.375", "4.90"), v("52.38", "4.90"))).isTrue();
        assertThat(eq(Field.NODE_GEO, v("52.37", "4.90"), v("52.37", ""))).isFalse();
        assertThat(FieldComparator.blank(Field.NODE_GEO, v("", ""))).isTrue();
        assertThat(FieldComparator.blank(Field.NODE_GEO, v("52.37", ""))).isFalse();
    }

    @Test
    void textAndTypesCompareNormalised() {
        assertThat(eq(Field.NODE_REGION, v("EU-West", ""), v(" eu-west ", ""))).isTrue();
        assertThat(eq(Field.NODE_EXISTS, v("PLANT", ""), v("plant", ""))).isTrue();
        assertThat(eq(Field.META_ROUNDING, v("NEAREST", ""), v("UP", ""))).isFalse();
    }
}
