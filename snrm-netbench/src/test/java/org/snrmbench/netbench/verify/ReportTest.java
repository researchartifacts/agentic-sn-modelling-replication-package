package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.snrmbench.netbench.verify.CsvEdits.sample;
import static org.snrmbench.netbench.verify.CsvEdits.without;
import static org.assertj.core.api.Assertions.assertThat;

/** report.json carries exactly the specified keys; scalars is flat and finite. */
class ReportTest {

    private static Report report() throws Exception {
        Map<String, String> b = sample("minimal-6-node");
        JsonNode manifest = Json.mapper().readTree("{\"tool_version\":\"0.1.0\",\"run_id\":\"s1-test\",\"seed\":1,"
                + "\"params\":{\"nodes\":6},\"counts\":{\"nodes\":6},\"files\":{}}");
        return Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(b),
                CandidateLoader.fromFiles(without(b, Sheet.PRODUCTS), CandidateLoader.SOURCE_DIRECTORY), null, null,
                manifest, new BigDecimal("1e-6"), "truth-dir", "candidate-dir"));
    }

    private static List<String> keys(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }

    @Test
    void jsonHasTheSpecifiedKeyStructure() throws Exception {
        JsonNode j = Json.mapper().readTree(report().toJson());

        assertThat(keys(j)).containsExactly("tool_version", "verified_at", "truth", "candidate", "run_id", "manifest",
                "envelope", "l0", "l1", "l2", "decoys", "l3", "composite");
        assertThat(j.get("tool_version").asText()).isEqualTo("0.1.0");
        assertThat(j.get("verified_at").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        assertThat(j.get("truth").asText()).isEqualTo("truth-dir");
        assertThat(j.get("run_id").asText()).isEqualTo("s1-test");
        assertThat(keys(j.get("manifest"))).containsExactly("seed", "params", "counts");
        assertThat(keys(j.get("envelope"))).containsExactly("source", "files_found", "files_missing", "findings");
        assertThat(keys(j.get("l0"))).containsExactly("import_valid", "error_count", "warning_count", "findings",
                "snrm", "import_valid_snrm", "disagreements");
        assertThat(j.get("l0").get("snrm").isNull()).isTrue();
        JsonNode finding = j.get("l0").get("findings").get(0);
        assertThat(keys(finding)).startsWith("code", "severity")
                .isSubsetOf("code", "severity", "file", "line", "column", "element", "message");
        assertThat(keys(j.get("l1"))).containsExactly("nodes", "links", "products", "node_products",
                "node_type_accuracy", "structure_f1", "missing_nodes", "extra_nodes", "missing_links", "extra_links",
                "reversed_links");
        assertThat(keys(j.get("l1").get("nodes"))).containsExactly("truth", "candidate", "matched", "precision",
                "recall", "f1");
        assertThat(keys(j.get("l1").get("links"))).containsExactly("truth", "candidate", "matched", "precision",
                "recall", "f1", "recall_conditional");
        assertThat(keys(j.get("l2"))).containsExactly("fact_recall", "fact_precision", "per_field", "fabricated",
                "convention_violations", "mismatches", "settings");
        assertThat(keys(j.get("l2").get("per_field")))
                .containsExactlyElementsOf(Arrays.stream(Field.values()).map(Field::key).toList());
        assertThat(keys(j.get("l2").get("per_field").get("nodes.capacity"))).containsExactly("stated", "correct",
                "correct_blank", "convention_ok", "omitted", "wrong", "wrong_unit", "wrong_type", "wrong_direction",
                "filled_unstated", "convention_violation", "decoy_absorbed");
        assertThat(keys(j.get("l2").get("fabricated"))).containsExactly("nodes", "links", "products",
                "node_products");
        assertThat(keys(j.get("l2").get("mismatches").get(0))).containsExactly("kind", "entity", "field", "class",
                "truth", "truth_unit", "candidate", "candidate_unit", "sentence");
        assertThat(keys(j.get("decoys"))).containsExactly("decoy_total", "decoy_entities_absorbed",
                "decoy_values_absorbed");
        assertThat(j.get("l3").isNull()).isTrue();
        assertThat(keys(j.get("composite"))).containsExactly("ncs");
    }

    @Test
    void scalarsAreFlatAndFinite() throws Exception {
        Report r = report();
        Map<String, Object> s = r.scalars();

        assertThat(s).containsKeys("import_valid", "error_count", "warning_count", "nodes_f1", "links_f1",
                "links_recall_conditional", "structure_f1", "fact_recall", "fact_precision", "facts_stated",
                "class_omitted", "class_wrong_unit", "fabricated_nodes", "fabricated_total", "fabricated_facts",
                "convention_violations", "decoy_total", "ncs");
        // SNRM and L3 scalars are present in every report, null when not computed
        List<String> snrmKeys = new ArrayList<>(List.of("import_valid_snrm", "disagreement_count", "l3_status",
                "behavioural_equivalence"));
        L3.SIMULATION_CODES.forEach(c -> snrmKeys.add("sim_rel_" + c));
        assertThat(s).containsKeys(snrmKeys.toArray(String[]::new));
        snrmKeys.forEach(k -> assertThat(s.get(k)).as(k).isNull());
        s.forEach((k, v) -> {
            if (!snrmKeys.contains(k)) {
                assertThat(v).as(k).isInstanceOfAny(Boolean.class, Number.class);
            }
            if (v instanceof Double d) {
                assertThat(d).isFinite();
            }
        });
        assertThat(s.get("class_omitted")).isEqualTo(2);
        assertThat(s.get("fact_recall")).isEqualTo(r.factRecall());
    }
}
