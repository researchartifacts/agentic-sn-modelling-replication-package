package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Numbers;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** verify's locale-lenient numeric mode: scoring only, never L0. */
class LenientNumbersTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "1200;1200", "1,200;1200", "1.200.000;1200000", "1 200;1200", "1'200;1200", "0,05;0.05", "1,5;1.5",
            "1.234,56;1234.56", "1,234.56;1234.56", "-2,35;-2.35", "2.50;2.50", "12,000,000;12000000",
            "0,050;0.050", "+1,200;1200", "-1.200.000;-1200000"})
    void lenientReadings(String cell, String expected) {
        assertThat(Numbers.parseLenient(cell)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {"n/a", "500 units", "1,2,3", "1.2.3,4.5", "abc", "''"})
    void stillNotNumbers(String cell) {
        assertThat(Numbers.parseLenient(cell)).isEmpty();
    }

    @Test
    void lenientModeScoresLocaleNumbersAsCorrectButL0StillRejectsThem() {
        Map<String, String> truth = CsvEdits.sample("multi-echelon-50-node");
        // SUP-01: capacity 6000 WEEK, var_cost 1.80, fixed_cost 2000 — written with grouping and a decimal comma
        Map<String, String> c = CsvEdits.set(truth, Sheet.NODES, "SUP-01", Schema.NODE_CAPACITY_VALUE, "6,000");
        c = CsvEdits.set(c, Sheet.NODES, "SUP-01", Schema.NODE_VAR_COST, "1,80");
        c = CsvEdits.set(c, Sheet.NODES, "SUP-01", Schema.NODE_FIXED_COST, "2 000");

        Report strict = CsvEdits.verify(truth, c);
        Report lenient = Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(truth),
                CandidateLoader.fromFiles(c, CandidateLoader.SOURCE_DIRECTORY), null, null, null,
                new BigDecimal("1e-6"), "truth", "candidate", true));

        assertThat(strict.l2().mismatches()).extracting(m -> m.cls() + " " + m.field())
                .containsExactlyInAnyOrder("wrong capacity", "wrong var_cost", "wrong fixed_cost");
        assertThat(lenient.l2().mismatches()).isEmpty();
        for (Report r : new Report[] {strict, lenient}) {
            assertThat(r.l0().findings()).extracting(Finding::code).filteredOn(code -> code == Code.NOT_A_NUMBER)
                    .hasSize(3);
            assertThat(r.importValid()).isFalse();
        }
    }

    /** A cell the strict grammar reads keeps that reading: "6.000" is 6, as SNRM reads it, never 6000. */
    @Test
    void theStrictReadingWinsWhereItExists() {
        assertThat(Numbers.parseLenient("6.000")).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("6"));
        Map<String, String> truth = CsvEdits.sample("multi-echelon-50-node");
        Map<String, String> c = CsvEdits.set(truth, Sheet.NODES, "SUP-01", Schema.NODE_CAPACITY_VALUE, "6.000");
        Report lenient = Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(truth),
                CandidateLoader.fromFiles(c, CandidateLoader.SOURCE_DIRECTORY), null, null, null,
                new BigDecimal("1e-6"), "truth", "candidate", true));
        assertThat(lenient.l2().mismatches()).extracting(Report.Mismatch::cls).containsExactly("wrong");
    }
}
