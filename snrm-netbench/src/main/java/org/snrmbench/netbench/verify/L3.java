package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The result of L3: SNRM's behaviour on the deterministic twins of B and C, compared metric by metric. */
public record L3(String status, String message, JsonNode snrm, String projectId, String truthNetworkId,
                 String candidateNetworkId, Map<String, Comparison> topological, List<NodeComparison> nodeCriticality,
                 Map<String, Comparison> simulation, Boolean behaviouralEquivalence, boolean cleanedUp) {

    public static final String OK = "ok";
    public static final String CANDIDATE_NOT_IMPORTABLE = "candidate_not_importable";
    public static final String ERROR = "error";

    /** The network-scoped topological codes compared. */
    public static final List<String> TOPOLOGICAL_CODES = List.of("DENSITY", "SPOF_NODE_COUNT", "SPOF_ARC_COUNT",
            "SPOF_COUNT", "AVG_PATH", "CLUSTERING", "ROBUSTNESS_RANDOM", "ROBUSTNESS_TARGETED");
    public static final String NODE_CRITICALITY = "NODE_CRITICALITY";
    /** The baseline-run metrics compared. */
    public static final List<String> SIMULATION_CODES = List.of("FILL_RATE", "SERVICE_LEVEL", "MIN_FILL_RATE",
            "TOTAL_COST", "CVAR_COST", "AVG_INVENTORY", "AVG_PIPELINE");
    /** Behavioural equivalence: every simulation metric within 1% of B's value, relative to |B|. */
    public static final double EQUIVALENCE_TOLERANCE = 0.01;

    /** One metric on both sides; a side is null when SNRM gave no value for it (never read as 0). */
    public record Comparison(Double truth, Double candidate) {

        public Double absDiff() {
            return truth == null || candidate == null ? null : Math.abs(candidate - truth);
        }

        /** |C − B| / |B|; when B is 0: 0 if C is 0 too, else undefined (null). */
        public Double relDiff() {
            if (truth == null || candidate == null) {
                return null;
            }
            if (truth == 0) {
                return candidate == 0 ? 0.0 : null;
            }
            return Math.abs(candidate - truth) / Math.abs(truth);
        }

        /** {@code truth}, {@code candidate} or {@code both} when a side has no value; null otherwise. */
        public String missing() {
            return truth == null && candidate == null ? "both" : truth == null ? "truth"
                    : candidate == null ? "candidate" : null;
        }

        /** Both present and within the equivalence tolerance of B (B = 0: only C = 0). */
        public boolean equivalent() {
            if (truth == null || candidate == null) {
                return false;
            }
            return truth == 0 ? candidate == 0 : Math.abs(candidate - truth) <= EQUIVALENCE_TOLERANCE * Math.abs(truth);
        }
    }

    /** NODE_CRITICALITY of one node, named as the truth names it. */
    public record NodeComparison(String node, Comparison values) {
    }

    public static L3 error(String message) {
        return new L3(ERROR, message, null, null, null, null, null, null, null, null, false);
    }

    public static L3 candidateNotImportable(JsonNode report) {
        return new L3(CANDIDATE_NOT_IMPORTABLE, null, report, null, null, null, null, null, null, null, false);
    }

    /** A completed comparison; behavioural equivalence = every simulation metric {@link Comparison#equivalent}. */
    public static L3 ok(String projectId, String truthNetworkId, String candidateNetworkId,
                        Map<String, Comparison> topological, List<NodeComparison> nodeCriticality,
                        Map<String, Comparison> simulation) {
        boolean equivalent = SIMULATION_CODES.stream().allMatch(c -> simulation.get(c).equivalent());
        return new L3(OK, null, null, projectId, truthNetworkId, candidateNetworkId, topological, nodeCriticality,
                simulation, equivalent, false);
    }

    public L3 withCleanedUp(boolean cleaned) {
        return new L3(status, message, snrm, projectId, truthNetworkId, candidateNetworkId, topological,
                nodeCriticality, simulation, behaviouralEquivalence, cleaned);
    }

    /** The relative difference of a simulation metric, or null (not ok, missing, or undefined). */
    public Double simulationRelDiff(String code) {
        Comparison c = simulation == null ? null : simulation.get(code);
        return c == null ? null : c.relDiff();
    }

    /** The {@code l3} object of report.json. */
    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        switch (status) {
            case ERROR -> m.put("message", message);
            case CANDIDATE_NOT_IMPORTABLE -> m.put("snrm", snrm);
            default -> {
                m.put("project_id", projectId);
                m.put("truth_network_id", truthNetworkId);
                m.put("candidate_network_id", candidateNetworkId);
                Map<String, Object> topo = new LinkedHashMap<>();
                topological.forEach((code, c) -> topo.put(code, comparisonJson(c)));
                m.put("topological", topo);
                m.put("node_criticality", nodeCriticality.stream().map(n -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("node", n.node());
                    v.putAll(comparisonJson(n.values()));
                    return v;
                }).toList());
                Map<String, Object> sim = new LinkedHashMap<>();
                simulation.forEach((code, c) -> {
                    Map<String, Object> v = comparisonJson(c);
                    v.put("equivalent", c.missing() == null ? c.equivalent() : null);
                    sim.put(code, v);
                });
                m.put("simulation", sim);
                m.put("behavioural_equivalence", behaviouralEquivalence);
                m.put("cleaned_up", cleanedUp);
            }
        }
        return m;
    }

    private static Map<String, Object> comparisonJson(Comparison c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("truth", c.truth());
        m.put("candidate", c.candidate());
        m.put("abs_diff", c.absDiff());
        m.put("rel_diff", c.relDiff());
        m.put("missing", c.missing());
        return m;
    }
}
