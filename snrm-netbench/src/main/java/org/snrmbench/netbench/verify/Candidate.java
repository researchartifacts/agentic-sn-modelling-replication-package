package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.validate.Finding;

import java.util.List;

/** A candidate network (C) as loaded, before any scoring. */
public record Candidate(RawNetwork network, String source, List<String> filesFound, List<String> filesMissing,
                        List<Finding> envelopeFindings) {

    public Candidate {
        filesFound = List.copyOf(filesFound);
        filesMissing = List.copyOf(filesMissing);
        envelopeFindings = List.copyOf(envelopeFindings);
    }
}
