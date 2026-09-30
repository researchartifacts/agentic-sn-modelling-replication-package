package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** verifying each bundled SNRM sample against itself is perfect. */
class VerifierSamplesTest {

    static String[] samples() {
        return TestSupport.SAMPLES;
    }

    @ParameterizedTest
    @MethodSource("samples")
    void sampleAgainstItselfIsPerfect(String sample) {
        Path dir = TestSupport.sample(sample);
        Report r = Verifier.verify(new Verifier.Inputs(LenientCsvReader.readDirectory(dir),
                CandidateLoader.load(dir, null), null, null, null, new BigDecimal("1e-6"), dir.toString(),
                dir.toString()));

        assertThat(r.importValid()).isTrue();
        assertThat(r.structureF1()).isEqualTo(1.0);
        assertThat(r.factRecall()).isEqualTo(1.0);
        assertThat(r.factPrecision()).isEqualTo(1.0);
        assertThat(r.l2().mismatches()).isEmpty();
        assertThat(r.fabricatedCount()).isZero();
        assertThat(r.conventionViolationCount()).isZero();
        assertThat(r.l1().nodeTypeAccuracy()).isEqualTo(1.0);
        assertThat(r.l1().linkRecallConditional()).isEqualTo(1.0);
        assertThat(r.l1().products().f1()).isEqualTo(1.0);
        assertThat(r.l1().nodeProducts().f1()).isEqualTo(1.0);
        assertThat(r.envelope().source()).isEqualTo(CandidateLoader.SOURCE_DIRECTORY);
        assertThat(r.envelope().filesMissing()).isEmpty();
        assertThat(r.ncs()).isCloseTo(1.0, within(1e-12));
        assertThat(r.l2().statedFacts()).isPositive();
    }
}
