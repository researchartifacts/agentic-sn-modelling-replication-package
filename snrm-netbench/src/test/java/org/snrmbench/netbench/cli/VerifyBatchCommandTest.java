package org.snrmbench.netbench.cli;

import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** verify-batch: every rep directory becomes one row of a results.csv that parses into rows of equal width, with the status and scores each candidate deserves. */
class VerifyBatchCommandTest {

    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        CommandLine cmd = new CommandLine(new VerifyBatchCommand());
        cmd.setOut(new PrintWriter(new StringWriter(), true));
        cmd.setErr(new PrintWriter(err, true));
        return cmd.execute(args);
    }

    /** A response in the envelope the instruction file asks for: ### headings and fenced csv blocks. */
    private static String envelope(Generator.Generated g) {
        StringBuilder sb = new StringBuilder("Here are the files.\n\n");
        for (Sheet s : Sheet.values()) {
            sb.append("### ").append(s.fileName()).append("\n```csv\n")
                    .append(g.files().get(Generator.TRUTH_DIR + "/" + s.fileName())).append("```\n\n");
        }
        return sb.toString();
    }

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target);
                }
            }
        }
    }

    @Test
    void verifiesEveryRepIntoOneFlatTable(@TempDir Path tmp) throws IOException {
        Path runs = tmp.resolve("runs");
        Generator.Generated g1 = Generator.build(GenerateParams.defaults(1, 7, 3, "Widget"), null);
        Generator.Generated g2 = Generator.build(GenerateParams.defaults(2, 7, 3, "Widget"), null);
        Path run1 = Generator.write(g1, runs, false);
        Generator.write(g2, runs, false);
        copyTree(run1, runs.resolve("broken-run"));
        Path brokenNodes = runs.resolve("broken-run").resolve("truth").resolve("nodes.csv");
        Files.writeString(brokenNodes, Files.readString(brokenNodes).replaceFirst(",CUSTOMER,", ",WAREHOUSE,"));

        Path responses = tmp.resolve("responses");
        String id1 = g1.runId();
        String id2 = g2.runId();
        copyTree(run1.resolve("truth"), responses.resolve("m1").resolve(id1).resolve("1").resolve("candidate"));
        write(responses.resolve("m1").resolve(id1).resolve("2").resolve("answer.md"), envelope(g1));
        write(responses.resolve("m1").resolve(id2).resolve("1").resolve("response.md"), envelope(g2));
        write(responses.resolve("m1").resolve(id2).resolve("1").resolve("call.json"), """
                {"model": "m1-large", "input_tokens": 1234, "latency_s": 2.5, "ok": true, "stop": null,
                 "note": "line one\\nline two", "usage": {"output_tokens": 99}}
                """);
        write(responses.resolve("m1").resolve(id2).resolve("2").resolve("response.md"),
                "I am sorry, I cannot produce these files.\n");
        Files.createDirectories(responses.resolve("m2").resolve(id1).resolve("1"));
        copyTree(run1.resolve("truth"), responses.resolve("m2").resolve("broken-run").resolve("1")
                .resolve("candidate"));
        write(responses.resolve("m2").resolve("s9-missing").resolve("1").resolve("response.md"), envelope(g1));
        Path out = tmp.resolve("out").resolve("results.csv");

        int code = run("--runs", runs.toString(), "--responses", responses.toString(), "--out", out.toString());

        assertThat(code).isZero();
        String csv = Files.readString(out, StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("\r").endsWith("\n");
        List<String> header;
        List<CSVRecord> rows;
        try (CSVParser p = CSVParser.parse(csv, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .build())) {
            header = p.getHeaderNames();
            rows = p.getRecords();
        }
        assertThat(header).startsWith("model", "run_id", "rep", "status", "seed", "nodes", "echelons")
                .contains("import_valid", "fact_recall", "structure_f1", "ncs", "l0_" + Code.values()[0].name(),
                        "l0_FILE_MISSING")
                .endsWith("call_input_tokens", "call_latency_s", "call_model", "call_note", "call_ok", "call_stop")
                .doesNotHaveDuplicates()
                .doesNotContain("call_usage");
        assertThat(header.subList(4, 4 + BatchCommand.parameterNames().size()))
                .containsExactlyElementsOf(BatchCommand.parameterNames());
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.isConsistent()).isTrue();
            assertThat(r.size()).isEqualTo(header.size());
        });

        assertThat(rows).extracting(r -> r.get("model") + "/" + r.get("run_id") + "/" + r.get("rep")).containsExactly(
                "m1/" + id1 + "/1", "m1/" + id1 + "/2", "m1/" + id2 + "/1", "m1/" + id2 + "/2",
                "m2/broken-run/1", "m2/" + id1 + "/1", "m2/s9-missing/1");
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("scored", "scored", "scored", "scored",
                "truth_invalid", "no_candidate", "missing_truth");
        assertThat(rows).extracting(r -> r.get("fact_recall")).containsExactly("1.0", "1.0", "1.0", "0.0", "", "",
                "");
        assertThat(rows).extracting(r -> r.get("import_valid")).containsExactly("1", "1", "1", "0", "",
                "", "");
        assertThat(rows).extracting(r -> r.get("l0_FILE_MISSING")).containsExactly("0", "0", "0", "5", "", "", "");
        assertThat(rows).extracting(r -> r.get("nodes")).containsExactly("7", "7", "7", "7", "7", "7", "");
        assertThat(rows.get(0).get("seed")).isEqualTo("1");
        assertThat(rows.get(2).get("seed")).isEqualTo("2");

        CSVRecord withCall = rows.get(2);
        assertThat(withCall.get("call_input_tokens")).isEqualTo("1234");
        assertThat(withCall.get("call_latency_s")).isEqualTo("2.5");
        assertThat(withCall.get("call_model")).isEqualTo("m1-large");
        assertThat(withCall.get("call_note")).isEqualTo("line one line two");
        assertThat(withCall.get("call_ok")).isEqualTo("1");
        assertThat(withCall.get("call_stop")).isEmpty();
        assertThat(rows.get(0).get("call_model")).isEmpty();

        Path rep = responses.resolve("m1").resolve(id2).resolve("1");
        assertThat(rep.resolve("report.json")).exists();
        assertThat(rep.resolve("response.md.split").resolve("nodes.csv")).exists();
        assertThat(responses.resolve("m1").resolve(id1).resolve("2").resolve("answer.md.split")).isDirectory();
        assertThat(responses.resolve("m1").resolve(id2).resolve("2").resolve("report.json")).exists();
        assertThat(responses.resolve("m2").resolve(id1).resolve("1").resolve("report.json")).doesNotExist();
        assertThat(err.toString()).contains("m2/s9-missing/1: missing_truth", "m2/broken-run/1: truth_invalid");
    }

    @Test
    void badArgumentsExitTwo(@TempDir Path tmp) {
        String out = tmp.resolve("results.csv").toString();

        assertThat(run("--runs", tmp.resolve("absent").toString(), "--responses", tmp.toString(), "--out", out))
                .isEqualTo(2);
        assertThat(run("--runs", tmp.toString(), "--responses", tmp.resolve("absent").toString(), "--out", out))
                .isEqualTo(2);
        assertThat(run("--runs", tmp.toString(), "--responses", tmp.toString(), "--out", out, "--tolerance", "-1"))
                .isEqualTo(2);
        assertThat(tmp.resolve("results.csv")).doesNotExist();
    }

    @Test
    void anEmptyResponsesTreeStillWritesTheHeader(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("results.csv");

        assertThat(run("--runs", tmp.toString(), "--responses", tmp.toString(), "--out", out.toString())).isZero();

        String csv = Files.readString(out, StandardCharsets.UTF_8);
        assertThat(csv.lines().toList()).hasSize(1);
        assertThat(csv).contains(",fact_recall,", ",l0_FILE_MISSING,");
    }
}
