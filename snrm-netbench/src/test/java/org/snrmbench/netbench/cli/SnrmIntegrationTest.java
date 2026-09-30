package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.verify.L3;
import org.snrmbench.netbench.verify.MockSnrm;
import org.snrmbench.netbench.verify.SnrmApi;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.AfterEach;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** SNRM integration end to end against a mock SNRM: generate's self-check 5, verify's l0.snrm cross-check and L3, verify-batch, and exit code 5. */
class SnrmIntegrationTest {

    private final MockSnrm mock = new MockSnrm();
    private final StringWriter err = new StringWriter();

    @AfterEach
    void stop() {
        mock.close();
    }

    private int run(Object command, String... args) {
        CommandLine cmd = new CommandLine(command);
        cmd.setOut(new PrintWriter(new StringWriter(), true));
        cmd.setErr(new PrintWriter(err, true));
        return cmd.execute(args);
    }

    private String[] login() {
        return new String[] {"--snrm-url", mock.url(), "--snrm-user", MockSnrm.USER, "--snrm-pass", MockSnrm.PASS};
    }

    private static String[] concat(String[]... parts) {
        return Stream.of(parts).flatMap(Stream::of).toArray(String[]::new);
    }

    private static final String[] GENERATE = {"--seed", "1", "--nodes", "12", "--echelons", "4",
            "--final-product-name", "Widget"};

    // ---- generate ---------------------------------------------------------------------------

    @Test
    void generateStoresSnrmsValidationOfBOutsideTheManifest(@TempDir Path tmp) throws IOException {
        int code = run(new GenerateCommand(), concat(GENERATE, login(), new String[] {"--out-dir",
                tmp.resolve("with").toString(), "--snrm-project-id", "7"}));
        int plain = run(new GenerateCommand(), concat(GENERATE, new String[] {"--out-dir",
                tmp.resolve("without").toString()}));

        assertThat(code).isZero();
        assertThat(plain).isZero();
        Path with = onlyChild(tmp.resolve("with"));
        Path without = onlyChild(tmp.resolve("without"));
        assertThat(with.getFileName()).isEqualTo(without.getFileName()); // --snrm-* never enters the run id
        assertThat(Files.readString(with.resolve("manifest.json")))
                .isEqualTo(Files.readString(without.resolve("manifest.json")))
                .doesNotContain("snrm");
        JsonNode stored = Json.read(with.resolve(GenerateCommand.SNRM_VALIDATION));
        assertThat(stored.get("valid").asBoolean()).isTrue();

        MockSnrm.Request r = mock.requests("POST", SnrmApi.IMPORT_PATH).get(0);
        assertThat(r.part("validateOnly")).isEqualTo("true");
        assertThat(r.part("projectId")).isEqualTo("7");
        assertThat(r.part("name")).isEqualTo(with.getFileName().toString());
        assertThat(r.parts().stream().filter(p -> p.fileName() != null).map(MockSnrm.Part::fileName))
                .containsExactly("network_meta.csv", "nodes.csv", "links.csv", "products.csv", "node_products.csv");
        assertThat(r.parts().get(1).value()).isEqualTo(Files.readString(with.resolve("truth").resolve("nodes.csv")));
    }

    @Test
    void generateExitsThreeAndWritesNothingWhenSnrmRejectsB(@TempDir Path tmp) {
        mock.validateOnlyValid = false;

        int code = run(new GenerateCommand(), concat(GENERATE, login(), new String[] {"--out-dir", tmp.toString()}));

        assertThat(code).isEqualTo(3);
        assertThat(tmp.toFile().list()).isEmpty();
    }

    @Test
    void generateExitsFiveAndWritesNothingWhenSnrmIsUnreachable(@TempDir Path tmp) {
        int code = run(new GenerateCommand(), concat(GENERATE, new String[] {"--snrm-url", MockSnrm.closedUrl(),
                "--snrm-token", "t", "--out-dir", tmp.toString()}));

        assertThat(code).isEqualTo(5);
        assertThat(tmp.toFile().list()).isEmpty();
    }

