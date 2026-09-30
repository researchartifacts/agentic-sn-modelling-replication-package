package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure parts of the SNRM integration: L0 disagreements and the L3 comparisons. */
class SnrmVerificationTest {

    private static JsonNode json(String s) throws Exception {
        return Json.mapper().readTree(s);
    }

    private static Finding finding(Code code) {
        return Finding.row(code, "nodes.csv", 2, null, "m");
    }

    @Test
    void disagreementsCompareSnrmCodesOnlyAndListCodesOnlySnrmRaises() throws Exception {
        List<Finding> ours = List.of(finding(Code.LATERAL_LINK), finding(Code.LATERAL_LINK),
                finding(Code.PRODUCT_CREATED_IMPLICITLY), finding(Code.FILE_MISSING),
                finding(Code.NETWORK_META_DEFAULTED), finding(Code.ENVELOPE_PARSE_ERROR));
        JsonNode snrm = json("""
                {"valid": true,
                 "errors": [],
                 "warnings": [{"code": "LATERAL_LINK"}, {"code": "PRODUCT_CREATED_IMPLICITLY"},
                              {"code": "PRODUCT_ALREADY_IN_PROJECT"}, {"code": "DUPLICATE_SHEET"}]}
                """);

        List<Report.Disagreement> d = SnrmVerification.disagreements(ours, snrm);

        assertThat(d).containsExactly(new Report.Disagreement("LATERAL_LINK", 2, 1),
                new Report.Disagreement("DUPLICATE_SHEET", 0, 1),
                new Report.Disagreement("PRODUCT_ALREADY_IN_PROJECT", 0, 1));
    }

    @Test
    void agreementAndAReportWithoutListsGiveNoDisagreement() throws Exception {
        assertThat(SnrmVerification.disagreements(List.of(finding(Code.SELF_LOOP)),
                json("{\"errors\":[{\"code\":\"SELF_LOOP\"}]}"))).isEmpty();
        assertThat(SnrmVerification.disagreements(List.of(), json("{\"valid\":true}"))).isEmpty();
    }

    @Test
    void comparisonsNeverTurnAMissingValueIntoZero() {
        L3.Comparison both = new L3.Comparison(200.0, 202.0);
        assertThat(both.absDiff()).isEqualTo(2.0);
        assertThat(both.relDiff()).isEqualTo(0.01);
        assertThat(both.missing()).isNull();
        assertThat(both.equivalent()).isTrue();
        assertThat(new L3.Comparison(200.0, 202.5).equivalent()).isFalse();

        L3.Comparison missing = new L3.Comparison(0.5, null);
        assertThat(missing.absDiff()).isNull();
        assertThat(missing.relDiff()).isNull();
        assertThat(missing.missing()).isEqualTo("candidate");
        assertThat(missing.equivalent()).isFalse();
        assertThat(new L3.Comparison(null, null).missing()).isEqualTo("both");

        assertThat(new L3.Comparison(0.0, 0.0).relDiff()).isEqualTo(0.0);
        assertThat(new L3.Comparison(0.0, 0.0).equivalent()).isTrue();
        assertThat(new L3.Comparison(0.0, 1.0).relDiff()).isNull();
        assertThat(new L3.Comparison(0.0, 1.0).equivalent()).isFalse();
    }

    @Test
    void behaviouralEquivalenceNeedsAllSevenMetrics() throws Exception {
        JsonNode all = json("[{\"code\":\"FILL_RATE\",\"value\":1},{\"code\":\"SERVICE_LEVEL\",\"value\":1},"
                + "{\"code\":\"MIN_FILL_RATE\",\"value\":1},{\"code\":\"TOTAL_COST\",\"value\":100},"
                + "{\"code\":\"CVAR_COST\",\"value\":100},{\"code\":\"AVG_INVENTORY\",\"value\":5},"
                + "{\"code\":\"AVG_PIPELINE\",\"value\":0}]");
        JsonNode sixOnly = json("{\"metrics\":[{\"code\":\"FILL_RATE\",\"value\":1},{\"code\":\"SERVICE_LEVEL\","
                + "\"value\":1},{\"code\":\"MIN_FILL_RATE\",\"value\":1},{\"code\":\"TOTAL_COST\",\"value\":100.5},"
                + "{\"code\":\"CVAR_COST\",\"value\":100},{\"code\":\"AVG_INVENTORY\",\"value\":5}]}");

        L3 same = L3.ok("p", "1", "2", java.util.Map.of(), List.of(),
                L3Runner.compareCodes(L3.SIMULATION_CODES, all, all));
        L3 lacking = L3.ok("p", "1", "2", java.util.Map.of(), List.of(),
                L3Runner.compareCodes(L3.SIMULATION_CODES, all, sixOnly));

        assertThat(same.behaviouralEquivalence()).isTrue();
        assertThat(lacking.behaviouralEquivalence()).isFalse();
        assertThat(lacking.simulation().get("AVG_PIPELINE").missing()).isEqualTo("candidate");
        assertThat(lacking.simulationRelDiff("TOTAL_COST")).isEqualTo(0.005);
        assertThat(lacking.simulationRelDiff("AVG_PIPELINE")).isNull();
    }

    @Test
    void nodeCriticalityIsMatchedByNormalisedNameThroughEachNetworksNodeIds() throws Exception {
        JsonNode truthNodes = json("[{\"id\":1,\"name\":\"DC-1\"},{\"id\":2,\"name\":\"SUP-1\"},{\"id\":3,\"name\":\"X\"}]");
        JsonNode truthTopo = json("[{\"code\":\"NODE_CRITICALITY\",\"scopeId\":1,\"value\":0.5},"
                + "{\"code\":\"NODE_CRITICALITY\",\"scopeId\":2,\"value\":1.0},"
                + "{\"code\":\"NODE_CRITICALITY\",\"scopeId\":3,\"value\":0.2},{\"code\":\"DENSITY\",\"value\":0.3}]");
        JsonNode candidateNodes = json("{\"nodes\":[{\"id\":10,\"name\":\"sup-1\"},{\"id\":11,\"name\":\" DC-1 \"}]}");
        JsonNode candidateTopo = json("{\"metrics\":[{\"code\":\"NODE_CRITICALITY\",\"scopeId\":10,\"value\":0.8}]}");

        List<L3.NodeComparison> c = L3Runner.compareCriticality(truthTopo, truthNodes, candidateTopo, candidateNodes);

        assertThat(c).containsExactly(
                new L3.NodeComparison("DC-1", new L3.Comparison(0.5, null)),
                new L3.NodeComparison("SUP-1", new L3.Comparison(1.0, 0.8)));
    }
}
