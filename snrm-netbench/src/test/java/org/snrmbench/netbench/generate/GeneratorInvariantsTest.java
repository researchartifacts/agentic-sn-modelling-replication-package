package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.cli.Main;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Severity;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.validate.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** the generator's guarantees over a grid of sizes, depths, unit modes and seeds. */
class GeneratorInvariantsTest {

    static Stream<Arguments> grid() {
        List<Arguments> out = new ArrayList<>();
        for (int nodes : new int[] {7, 12, 25, 50, 100, 300}) {
            for (int echelons = 3; echelons <= 7; echelons++) {
                for (GenerateParams.Units units : GenerateParams.Units.values()) {
                    for (long seed : new long[] {1, 2, 3}) {
                        out.add(Arguments.of(nodes, echelons, units, seed));
                    }
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "n={0} e={1} {2} seed={3}")
    @MethodSource("grid")
    void everyGridCellHoldsTheGuarantees(int nodes, int echelons, GenerateParams.Units units, long seed) {
        GenerateParams d = GenerateParams.defaults(seed, nodes, echelons, "Widget");
        GenerateParams p = new GenerateParams(seed, nodes, echelons, "Widget", 1, d.naming(), d.style(), d.order(),
                units, d.periodUnit(), 1, 90, 1, 2, 0, d.attributeCoverage(), d.noiseType(), d.noiseRatio(), false,
                d.distanceMode(), d.locale());
        Generator.Generated g = Generator.build(p, null); // runs the self-checks
        Network b = g.truth();

        // tier sizes as states
        int[] sizes = StructureBuilder.allocate(nodes, echelons);
        for (int t = 0; t < echelons; t++) {
            assertThat(b.tiers().get(t).nodeNames()).hasSize(sizes[t]);
        }
        assertThat(b.nodes()).hasSize(nodes);

        // zero errors and exactly the predicted warnings
        Map<String, String> truth = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            truth.put(s.fileName(), g.files().get("truth/" + s.fileName()));
        }
        ValidationResult r = SnrmRules.validate(LenientCsvReader.read(truth));
        assertThat(r.errorCount()).isZero();
        long sameType = b.links().stream()
                .filter(l -> type(b, l.source()) == type(b, l.target())).count();
        Map<String, Integer> expected = sameType == 0 ? Map.of() : Map.of("LATERAL_LINK", (int) sameType);
        assertThat(r.countsByCode(Severity.WARNING)).isEqualTo(expected);
        assertThat(((Map<?, ?>) ((Map<?, ?>) g.manifest().get("expected_import")).get("warnings")))
                .isEqualTo(expected);

        // no upstream links: every link runs exactly one tier down
        for (Link l : b.links()) {
            assertThat(tier(b, l.target())).isEqualTo(tier(b, l.source()) + 1);
        }
        // feasibility and reachability
        assertThat(Feasibility.check(b).feasible()).isTrue();
        Set<String> reached = new LinkedHashSet<>(b.tiers().get(0).nodeNames());
        for (int t = 0; t < echelons; t++) {
            for (Link l : b.links()) {
                if (reached.contains(l.source())) {
                    reached.add(l.target());
                }
            }
        }
        assertThat(reached).containsAll(b.tiers().get(echelons - 1).nodeNames());
    }

    @Test
    void allocationMatchesTheWorkedExamples() {
        assertThat(StructureBuilder.allocate(25, 5)).containsExactly(4, 4, 3, 3, 11);
        assertThat(StructureBuilder.allocate(6, 5)).containsExactly(2, 1, 1, 1, 1);
        assertThat(StructureBuilder.allocate(7, 7)).containsExactly(1, 1, 1, 1, 1, 1, 1);
    }

    @Test
    void nodesFewerThanEchelonsIsRefusedWithExitCode2() {
        assertThat(Main.run("generate", "--seed", "1", "--nodes", "4", "--echelons", "5",
                "--final-product-name", "Widget", "--out-dir", Path.of("target", "never").toString())).isEqualTo(2);
    }

    @Test
    void badArgumentsExitWith2() {
        String[] base = {"generate", "--seed", "1", "--nodes", "12", "--echelons", "4", "--out-dir", "target/never"};
        assertThat(run(base, "--final-product-name", "Wid,get")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widget", "--products", "0")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widget", "--inbound-links", "0,2")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widget", "--inbound-links", "3,2")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widget", "--noise-ratio", "0.3")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widgét")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widget", "--style", "poetic")).isEqualTo(2);
        assertThat(run(base, "--final-product-name", "Widget", "--run-id", "..")).isEqualTo(2);
    }

    @Test
    void refusesToOverwriteAnExistingRunWithoutTheFlag(@org.junit.jupiter.api.io.TempDir Path tmp) {
        String[] args = {"generate", "--seed", "1", "--nodes", "12", "--echelons", "4", "--final-product-name",
                "Widget", "--out-dir", tmp.toString(), "--run-id", "r"};
        assertThat(Main.run(args)).isZero();
        assertThat(Main.run(args)).isEqualTo(2);
        assertThat(run(args, "--overwrite")).isZero();
    }

    @Test
    void supplierOverseasShareIsNearThirtyPercent() {
        int overseas = 0;
        int suppliers = 0;
        for (long seed = 1; seed <= 60; seed++) {
            for (Node n : Generator.build(GenerateParams.defaults(seed, 50, 4, "Widget"), null).full().nodes()) {
                if (n.type() == NodeType.SUPPLIER) {
                    suppliers++;
                    overseas += n.region().startsWith("EU-") ? 0 : 1;
                }
            }
        }
        assertThat((double) overseas / suppliers).isBetween(0.22, 0.38);
    }

    private static int run(String[] base, String... extra) {
        return Main.run(Stream.concat(Stream.of(base), Stream.of(extra)).toArray(String[]::new));
    }

    private static NodeType type(Network b, String node) {
        return b.node(node).orElseThrow().type();
    }

    private static int tier(Network b, String node) {
        return b.node(node).orElseThrow().tierIndex();
    }
}