    @Test
    void incompleteSnrmFlagsAreBadArguments(@TempDir Path tmp) {
        String out = tmp.toString();
        assertThat(run(new GenerateCommand(), concat(GENERATE, new String[] {"--out-dir", out, "--snrm-token", "t"})))
                .isEqualTo(2);
        assertThat(run(new GenerateCommand(), concat(GENERATE, new String[] {"--out-dir", out, "--snrm-url",
                mock.url()}))).isEqualTo(2);
        assertThat(run(new GenerateCommand(), concat(GENERATE, new String[] {"--out-dir", out, "--snrm-url",
                mock.url(), "--snrm-token", "t", "--snrm-user", "u", "--snrm-pass", "p"}))).isEqualTo(2);
        Path sample = TestSupport.sample("minimal-6-node");
        String[] verify = {"--truth", sample.toString(), "--candidate", sample.toString(), "--out",
                tmp.resolve("r.json").toString()};
        assertThat(run(new VerifyCommand(), concat(verify, new String[] {"--l3"}))).isEqualTo(2);
        assertThat(run(new VerifyCommand(), concat(verify, login(), new String[] {"--keep"}))).isEqualTo(2);
        assertThat(run(new VerifyCommand(), concat(verify, new String[] {"--snrm-url", "localhost",
                "--snrm-token", "t"}))).isEqualTo(2);
        assertThat(tmp.toFile().list()).isEmpty();
        assertThat(mock.calls()).isEmpty();
    }

    // ---- verify: l0.snrm --------------------------------------------------------------------

    @Test
    void verifyStoresSnrmsReportAndTheDisagreements(@TempDir Path tmp) throws IOException {
        Path candidate = tmp.resolve("candidate");
        Files.createDirectories(candidate);
        for (String f : List.of("network_meta.csv", "nodes.csv", "links.csv", "node_products.csv")) {
            Files.copy(TestSupport.sample("minimal-6-node").resolve(f), candidate.resolve(f));
        }
        mock.validateOnlyWarnings = List.of("LATERAL_LINK");
        Path out = tmp.resolve("report.json");

        int code = run(new VerifyCommand(), concat(new String[] {"--truth",
                TestSupport.sample("minimal-6-node").toString(), "--candidate", candidate.toString(), "--out",
                out.toString()}, login()));

        assertThat(code).isZero();
        JsonNode l0 = Json.read(out).get("l0");
        assertThat(l0.get("snrm").get("valid").asBoolean()).isTrue();
        assertThat(l0.get("snrm").get("warnings").get(0).get("code").asText()).isEqualTo("LATERAL_LINK");
        assertThat(l0.get("import_valid_snrm").asBoolean()).isTrue();
        // ours: PRODUCT_CREATED_IMPLICITLY (SNRM: none); SNRM: LATERAL_LINK (ours: none); FILE_MISSING is ours only
        assertThat(l0.get("disagreements").toString()).isEqualTo(
                "[{\"code\":\"LATERAL_LINK\",\"ours\":0,\"snrm\":1},"
                        + "{\"code\":\"PRODUCT_CREATED_IMPLICITLY\",\"ours\":1,\"snrm\":0}]");
        assertThat(Json.read(out).get("l3").isNull()).isTrue();

        MockSnrm.Request r = mock.requests("POST", SnrmApi.IMPORT_PATH).get(0);
        assertThat(r.part("validateOnly")).isEqualTo("true");
        assertThat(r.part("projectId")).isNull();
        assertThat(r.parts().stream().filter(p -> p.fileName() != null).map(MockSnrm.Part::fileName))
                .containsExactly("links.csv", "network_meta.csv", "node_products.csv", "nodes.csv");
        assertThat(mock.calls()).containsExactly("POST " + SnrmApi.LOGIN_PATH, "POST " + SnrmApi.IMPORT_PATH);
    }

    @Test
    void aResponseFileIsSentAsItsSplitFiles(@TempDir Path tmp) throws IOException {
        Path response = tmp.resolve("response.md");
        StringBuilder sb = new StringBuilder("Here you go.\n\n");
        for (Sheet s : Sheet.values()) {
            sb.append("### ").append(s.fileName()).append("\n```csv\n")
                    .append(Files.readString(TestSupport.sample("minimal-6-node").resolve(s.fileName())))
                    .append("```\n\n");
        }
        Files.writeString(response, sb.toString(), StandardCharsets.UTF_8);

        int code = run(new VerifyCommand(), concat(new String[] {"--truth",
                TestSupport.sample("minimal-6-node").toString(), "--candidate", response.toString(), "--out",
                tmp.resolve("report.json").toString(), "--snrm-url", mock.url(), "--snrm-token", MockSnrm.TOKEN}));

        assertThat(code).isZero();
        List<MockSnrm.Part> files = mock.requests("POST", SnrmApi.IMPORT_PATH).get(0).parts().stream()
                .filter(p -> p.fileName() != null).toList();
        assertThat(files).extracting(MockSnrm.Part::fileName).containsExactly("network_meta.csv", "nodes.csv",
                "links.csv", "products.csv", "node_products.csv");
        for (MockSnrm.Part p : files) {
            assertThat(p.value()).isEqualTo(Files.readString(tmp.resolve("response.md.split").resolve(p.fileName())));
        }
        assertThat(Json.read(tmp.resolve("report.json")).get("l0").get("disagreements")).isEmpty();
    }

