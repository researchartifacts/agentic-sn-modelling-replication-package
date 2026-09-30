package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.Fact;

import java.util.List;

/** A rendered description A with the ledger's sentence indices filled in. */
public record Description(String markdown, List<Fact> facts, List<Decoy> decoys, int headerSentences,
                          int factSentences, int noiseSentences, List<String> decoyEntities,
                          List<String> sentences) {

    public Description {
        facts = List.copyOf(facts);
        decoys = List.copyOf(decoys);
        decoyEntities = List.copyOf(decoyEntities);
        sentences = List.copyOf(sentences);
    }

    /** The text of a 1-based sentence index. */
    public String sentence(int index) {
        return sentences.get(index - 1);
    }
}
