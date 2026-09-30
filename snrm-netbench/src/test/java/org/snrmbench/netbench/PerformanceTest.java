package org.snrmbench.netbench;

import org.snrmbench.netbench.cli.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** generating a 1000-node network (self-checks included) and verifying it each take under 5 s. */
class PerformanceTest {

    @Test
    void generateAndVerifyOneThousandNodesUnderFiveSecondsEach(@TempDir Path tmp) {
        Main.run("generate", "--seed", "99", "--nodes", "60", "--echelons", "5", "--final-product-name", "Warmup",
                "--out-dir", tmp.toString(), "--run-id", "warmup");
        long start = System.nanoTime();
        int exit = Main.run("generate", "--seed", "1", "--nodes", "1000", "--echelons", "5",
                "--final-product-name", "Widget", "--out-dir", tmp.toString(), "--run-id", "big");
        long generateMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(exit).isZero();
        assertThat(generateMs).as("generate --nodes 1000 took %d ms", generateMs).isLessThan(5_000);

        Path truth = tmp.resolve("big").resolve("truth");
        start = System.nanoTime();
        exit = Main.run("verify", "--truth", truth.toString(), "--candidate", truth.toString(),
                "--out", tmp.resolve("report.json").toString());
        long verifyMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(exit).isZero();
        assertThat(verifyMs).as("verify of the 1000-node pair took %d ms", verifyMs).isLessThan(5_000);
    }
}