    @Test
    void verifyExitsFiveWithoutAReportWhenSnrmIsUnreachable(@TempDir Path tmp) {
        Path sample = TestSupport.sample("minimal-6-node");
        Path out = tmp.resolve("report.json");

        int code = run(new VerifyCommand(), "--truth", sample.toString(), "--candidate", sample.toString(), "--out",
                out.toString(), "--snrm-url", MockSnrm.closedUrl(), "--snrm-token", "t", "--l3");

        assertThat(code).isEqualTo(5);
        assertThat(out).doesNotExist();
        assertThat(err.toString()).contains("unreachable");
    }

    // ---- verify: L3 -------------------------------------------------------------------------

    private JsonNode verifyL3(Path tmp, String... extra) {
        Path sample = TestSupport.sample("minimal-6-node");
        Path out = tmp.resolve("report.json");
        int code = run(new VerifyCommand(), concat(new String[] {"--truth", sample.toString(), "--candidate",
                sample.toString(), "--out", out.toString(), "--l3"}, login(), extra));
        assertThat(code).isZero();
        return Json.read(out);
    }

    @Test
    void l3OnIdenticalMetricsIsBehaviourallyEquivalentAndCleansUp(@TempDir Path tmp) {
        JsonNode report = verifyL3(tmp);

        JsonNode l3 = report.get("l3");
        assertThat(fieldNames(l3)).containsExactly("status", "project_id", "truth_network_id",
                "candidate_network_id", "topological", "node_criticality", "simulation", "behavioural_equivalence",
                "cleaned_up");
        assertThat(l3.get("status").asText()).isEqualTo(L3.OK);
        assertThat(l3.get("behavioural_equivalence").asBoolean()).isTrue();
        assertThat(l3.get("cleaned_up").asBoolean()).isTrue();
        assertThat(fieldNames(l3.get("topological"))).containsExactlyElementsOf(L3.TOPOLOGICAL_CODES);
        assertThat(l3.get("topological").get("DENSITY").toString())
                .isEqualTo("{\"truth\":0.25,\"candidate\":0.25,\"abs_diff\":0.0,\"rel_diff\":0.0,\"missing\":null}");
        assertThat(l3.get("node_criticality")).hasSize(6);
        assertThat(l3.get("node_criticality").get(0).get("node").asText()).isEqualTo("CUST-1");
        assertThat(l3.get("node_criticality").get(0).get("truth").asDouble())
                .isEqualTo(MockSnrm.criticality("CUST-1"));
        assertThat(fieldNames(l3.get("simulation"))).containsExactlyElementsOf(L3.SIMULATION_CODES);
        assertThat(l3.get("simulation").get("AVG_PIPELINE").get("equivalent").asBoolean()).isTrue();

        // the project, the twins (truth first), the runs with SNRM's default safetyStockPriority
        String project = l3.get("project_id").asText();
        List<MockSnrm.Request> imports = mock.requests("POST", SnrmApi.IMPORT_PATH);
        assertThat(imports).extracting(r -> r.part("validateOnly")).containsExactly("true", "false", "false");
        assertThat(imports.get(1).part("name")).endsWith("-truth");
        assertThat(imports.get(2).part("name")).endsWith("-candidate");
        assertThat(imports.subList(1, 3)).allSatisfy(r -> {
            assertThat(r.part("projectId")).isEqualTo(project);
            for (MockSnrm.Part p : r.parts()) {
                if ("nodes.csv".equals(p.fileName()) || "links.csv".equals(p.fileName())) {
                    Sheet sheet = p.fileName().equals("nodes.csv") ? Sheet.NODES : Sheet.LINKS;
                    RawTable t = LenientCsvReader.readTable(sheet, p.fileName(), p.value());
                    t.rows().forEach(row -> assertThat(t.cell(row, sheet == Sheet.NODES ? Schema.NODE_FAILURE_PROB
                            : Schema.LINK_FAILURE_PROB)).isEqualTo("0"));
                }
            }
        });
        assertThat(mock.requests("POST", SnrmApi.PROJECTS_PATH).get(0).json().get("name").asText())
                .isEqualTo("netbench-l3-" + report.get("verified_at").asText().replace("-", "").replace(":", ""));
        List<MockSnrm.Request> runs = mock.requests("POST", SnrmApi.SIMULATIONS_PATH);
        assertThat(runs).hasSize(2).allSatisfy(r -> {
            assertThat(r.json().get("params").toString()).isEqualTo("{\"seed\":1,\"replications\":1,"
                    + "\"demandNoiseCv\":0,\"timingJitterPeriods\":0,\"recordElementTimeseries\":false}");
            assertThat(r.json().has("scenarioId")).isFalse();
        });
        assertThat(runs.get(0).json().get("networkId").asText()).isEqualTo(l3.get("truth_network_id").asText());

        List<String> deletes = mock.calls().stream().filter(c -> c.startsWith("DELETE")).toList();
        assertThat(deletes).hasSize(5);
        assertThat(deletes.subList(0, 2)).allMatch(c -> c.startsWith("DELETE /api/v1/simulations/"));
        assertThat(deletes.subList(2, 4)).containsExactly(
                "DELETE /api/v1/networks/" + l3.get("truth_network_id").asText(),
                "DELETE /api/v1/networks/" + l3.get("candidate_network_id").asText());
        assertThat(deletes.get(4)).isEqualTo("DELETE /api/v1/projects/" + project);

        assertThat(report.get("l0").get("import_valid_snrm").asBoolean()).isTrue();
    }

