package org.snrmbench.netbench.generate;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One atomic fact of the ledger, a line of facts.jsonl. */
@JsonPropertyOrder({"id", "kind", "entity", "field", "value", "unit", "stated", "relative", "sentence"})
public record Fact(int id, String kind, String entity, String field, String value, String unit, boolean stated,
                   boolean relative, Integer sentence) {

    /** The entity key used for meta facts. */
    public static final String META_ENTITY = "network_meta";

    public Fact withSentence(Integer newSentence) {
        return new Fact(id, kind, entity, field, value, unit, stated, relative, newSentence);
    }

    public Fact withStated(boolean newStated) {
        return new Fact(id, kind, entity, field, value, unit, newStated, relative, newStated ? sentence : null);
    }

    public Fact withRelative(boolean newRelative) {
        return new Fact(id, kind, entity, field, value, unit, stated, newRelative, sentence);
    }
}
