package org.snrmbench.netbench.validate;

import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** /: the four bundled samples give exactly the outcomes their README states. */
class SnrmSamplesTest {

    private static ValidationResult validate(String sample) {
        return SnrmRules.validate(LenientCsvReader.readDirectory(TestSupport.sample(sample)));
    }

    @Test
    void minimal6NodeImportsClean() {
        ValidationResult r = validate("minimal-6-node");
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void multiEchelon50NodeHasEightLateralLinks() {
        ValidationResult r = validate("multi-echelon-50-node");
        assertThat(r.errorCount()).isZero();
        assertThat(r.countsByCode(Severity.WARNING)).isEqualTo(Map.of("LATERAL_LINK", 8));
    }

    @Test
    void mixedUnits10NodeHasOneRoundingErrorAndThreeLateralLinks() {
        ValidationResult r = validate("mixed-units-10-node");
        assertThat(r.errorCount()).isZero();
        assertThat(r.countsByCode(Severity.WARNING))
                .isEqualTo(Map.of("DURATION_ROUNDING_ERROR", 1, "LATERAL_LINK", 3));
        Finding rounding = r.findings().stream()
                .filter(f -> f.code() == Code.DURATION_ROUNDING_ERROR).findFirst().orElseThrow();
        assertThat(rounding.element()).isEqualTo("PLT-DE");
        assertThat(rounding.message()).contains("rounds to 2 periods (+33%)");
    }

    @Test
    void simulationVerification3NodeImportsCleanWithZeroLeadTimes() {
        ValidationResult r = validate("simulation-verification-3-node");
        assertThat(r.findings()).isEmpty();
    }
}
