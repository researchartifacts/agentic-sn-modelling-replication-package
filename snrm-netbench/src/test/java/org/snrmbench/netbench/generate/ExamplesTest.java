package org.snrmbench.netbench.generate;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.cli.Main;
import org.snrmbench.netbench.util.Json;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The committed examples/ (seeds 1–3, 12 nodes, 4 echelons) are golden: regenerating them on any machine must reproduce every file byte for byte. */
class ExamplesTest {

    private static Map<String, String> hashes(Path dir) throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                out.put(dir.relativize(f).toString().replace('\\', '/'), Json.sha256(f));
            }
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void regeneratingAnExampleReproducesItExactly(long seed, @TempDir Path tmp) throws IOException {
        String runId = GenerateParams.defaults(seed, 12, 4, "Widget").defaultRunId();
        Path committed = Path.of("examples", runId);
        assertThat(committed).isDirectory();
        assertThat(Main.run("generate", "--seed", Long.toString(seed), "--nodes", "12", "--echelons", "4",
                "--final-product-name", "Widget", "--out-dir", tmp.toString())).isZero();
        assertThat(hashes(tmp.resolve(runId))).isEqualTo(hashes(committed));
    }

    /** Acceptance: verify(B, B) is perfect on every example, with its ledger and manifest. */
    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void verifyingAnExampleAgainstItselfIsPerfect(long seed, @TempDir Path tmp) {
        Path truth = Path.of("examples", GenerateParams.defaults(seed, 12, 4, "Widget").defaultRunId(), "truth");
        Path report = tmp.resolve("report.json");
        assertThat(Main.run("verify", "--truth", truth.toString(), "--candidate", truth.toString(),
                "--out", report.toString())).isZero();
        JsonNode r = Json.read(report);
        assertThat(r.at("/l0/import_valid").asBoolean()).isTrue();
        assertThat(r.at("/l1/structure_f1").asDouble()).isEqualTo(1.0);
        assertThat(r.at("/l2/fact_recall").asDouble()).isEqualTo(1.0);
        assertThat(r.at("/l2/fact_precision").asDouble()).isEqualTo(1.0);
        assertThat(r.at("/l2/mismatches")).isEmpty();
        assertThat(r.at("/composite/ncs").asDouble()).isEqualTo(1.0);
        assertThat(r.at("/run_id").asText()).startsWith("s" + seed + "-n12-e4-");
    }
}
