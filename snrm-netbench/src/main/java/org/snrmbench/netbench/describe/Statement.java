package org.snrmbench.netbench.describe;

import java.util.List;

/** One sentence unit of a description: the unit that is ordered, shuffled, interleaved with noise and indexed in the ledger. */
public record Statement(String text, Kind kind, List<Integer> factIds, Integer decoyId) {

    public enum Kind {
        FACT, NOISE, DECOY
    }

    public Statement {
        factIds = List.copyOf(factIds);
    }

    public static Statement fact(String text, List<Integer> factIds) {
        return new Statement(text, Kind.FACT, factIds, null);
    }

    public static Statement noise(String text) {
        return new Statement(text, Kind.NOISE, List.of(), null);
    }

    public static Statement decoy(String text, int decoyId) {
        return new Statement(text, Kind.DECOY, List.of(), decoyId);
    }
}
