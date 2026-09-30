package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.verify.SnrmClient.SnrmException;
import org.snrmbench.netbench.verify.SnrmClient.SnrmUnreachable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The SNRM side of verify: SNRM's own validation of the candidate (validateOnly=true) stored under l0.snrm, its disagreements with this tool's L0, and, when asked, L3. Only an unreachable SNRM escapes as an exception. */
public final class SnrmVerification {

    /** This tool's envelope codes: SNRM never raises them, so they are not compared. */
    private static final Set<Code> OWN_CODES = EnumSet.of(Code.ENVELOPE_PARSE_ERROR, Code.FILE_MISSING,
            Code.NETWORK_META_DEFAULTED);

    public record Settings(String projectId, boolean l3, boolean keep) {
    }

    private SnrmVerification() {
    }

    /** Adds SNRM's validation of C (and L3 when asked) to a report. */
    public static Report apply(SnrmClient client, Report report, Map<String, String> candidateFiles, RawNetwork truth,
                               RawNetwork candidate, Settings settings) {
        String label = label(report);
        Report out = report.withSnrm(check(client, report.l0().findings(), candidateFiles,
                "netbench-l0-" + label + "-candidate", settings.projectId()));
        if (settings.l3()) {
            out = out.withL3(L3Runner.run(client, truth, candidate, settings.projectId(), label, settings.keep()));
        }
        return out;
    }

    /** The run id, else the verification time as {@code yyyyMMddTHHmmssZ}: names what SNRM is sent. */
    static String label(Report report) {
        return report.runId() != null ? report.runId() : report.verifiedAt().replace("-", "").replace(":", "");
    }

    /** POSTs C with validateOnly=true. */
    static Report.SnrmL0 check(SnrmClient client, List<Finding> ours, Map<String, String> files, String name,
                               String projectId) {
        JsonNode report;
        try {
            report = client.importNetwork(files, name, projectId, true);
        } catch (SnrmUnreachable e) {
            throw e;
        } catch (SnrmException e) {
            return new Report.SnrmL0(Json.mapper().createObjectNode().put("error", e.getMessage()), null, List.of());
        }
        JsonNode valid = report.get(SnrmApi.REPORT_VALID);
        return new Report.SnrmL0(report, valid != null && valid.isBoolean() ? valid.asBoolean() : null,
                disagreements(ours, report));
    }

    /** Every validation code whose count differs between this tool's L0 (SNRM's codes only) and SNRM's errors[] plus warnings[]: this tool's codes in declaration order, then codes only SNRM raises, alphabetically. */
    static List<Report.Disagreement> disagreements(List<Finding> ours, JsonNode snrm) {
        Map<String, Integer> mine = new LinkedHashMap<>();
        for (Code c : Code.values()) {
            if (!OWN_CODES.contains(c)) {
                mine.put(c.name(), 0);
            }
        }
        for (Finding f : ours) {
            mine.computeIfPresent(f.code().name(), (k, n) -> n + 1);
        }
        Map<String, Integer> theirs = new TreeMap<>();
        for (String field : List.of(SnrmApi.REPORT_ERRORS, SnrmApi.REPORT_WARNINGS)) {
            snrm.path(field).forEach(item -> theirs.merge(item.path(SnrmApi.REPORT_ITEM_CODE).asText(""), 1,
                    Integer::sum));
        }
        List<Report.Disagreement> out = new ArrayList<>();
        mine.forEach((code, n) -> {
            int s = theirs.getOrDefault(code, 0);
            if (n != s) {
                out.add(new Report.Disagreement(code, n, s));
            }
        });
        theirs.forEach((code, s) -> {
            if (!mine.containsKey(code)) {
                out.add(new Report.Disagreement(code, 0, s));
            }
        });
        return out;
    }

    /** The report's {@code valid} flag; anything but {@code true} is not valid. */
    public static boolean valid(JsonNode report) {
        return report.path(SnrmApi.REPORT_VALID).asBoolean(false);
    }

    /** The report's {@code errors[]}, compactly, for a message. */
    public static String errors(JsonNode report) {
        JsonNode errors = report.path(SnrmApi.REPORT_ERRORS);
        return errors.isMissingNode() ? report.toString() : errors.toString();
    }
}
