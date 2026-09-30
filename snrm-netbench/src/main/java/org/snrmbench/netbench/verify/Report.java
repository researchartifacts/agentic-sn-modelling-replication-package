package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.validate.Finding;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The result of verify. */
public record Report(String toolVersion, String verifiedAt, String truth, String candidate, String runId,
                     JsonNode manifest, Envelope envelope, L0 l0, L1 l1, L2 l2, Decoys decoys, double ncs,
                     SnrmL0 snrm, L3 l3) {

    /** A report without SNRM results. */
    public Report(String toolVersion, String verifiedAt, String truth, String candidate, String runId,
                  JsonNode manifest, Envelope envelope, L0 l0, L1 l1, L2 l2, Decoys decoys, double ncs) {
        this(toolVersion, verifiedAt, truth, candidate, runId, manifest, envelope, l0, l1, l2, decoys, ncs, null, null);
    }

    /** How the candidate was loaded; {@code findings} holds the envelope findings only. */
    public record Envelope(String source, List<String> filesFound, List<String> filesMissing,
                           List<Finding> findings) {
    }

    /** SnrmRules over the candidate, with the envelope findings appended. */
    public record L0(boolean importValid, long errorCount, long warningCount, List<Finding> findings) {
    }

    /** SNRM's own validation of the candidate. */
    public record SnrmL0(JsonNode report, Boolean importValid, List<Disagreement> disagreements) {
    }

    /** One validation code counted differently by L0 ({@code ours}) and by SNRM. */
    public record Disagreement(String code, int ours, int snrm) {
    }

    /** Structural counts of one sheet; every undefined ratio is 0.0. */
    public record Structure(int truth, int candidate, int matched, double precision, double recall, double f1) {
    }

    /** L1. Missing and extra lists are raw (a reversed link is both missing and extra); reversedLinks names the truth links C has the wrong way round. */
    public record L1(Structure nodes, Structure links, double linkRecallConditional, Structure products,
                     Structure nodeProducts, double nodeTypeAccuracy, double structureF1, List<String> missingNodes,
                     List<String> extraNodes, List<String> missingLinks, List<String> extraLinks,
                     List<String> reversedLinks) {
    }

    /** One non-correct classification, or one fabricated entity. */
    public record Mismatch(String kind, String entity, String field, String cls, String truth, String truthUnit,
                           String candidate, String candidateUnit, Integer sentence) {
    }

    public record L2(double factRecall, double factPrecision, int statedFacts, int correctFacts, int fabricatedFacts,
                     Map<String, Map<String, Integer>> perField, Map<String, List<String>> fabricated,
                     List<Mismatch> conventionViolations, List<Mismatch> mismatches, BigDecimal tolerance,
                     boolean lenientNumbers) {
    }

    /** A decoy value C absorbed, as the decoy ledger states it. */
    public record DecoyValue(String entity, String field, String value, String unit, Integer sentence) {
    }

    public record Decoys(int total, List<String> entitiesAbsorbed, List<DecoyValue> valuesAbsorbed) {
    }

    public boolean importValid() {
        return l0.importValid();
    }

    public double structureF1() {
        return l1.structureF1();
    }

    public double factRecall() {
        return l2.factRecall();
    }

    public double factPrecision() {
        return l2.factPrecision();
    }

    /** Fabricated entities of every kind. */
    public int fabricatedCount() {
        return l2.fabricated().values().stream().mapToInt(List::size).sum();
    }

    public int conventionViolationCount() {
        return l2.conventionViolations().size();
    }

    public Report withSnrm(SnrmL0 value) {
        return new Report(toolVersion, verifiedAt, truth, candidate, runId, manifest, envelope, l0, l1, l2, decoys, ncs,
                value, l3);
    }

    public Report withL3(L3 value) {
        return new Report(toolVersion, verifiedAt, truth, candidate, runId, manifest, envelope, l0, l1, l2, decoys, ncs,
                snrm, value);
    }

    /** The {@code report.json} text. */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("tool_version", toolVersion);
        root.put("verified_at", verifiedAt);
        root.put("truth", truth);
        root.put("candidate", candidate);
        root.put("run_id", runId);
        root.put("manifest", manifest);
        root.put("envelope", envelopeJson());
        root.put("l0", l0Json());
        root.put("l1", l1Json());
        root.put("l2", l2Json());
        root.put("decoys", decoysJson());
        root.put("l3", l3 == null ? null : l3.toJson());
        root.put("composite", Map.of("ncs", ncs));
        return Json.pretty(root);
    }

    private Map<String, Object> envelopeJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", envelope.source());
        m.put("files_found", envelope.filesFound());
        m.put("files_missing", envelope.filesMissing());
        m.put("findings", envelope.findings());
        return m;
    }

    private Map<String, Object> l0Json() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("import_valid", l0.importValid());
        m.put("error_count", l0.errorCount());
        m.put("warning_count", l0.warningCount());
        m.put("findings", l0.findings());
        m.put("snrm", snrm == null ? null : snrm.report());
        m.put("import_valid_snrm", snrm == null ? null : snrm.importValid());
        m.put("disagreements", snrm == null ? List.of() : snrm.disagreements().stream().map(d -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("code", d.code());
            v.put("ours", d.ours());
            v.put("snrm", d.snrm());
            return v;
        }).toList());
        return m;
    }

    private Map<String, Object> l1Json() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodes", structureJson(l1.nodes()));
        Map<String, Object> links = structureJson(l1.links());
        links.put("recall_conditional", l1.linkRecallConditional());
        m.put("links", links);
        m.put("products", structureJson(l1.products()));
        m.put("node_products", structureJson(l1.nodeProducts()));
        m.put("node_type_accuracy", l1.nodeTypeAccuracy());
        m.put("structure_f1", l1.structureF1());
        m.put("missing_nodes", l1.missingNodes());
        m.put("extra_nodes", l1.extraNodes());
        m.put("missing_links", l1.missingLinks());
        m.put("extra_links", l1.extraLinks());
        m.put("reversed_links", l1.reversedLinks());
        return m;
    }

    private static Map<String, Object> structureJson(Structure s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("truth", s.truth());
        m.put("candidate", s.candidate());
        m.put("matched", s.matched());
        m.put("precision", s.precision());
        m.put("recall", s.recall());
        m.put("f1", s.f1());
        return m;
    }

    private Map<String, Object> l2Json() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fact_recall", l2.factRecall());
        m.put("fact_precision", l2.factPrecision());
        m.put("per_field", l2.perField());
        m.put("fabricated", l2.fabricated());
        m.put("convention_violations", l2.conventionViolations().stream().map(Report::mismatchJson).toList());
        m.put("mismatches", l2.mismatches().stream().map(Report::mismatchJson).toList());
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("tolerance", l2.tolerance());
        settings.put("lenient_numbers", l2.lenientNumbers());
        m.put("settings", settings);
        return m;
    }

    private static Map<String, Object> mismatchJson(Mismatch x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", x.kind());
        m.put("entity", x.entity());
        m.put("field", x.field());
        m.put("class", x.cls());
        m.put("truth", x.truth());
        m.put("truth_unit", x.truthUnit());
        m.put("candidate", x.candidate());
        m.put("candidate_unit", x.candidateUnit());
        m.put("sentence", x.sentence());
        return m;
    }

    private Map<String, Object> decoysJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decoy_total", decoys.total());
        m.put("decoy_entities_absorbed", decoys.entitiesAbsorbed());
        m.put("decoy_values_absorbed", decoys.valuesAbsorbed().stream().map(d -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("entity", d.entity());
            v.put("field", d.field());
            v.put("value", d.value());
            v.put("unit", d.unit());
            v.put("sentence", d.sentence());
            return v;
        }).toList());
        return m;
    }

    /** Every scalar metric, flat and in a fixed key order: L0 validity and counts. */
    public Map<String, Object> scalars() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("import_valid", l0.importValid());
        m.put("error_count", l0.errorCount());
        m.put("warning_count", l0.warningCount());
        m.put("import_valid_snrm", snrm == null ? null : snrm.importValid());
        m.put("disagreement_count", snrm == null ? null : snrm.disagreements().size());
        structureScalars(m, "nodes", l1.nodes());
        structureScalars(m, "links", l1.links());
        m.put("links_recall_conditional", l1.linkRecallConditional());
        structureScalars(m, "products", l1.products());
        structureScalars(m, "node_products", l1.nodeProducts());
        m.put("node_type_accuracy", l1.nodeTypeAccuracy());
        m.put("structure_f1", l1.structureF1());
        m.put("fact_recall", l2.factRecall());
        m.put("fact_precision", l2.factPrecision());
        m.put("facts_stated", l2.statedFacts());
        for (FactClass c : FactClass.perField()) {
            m.put("class_" + c.id(), l2.perField().values().stream().mapToInt(f -> f.get(c.id())).sum());
        }
        l2.fabricated().forEach((sheet, names) -> m.put("fabricated_" + sheet, names.size()));
        m.put("fabricated_total", fabricatedCount());
        m.put("fabricated_facts", l2.fabricatedFacts());
        m.put("convention_violations", conventionViolationCount());
        m.put("decoy_total", decoys.total());
        m.put("decoy_entities_absorbed", decoys.entitiesAbsorbed().size());
        m.put("decoy_values_absorbed", decoys.valuesAbsorbed().size());
        m.put("l3_status", l3 == null ? null : l3.status());
        m.put("behavioural_equivalence", l3 == null ? null : l3.behaviouralEquivalence());
        for (String code : L3.SIMULATION_CODES) {
            m.put("sim_rel_" + code, l3 == null ? null : l3.simulationRelDiff(code));
        }
        m.put("ncs", ncs);
        return m;
    }

    private static void structureScalars(Map<String, Object> m, String prefix, Structure s) {
        m.put(prefix + "_truth", s.truth());
        m.put(prefix + "_candidate", s.candidate());
        m.put(prefix + "_matched", s.matched());
        m.put(prefix + "_precision", s.precision());
        m.put(prefix + "_recall", s.recall());
        m.put(prefix + "_f1", s.f1());
    }
}
