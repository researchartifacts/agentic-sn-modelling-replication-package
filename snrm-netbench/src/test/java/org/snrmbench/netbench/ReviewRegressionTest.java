package org.snrmbench.netbench;

import org.snrmbench.netbench.cli.Main;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.verify.CsvEdits;
import org.snrmbench.netbench.verify.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for the findings of the code review that were confirmed and fixed. */
class ReviewRegressionTest {

    private static List<Code> codes(Map<String, String> files) {
        return SnrmRules.validate(LenientCsvReader.read(files)).findings().stream().map(Finding::code).toList();
    }

    /** Durations and NO_DEMAND_DECLARED see only the graph the network stage runs on. */
    @Test
    void networkChecksIgnoreRowsOfDroppedNodes() {
        Map<String, String> m = CsvEdits.sample("minimal-6-node");
        m = CsvEdits.set(m, Sheet.NODES, "DC-1", Schema.NODE_FAILURE_PROB, "1.4");          // DC-1 dropped
        m = CsvEdits.set(m, Sheet.LINKS, "DC-1->CUST-1", Schema.LINK_LEAD_UNIT, "HOUR");      // 1 HOUR on 1 DAY
        m = CsvEdits.set(m, Sheet.LINKS, "DC-1->CUST-2", Schema.LINK_LEAD_UNIT, "YEAR");      // 2 YEAR: 730 periods
        m = CsvEdits.set(m, Sheet.LINKS, "DC-1->CUST-2", Schema.LINK_LEAD_VALUE, "5");
        assertThat(codes(m)).contains(Code.PROBABILITY_OUT_OF_RANGE, Code.NETWORK_CHECKS_ON_PARTIAL_GRAPH)
                .doesNotContain(Code.DURATION_ROUNDS_TO_ZERO, Code.PERIOD_TOO_FINE);

        Map<String, String> d = CsvEdits.sample("minimal-6-node");
        d = CsvEdits.set(d, Sheet.NODE_PRODUCTS, "CUST-2|WIDGET-A", Schema.NP_DEMAND_VALUE, "0");
        d = CsvEdits.set(d, Sheet.NODES, "CUST-1", Schema.NODE_FAILURE_PROB, "1.4");         // the only demand is dropped
        assertThat(codes(d)).contains(Code.NO_DEMAND_DECLARED);
    }

    /** A product name may contain "->" and "|": the ledger keys are split by kind, not by text. */
    @Test
    void aProductNameWithArrowAndBarGenerates() {
        for (String name : List.of("Widget->Pro", "A|B")) {
            Generator.Generated g = Generator.build(GenerateParams.defaults(5, 9, 4, name), null);
            Map<String, String> truth = new LinkedHashMap<>();
            for (Sheet s : Sheet.values()) {
                truth.put(s.fileName(), g.files().get("truth/" + s.fileName()));
            }
            Report r = CsvEdits.verify(truth, truth, g.facts(), null);
            assertThat(r.factRecall()).isEqualTo(1.0);
            assertThat(r.l2().mismatches()).isEmpty();
        }
    }

    /** A blank period_length_unit is not resolved through the period itself: the meta fact is wrong. */
    @Test
    void aBlankPeriodUnitIsNotCredited() {
        Map<String, String> truth = CsvEdits.sample("multi-echelon-50-node");
        Map<String, String> c = CsvEdits.set(truth, Sheet.NETWORK_META, null, Schema.META_PERIOD_UNIT, "");
        Report r = CsvEdits.verify(truth, c);
        assertThat(r.l2().mismatches()).extracting(m -> m.cls() + " " + m.field())
                .containsExactly("wrong period_length");
    }

    /** A decoy node that only a link decoy introduces is caught when C adds it. */
    @Test
    void aDecoyNodeIntroducedByALinkDecoyIsAbsorbed() {
        for (long seed = 1; seed <= 40; seed++) {
            GenerateParams d = GenerateParams.defaults(seed, 20, 4, "Widget");
            Generator.Generated g = Generator.build(new GenerateParams(seed, 20, 4, "Widget", 1, d.naming(),
                    d.style(), d.order(), d.units(), d.periodUnit(), 1, 90, 1, 2, 0, d.attributeCoverage(),
                    GenerateParams.NoiseType.ADVERSARIAL, new BigDecimal("0.6"), false, d.distanceMode(),
                    d.locale()), null);
            Decoy link = g.description().decoys().stream()
                    .filter(x -> x.decoyEntity() && x.kind().equals("link")
                            && g.description().decoyEntities().contains(x.entity().split("->")[1]))
                    .findFirst().orElse(null);
            if (link == null) {
                continue;
            }
            String decoyNode = link.entity().split("->")[1];
            Map<String, String> truth = new LinkedHashMap<>();
            for (Sheet s : Sheet.values()) {
                truth.put(s.fileName(), g.files().get("truth/" + s.fileName()));
            }
            Map<String, String> c = CsvEdits.addRow(truth, Sheet.NODES,
                    Map.of(Schema.NODE_NAME, decoyNode, Schema.NODE_TYPE, "CUSTOMER"));
            Report r = CsvEdits.verify(truth, c, g.facts(), g.description().decoys());
            assertThat(r.decoys().entitiesAbsorbed()).contains(decoyNode);
            assertThat(r.l2().mismatches()).extracting(Report.Mismatch::cls).containsExactly("fabricated");
            return;
        }
        throw new AssertionError("no seed produced a link decoy to a decoy node");
    }

    /** extract leaves an out-of-format value blank and reports it, instead of crashing. */
    @Test
    void extractSurvivesAValueThatDoesNotFitItsColumn(@TempDir Path tmp) throws IOException {
        Path run = Path.of("examples", GenerateParams.defaults(1, 12, 4, "Widget").defaultRunId());
        String text = Files.readString(run.resolve("description/description.md"))
                .replace("a capacity of 80 units per day", "a capacity of 80.5 units per day");
        Path description = Files.writeString(tmp.resolve("description.md"), text);
        assertThat(Main.run("extract", "--description", description.toString(), "--out",
                tmp.resolve("lenient").toString())).isZero();
        assertThat(tmp.resolve("lenient").resolve("nodes.csv")).exists();
        assertThat(Files.readString(tmp.resolve("lenient").resolve("extract-log.json"))).contains("80.5");
        assertThat(Main.run("extract", "--description", description.toString(), "--out",
                tmp.resolve("strict").toString(), "--strict")).isEqualTo(4);
    }

    /** validate writes UTF-8 whatever the console encoding, and lists files in ordinal name order. */
    @Test
    void validateWritesUtf8AndOrdersFilesOrdinally(@TempDir Path tmp) throws IOException {
        Map<String, String> m = CsvEdits.addRow(CsvEdits.sample("minimal-6-node"), Sheet.NODES,
                Map.of(Schema.NODE_NAME, "Zürich", Schema.NODE_TYPE, "DC"));
        for (Map.Entry<String, String> e : m.entrySet()) {
            Files.writeString(tmp.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
        Files.writeString(tmp.resolve("extra.csv"), "a\n1\n");
        Files.writeString(tmp.resolve("Notes.csv"), "a\n1\n");
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.ISO_8859_1));
            Main.run("validate", "--network", tmp.toString());
        } finally {
            System.setOut(original);
        }
        String out = bytes.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("Zürich");
        assertThat(Json.mapper().readTree(out).get("findings").toString())
                .containsSubsequence("Notes.csv", "extra.csv");
    }
}
