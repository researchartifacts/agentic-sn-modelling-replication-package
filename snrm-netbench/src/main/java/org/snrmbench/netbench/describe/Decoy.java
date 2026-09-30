package org.snrmbench.netbench.describe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One adversarial sentence, a line of decoys.jsonl: a false fact marked historical, hypothetical or foreign. */
@JsonPropertyOrder({"id", "marker", "kind", "entity", "field", "value", "unit", "decoy_entity", "sentence"})
public record Decoy(int id, String marker, String kind, String entity, String field, String value, String unit,
                    @JsonProperty("decoy_entity") boolean decoyEntity, int sentence) {
}
