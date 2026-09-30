package org.snrmbench.netbench.cli;

import org.snrmbench.netbench.generate.GenerateParams;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code batch}: the full factorial, skipped cells with reasons, index.csv, and bad designs. */
class BatchCommandTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        CommandLine cmd = new CommandLine(new BatchCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(err, true));
        return cmd.execute(args);
    }

    private int batch(Path tmp, String design, String... extra) throws IOException {
        Path file = tmp.resolve("design.json");
        Files.writeString(file, design, StandardCharsets.UTF_8);
        List<String> args = new ArrayList<>(List.of("--design", file.toString(), "--out-dir",
                tmp.resolve("runs").toString()));
        args.addAll(List.of(extra));
        return run(args.toArray(String[]::new));
    }

    private static List<CSVRecord> index(Path tmp) throws IOException {
        String csv = Files.readString(tmp.resolve("runs").resolve("index.csv"), StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("\r").endsWith("\n");
        try (CSVParser p = CSVParser.parse(csv, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .build())) {
            assertThat(p.getHeaderNames()).containsExactlyElementsOf(BatchCommand.header());
            List<CSVRecord> records = p.getRecords();
            assertThat(records).allSatisfy(r -> assertThat(r.size()).isEqualTo(BatchCommand.header().size()));
            return records;
        }
    }

    private static final String DESIGN = """
            { "name": "t", "fixed": {"final-product-name": "Widget", "horizon": 30, "inbound-links": "1,2",
                                     "attribute-coverage": 1.0, "geo": false},
              "factors": {"nodes": [4, 8], "echelons": [3, 5]},
              "seeds": [1, 2] }
            """;

    @Test
    void generatesTheFullFactorialAndSkipsInvalidCells(@TempDir Path tmp) throws IOException {
        assertThat(batch(tmp, DESIGN)).isZero();

        List<CSVRecord> rows = index(tmp);
        assertThat(rows).extracting(r -> r.get("nodes") + "/" + r.get("echelons") + "/" + r.get("seed"))
                .containsExactly("4/3/1", "4/3/2", "4/5/1", "4/5/2", "8/3/1", "8/3/2", "8/5/1", "8/5/2");
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("generated", "generated", "skipped",
                "skipped", "generated", "generated", "generated", "generated");
        CSVRecord skipped = rows.get(2);
        assertThat(skipped.get("reason")).isEqualTo("--nodes (4) must be at least --echelons (5)");
        assertThat(skipped.get("run_id")).isEmpty();
        assertThat(skipped.get("horizon")).isEqualTo("30");
        assertThat(skipped.get("counts_nodes")).isEmpty();
        assertThat(out.toString()).contains("8 cells, 6 generated, 2 skipped",
                "skipped nodes=4 echelons=5 seed=1: --nodes (4) must be at least --echelons (5)");

        CSVRecord first = rows.get(0);
        assertThat(first.get("run_id")).startsWith("s1-n4-e3-template-typed-uniform-none0-")
                .isNotEqualTo(GenerateParams.defaults(1, 4, 3, "Widget").defaultRunId()); // --horizon 30 is hashed
        assertThat(first.get("reason")).isEmpty();
        assertThat(first.get("style")).isEqualTo("template");
        assertThat(first.get("attribute-coverage")).isEqualTo("1");
        assertThat(first.get("geo")).isEqualTo("0");
        assertThat(first.get("counts_nodes")).isEqualTo("4");
        assertThat(first.get("counts_noise_ratio_actual")).isEqualTo("0");
        assertThat(Integer.parseInt(first.get("counts_facts_stated"))).isPositive();
        for (CSVRecord r : rows) {
            if (r.get("status").equals("generated")) {
                assertThat(tmp.resolve("runs").resolve(r.get("run_id")).resolve("manifest.json")).exists();
                assertThat(r.get("counts_nodes")).isEqualTo(r.get("nodes"));
            }
        }
    }

    @Test
    void existingRunsAreSkippedUnlessOverwrite(@TempDir Path tmp) throws IOException {
        String design = """
                {"fixed": {"final-product-name": "Widget", "nodes": 5, "echelons": 3}, "seeds": [7]}
                """;
        assertThat(batch(tmp, design)).isZero();
        assertThat(index(tmp)).extracting(r -> r.get("status")).containsExactly("generated");

        assertThat(batch(tmp, design)).isZero();
        CSVRecord again = index(tmp).get(0);
        assertThat(again.get("status")).isEqualTo("skipped");
        assertThat(again.get("reason")).contains("exists", "--overwrite");
        assertThat(again.get("run_id")).isNotEmpty();

        assertThat(batch(tmp, design, "--overwrite")).isZero();
        assertThat(index(tmp)).extracting(r -> r.get("status")).containsExactly("generated");
    }

    @Test
    void badValuesAndRepeatedCellsAreSkippedWithTheirReason(@TempDir Path tmp) throws IOException {
        String design = """
                {"fixed": {"final-product-name": "Widget", "echelons": 3},
                 "factors": {"nodes": [5, "many"], "style": ["template", "poetic"]}, "seeds": [3, 3]}
                """;
        assertThat(batch(tmp, design)).isZero();

        List<CSVRecord> rows = index(tmp);
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("generated", "skipped", "skipped",
                "skipped", "skipped", "skipped", "skipped", "skipped");
        assertThat(rows.get(1).get("reason")).isEqualTo("same parameters as an earlier cell");
        assertThat(rows.get(2).get("reason")).contains("poetic");
        assertThat(rows.get(2).get("style")).isEqualTo("poetic");
        assertThat(rows.get(4).get("reason")).contains("--nodes", "many");
        assertThat(rows.get(4).get("nodes")).isEqualTo("many");
    }

    @Test
    void aBadDesignExitsTwo(@TempDir Path tmp) throws IOException {
        assertThat(batch(tmp, "{ not json")).isEqualTo(2);
        assertThat(batch(tmp, "[]")).isEqualTo(2);
        assertThat(batch(tmp, "{\"fixed\": {\"node\": 5}, \"seeds\": [1]}")).isEqualTo(2);
        assertThat(err.toString()).contains("unknown parameter \"node\"");
        assertThat(batch(tmp, "{\"fixed\": {\"seed\": 5}, \"seeds\": [1]}")).isEqualTo(2);
        assertThat(batch(tmp, "{\"fixed\": {\"nodes\": 5}}")).isEqualTo(2);
        assertThat(batch(tmp, "{\"fixed\": {\"nodes\": 5}, \"seeds\": [1.5]}")).isEqualTo(2);
        assertThat(batch(tmp, "{\"factors\": {\"nodes\": []}, \"seeds\": [1]}")).isEqualTo(2);
        assertThat(batch(tmp, "{\"fixed\": {\"nodes\": 5}, \"factors\": {\"nodes\": [6]}, \"seeds\": [1]}"))
                .isEqualTo(2);
        assertThat(batch(tmp, "{\"fixed\": {\"nodes\": [5]}, \"seeds\": [1]}")).isEqualTo(2);
        assertThat(batch(tmp, "{\"factor\": {}, \"seeds\": [1]}")).isEqualTo(2);
        assertThat(tmp.resolve("runs").resolve("index.csv")).doesNotExist();
        assertThat(run("--design", tmp.resolve("absent.json").toString(), "--out-dir", tmp.toString()))
                .isEqualTo(2);
    }
}
