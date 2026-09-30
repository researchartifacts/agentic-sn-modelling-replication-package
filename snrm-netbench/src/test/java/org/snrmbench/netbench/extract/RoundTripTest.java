package org.snrmbench.netbench.extract;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.cli.Main;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code extract} on a template description reproduces B exactly. */
class RoundTripTest {

    /** template × every order × every noise type × three seeds; seed 2 also uses mixed units and coverage 0.6. */
    static Stream<Arguments> cells() {
        List<Arguments> out = new ArrayList<>();
        for (String order : new String[] {"by-tier", "by-node", "shuffled"}) {
            for (String noise : new String[] {"none", "boilerplate", "topical", "adversarial"}) {
                for (long seed : new long[] {1, 2, 3}) {
                    out.add(Arguments.of(order, noise, seed, seed == 2 ? "0.6" : "1.0"));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1} seed={2} coverage={3}")
    @MethodSource("cells")
    void extractReproducesTheTruthByteForByte(String order, String noise, long seed, String coverage,
                                              @TempDir Path tmp) throws IOException {
        assertThat(Main.run("generate", "--seed", Long.toString(seed), "--nodes", "25", "--echelons", "5",
                "--final-product-name", "Widget", "--order", order, "--attribute-coverage", coverage,
                "--noise-type", noise, "--noise-ratio", noise.equals("none") ? "0" : "0.4",
                "--units", seed == 2 ? "mixed" : "uniform", "--out-dir", tmp.toString(), "--run-id", "r")).isZero();
        Path run = tmp.resolve("r");
        Path out = tmp.resolve("extracted");
        String description = run.resolve("description/description.md").toString();
        // without noise every sentence must match (--strict); with noise, exactly the noise is left out
        assertThat(Main.run("extract", "--description", description, "--out", out.toString(), "--strict"))
                .isEqualTo(noise.equals("none") || noise.equals("adversarial") ? 0 : 4);
        for (Sheet s : Sheet.values()) {
            assertThat(out.resolve(s.fileName())).as(s.fileName())
                    .hasSameBinaryContentAs(run.resolve("truth").resolve(s.fileName()));
        }
        int noiseSentences = Json.read(run.resolve("manifest.json")).at("/counts/sentences_noise").asInt();
        JsonNode log = Json.read(out.resolve("extract-log.json"));
        assertThat(log.get("skipped").asInt()).isEqualTo(noise.equals("adversarial") ? noiseSentences : 0);
        assertThat(log.get("ignored").asInt()).isEqualTo(noise.equals("adversarial") ? 0 : noiseSentences);
        if (noise.equals("none")) {
            assertThat(noiseSentences).isZero();
        } else {
            assertThat(noiseSentences).isPositive();
        }

        Path report = tmp.resolve("report.json");
        assertThat(Main.run("verify", "--truth", run.resolve("truth").toString(), "--candidate", out.toString(),
                "--out", report.toString())).isZero();
        JsonNode r = Json.read(report);
        assertThat(r.at("/l0/import_valid").asBoolean()).isTrue();
        assertThat(r.at("/l1/structure_f1").asDouble()).isEqualTo(1.0);
        assertThat(r.at("/l2/fact_recall").asDouble()).isEqualTo(1.0);
        assertThat(r.at("/l2/mismatches")).isEmpty();
    }
}