    @Test
    void keepDeletesNothingAndAGivenProjectIsUsedAsIs(@TempDir Path tmp) {
        JsonNode l3 = verifyL3(tmp, "--keep", "--snrm-project-id", "42").get("l3");

        assertThat(l3.get("status").asText()).isEqualTo(L3.OK);
        assertThat(l3.get("project_id").asText()).isEqualTo("42");
        assertThat(l3.get("cleaned_up").asBoolean()).isFalse();
        assertThat(mock.calls()).noneMatch(c -> c.startsWith("DELETE"))
                .doesNotContain("POST " + SnrmApi.PROJECTS_PATH);
        assertThat(mock.requests("POST", SnrmApi.IMPORT_PATH)).allSatisfy(r ->
                assertThat(r.part("projectId")).isEqualTo("42"));
    }

    @Test
    void aFivePercentCostDifferenceBreaksBehaviouralEquivalence(@TempDir Path tmp) {
        mock.candidateTotalCostFactor = 1.05;

        JsonNode l3 = verifyL3(tmp).get("l3");

        assertThat(l3.get("behavioural_equivalence").asBoolean()).isFalse();
        JsonNode cost = l3.get("simulation").get("TOTAL_COST");
        assertThat(cost.get("truth").asDouble()).isEqualTo(12000.0);
        assertThat(cost.get("candidate").asDouble()).isEqualTo(12600.0);
        assertThat(cost.get("rel_diff").asDouble()).isCloseTo(0.05, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(cost.get("equivalent").asBoolean()).isFalse();
        assertThat(l3.get("simulation").get("FILL_RATE").get("equivalent").asBoolean()).isTrue();
    }

    @Test
    void aCandidateSnrmRejectsIsNotImportableAndNothingIsComputed(@TempDir Path tmp) {
        mock.rejectImport = name -> name.endsWith("-candidate");

        JsonNode l3 = verifyL3(tmp).get("l3");

        assertThat(fieldNames(l3)).containsExactly("status", "snrm");
        assertThat(l3.get("status").asText()).isEqualTo(L3.CANDIDATE_NOT_IMPORTABLE);
        assertThat(l3.get("snrm").get("valid").asBoolean()).isFalse();
        assertThat(l3.get("snrm").get("errors").get(0).get("code").asText()).isEqualTo("NO_CUSTOMER_NODE");
        assertThat(mock.calls()).noneMatch(c -> c.contains("/metrics/") || c.contains("/simulations"));
        assertThat(mock.calls().stream().filter(c -> c.startsWith("DELETE")).toList()).hasSize(2); // truth network, project
    }

    @Test
    void anHttpErrorDuringL3IsRecordedAndTheReportStillWritten(@TempDir Path tmp) {
        mock.topologyStatus = 500;

        JsonNode l3 = verifyL3(tmp).get("l3");

        assertThat(fieldNames(l3)).containsExactly("status", "message");
        assertThat(l3.get("status").asText()).isEqualTo(L3.ERROR);
        assertThat(l3.get("message").asText()).contains("500", "metric engine failure");
        assertThat(mock.calls().stream().filter(c -> c.startsWith("DELETE")).toList()).hasSize(3);
        assertThat(err.toString()).contains("l3:", "500");
    }

    // ---- verify-batch -----------------------------------------------------------------------

    @Test
    void verifyBatchAddsTheSnrmColumnsAndLogsInOnce(@TempDir Path tmp) throws IOException {
        Path runs = tmp.resolve("runs");
        Path run = runs.resolve("r1");
        copyTree(TestSupport.sample("minimal-6-node"), run.resolve("truth"));
        Files.writeString(run.resolve("manifest.json"), "{\"run_id\":\"r1\",\"seed\":1,\"params\":{},\"counts\":{}}\n");
        Path responses = tmp.resolve("responses");
        copyTree(TestSupport.sample("minimal-6-node"), responses.resolve("m").resolve("r1").resolve("1")
                .resolve("candidate"));
        copyTree(TestSupport.sample("minimal-6-node"), responses.resolve("m").resolve("r1").resolve("2")
                .resolve("candidate"));
        mock.candidateTotalCostFactor = 1.05;
        Path out = tmp.resolve("results.csv");

        int code = run(new VerifyBatchCommand(), concat(new String[] {"--runs", runs.toString(), "--responses",
                responses.toString(), "--out", out.toString(), "--l3"}, login()));

        assertThat(code).isZero();
        List<CSVRecord> rows;
        List<String> header;
        try (CSVParser p = CSVParser.parse(Files.readString(out), CSVFormat.DEFAULT.builder().setHeader()
                .setSkipHeaderRecord(true).build())) {
            header = p.getHeaderNames();
            rows = p.getRecords();
        }
        List<String> expected = new ArrayList<>(List.of("import_valid_snrm", "disagreement_count", "l3_status",
                "behavioural_equivalence"));
        L3.SIMULATION_CODES.forEach(c -> expected.add("sim_rel_" + c));
        assertThat(header).containsAll(expected);
        assertThat(rows).hasSize(2).allSatisfy(r -> {
            assertThat(r.get("import_valid_snrm")).isEqualTo("1");
            assertThat(r.get("disagreement_count")).isEqualTo("0");
            assertThat(r.get("l3_status")).isEqualTo("ok");
            assertThat(r.get("behavioural_equivalence")).isEqualTo("0");
            assertThat(Double.parseDouble(r.get("sim_rel_TOTAL_COST"))).isCloseTo(0.05,
                    org.assertj.core.data.Offset.offset(1e-12));
            assertThat(r.get("sim_rel_FILL_RATE")).isEqualTo("0.0");
        });
        assertThat(mock.calls()).filteredOn(c -> c.equals("POST " + SnrmApi.LOGIN_PATH)).hasSize(1);
        assertThat(mock.requests("POST", SnrmApi.PROJECTS_PATH)).extracting(r -> r.json().get("name").asText())
                .containsOnly("netbench-l3-r1");
        assertThat(Json.read(responses.resolve("m").resolve("r1").resolve("1").resolve("report.json")).get("l3")
                .get("status").asText()).isEqualTo("ok");
    }

    @Test
    void verifyBatchExitsFiveWhenSnrmIsUnreachable(@TempDir Path tmp) throws IOException {
        Path runs = tmp.resolve("runs");
        copyTree(TestSupport.sample("minimal-6-node"), runs.resolve("r1").resolve("truth"));
        Path responses = tmp.resolve("responses");
        copyTree(TestSupport.sample("minimal-6-node"), responses.resolve("m").resolve("r1").resolve("1")
                .resolve("candidate"));
        Path out = tmp.resolve("results.csv");

        int code = run(new VerifyBatchCommand(), "--runs", runs.toString(), "--responses", responses.toString(),
                "--out", out.toString(), "--snrm-url", MockSnrm.closedUrl(), "--snrm-token", "t");

        assertThat(code).isEqualTo(5);
        assertThat(out).doesNotExist();
    }

    @Test
    void theResultsColumnsIncludeTheSnrmScalars() {
        List<String> columns = VerifyBatchCommand.scalarColumns();

        assertThat(columns).containsSubsequence("warning_count", "import_valid_snrm", "disagreement_count")
                .containsSubsequence("decoy_values_absorbed", "l3_status", "behavioural_equivalence",
                        "sim_rel_FILL_RATE", "sim_rel_AVG_PIPELINE", "ncs");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static List<String> fieldNames(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }

    private static Path onlyChild(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> children = s.filter(p -> !p.getFileName().toString().startsWith(".")).toList();
            assertThat(children).hasSize(1);
            return children.get(0);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (Stream<Path> s = Files.list(from)) {
            for (Path p : s.toList()) {
                Files.copy(p, to.resolve(p.getFileName().toString()));
            }
        }
    }
}
