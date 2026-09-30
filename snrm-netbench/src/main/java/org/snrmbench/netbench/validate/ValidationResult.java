package org.snrmbench.netbench.validate;

import org.snrmbench.netbench.model.TimeBase;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The findings of one validation, in check order, and the time base the checks resolved units against (the file's own, or the fallback when network_meta is absent or broken). */
public record ValidationResult(List<Finding> findings, TimeBase timeBase) {

    public ValidationResult {
        findings = List.copyOf(findings);
    }

    public long errorCount() {
        return findings.stream().filter(Finding::isError).count();
    }

    public long warningCount() {
        return findings.size() - errorCount();
    }

    public boolean valid() {
        return errorCount() == 0;
    }

    /** Count per code for one severity, sorted by code name. */
    public Map<String, Integer> countsByCode(Severity severity) {
        Map<String, Integer> out = new TreeMap<>();
        for (Finding f : findings) {
            if (f.severity() == severity) {
                out.merge(f.code().name(), 1, Integer::sum);
            }
        }
        return out;
    }
}
