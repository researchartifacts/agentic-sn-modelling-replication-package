package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.verify.CsvEdits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** verify's exit codes, and the ledger/manifest defaults beside the truth directory. */
class VerifyCommandTest {

    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        CommandLine cmd = new CommandLine(new VerifyCommand());
        cmd.setErr(new PrintWriter(err, true));
        cmd.setOut(new PrintWriter(new StringWriter(), true));
        return cmd.execute(args);
    }

    private static Path writeFiles(Path dir, Map<String, String> files) throws Exception {
        Files.createDirectories(dir);
        for (Map.Entry<String, String> e : files.entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
        return dir;
    }

    @Test
    void writesTheReportAndExitsZero(@TempDir Path tmp) {
        Path sample = TestSupport.sample("minimal-6-node");
        Path out = tmp.resolve("nested").resolve("report.json");

        int code = run("--truth", sample.toString(), "--candidate", sample.toString(), "--out", out.toString());

        assertThat(code).isZero();
        JsonNode report = Json.read(out);
        assertThat(report.get("l0").get("import_valid").asBoolean()).isTrue();
        assertThat(report.get("l2").get("fact_recall").asDouble()).isEqualTo(1.0);
        assertThat(report.get("run_id").isNull()).isTrue();
    }

    @Test
    void picksUpTheLedgersAndManifestBesideTheTruth(@TempDir Path tmp) throws Exception {
        Path run = tmp.resolve("s1-run");
        Path truth = writeFiles(run.resolve("truth"), CsvEdits.sample("minimal-6-node"));
        Files.writeString(run.resolve("facts.jsonl"), "{\"id\":1,\"kind\":\"node\",\"entity\":\"SUP-1\","
                + "\"field\":\"capacity\",\"value\":\"500\",\"unit\":\"DAY\",\"stated\":true,\"relative\":false,"
                + "\"sentence\":4}\n", StandardCharsets.UTF_8);
        Files.writeString(run.resolve("decoys.jsonl"), "{\"id\":1,\"marker\":\"Until 2019, \",\"kind\":\"node\","
                + "\"entity\":\"DC-9\",\"field\":\"exists\",\"value\":\"DC\",\"unit\":null,\"decoy_entity\":true,"
                + "\"sentence\":12}\n", StandardCharsets.UTF_8);
        Files.writeString(run.resolve("manifest.json"),
                "{\"run_id\":\"s1-run\",\"seed\":1,\"params\":{\"nodes\":6},\"counts\":{\"nodes\":6}}\n",
                StandardCharsets.UTF_8);
        Path response = tmp.resolve("response.md");
        StringBuilder sb = new StringBuilder();
        CsvEdits.set(CsvEdits.sample("minimal-6-node"), Sheet.NODES, "SUP-1", Schema.NODE_CAPACITY_VALUE, "")
                .forEach((name, content) -> sb.append("### ").append(name).append("\n```csv\n")
                        .append(content.replace("\r\n", "\n")).append("```\n"));
        Files.writeString(response, sb.toString(), StandardCharsets.UTF_8);
        Path out = tmp.resolve("report.json");

        int code = run("--truth", truth.toString(), "--candidate", response.toString(), "--out", out.toString());

        assertThat(code).isZero();
        JsonNode report = Json.read(out);
        assertThat(report.get("run_id").asText()).isEqualTo("s1-run");
        assertThat(report.get("manifest").get("seed").asInt()).isEqualTo(1);
        assertThat(report.get("decoys").get("decoy_total").asInt()).isEqualTo(1);
        assertThat(report.get("envelope").get("source").asText()).isEqualTo("file");
        JsonNode mismatch = report.get("l2").get("mismatches").get(0);
        assertThat(mismatch.get("class").asText()).isEqualTo("omitted");
        assertThat(mismatch.get("sentence").asInt()).isEqualTo(4);
        assertThat(tmp.resolve("response.md.split").resolve("nodes.csv")).exists();
    }

    @Test
    void badArgumentsExitTwo(@TempDir Path tmp) {
        Path sample = TestSupport.sample("minimal-6-node");
        String out = tmp.resolve("report.json").toString();

        assertThat(run()).isEqualTo(2);
        assertThat(run("--truth", tmp.resolve("absent").toString(), "--candidate", sample.toString(), "--out", out))
                .isEqualTo(2);
        assertThat(run("--truth", sample.toString(), "--candidate", tmp.resolve("absent.md").toString(),
                "--out", out)).isEqualTo(2);
        assertThat(run("--truth", sample.toString(), "--candidate", sample.toString(), "--out", out,
                "--tolerance", "-1")).isEqualTo(2);
        assertThat(run("--truth", sample.toString(), "--candidate", sample.toString(), "--out", out,
                "--facts", tmp.resolve("absent.jsonl").toString())).isEqualTo(2);
        assertThat(tmp.resolve("report.json")).doesNotExist();
        assertThat(err.toString()).contains("absent");
    }

    @Test
    void anInvalidTruthExitsThreeAndPrintsItsFindings(@TempDir Path tmp) throws Exception {
        Path truth = writeFiles(tmp.resolve("truth"), CsvEdits.set(CsvEdits.sample("minimal-6-node"),
                Sheet.NODES, "DC-1", Schema.NODE_TYPE, "WAREHOUSE"));
        Path out = tmp.resolve("report.json");

        int code = run("--truth", truth.toString(), "--candidate", truth.toString(), "--out", out.toString());

        assertThat(code).isEqualTo(3);
        assertThat(err.toString()).contains("UNKNOWN_ENUM_VALUE", "WAREHOUSE");
        assertThat(out).doesNotExist();
    }
}
