package org.snrmbench.netbench.describe;

/** Every phrase of a fact statement that a style may word differently. */
public enum Slot {
    NODE("node", Templates.NODE),
    NODE_REGION("node-region", Templates.NODE_REGION),
    NODE_ATTRIBUTES("node-attributes", Templates.NODE_ATTRIBUTES),
    NODE_CAPACITY("node-capacity", Templates.NODE_CAPACITY),
    NODE_PROCESSING_TIME("node-processing-time", Templates.NODE_PROCESSING_TIME),
    NODE_FIXED_COST("node-fixed-cost", Templates.NODE_FIXED_COST),
    NODE_VAR_COST("node-var-cost", Templates.NODE_VAR_COST),
    NODE_FAILURE_PROB("node-failure-prob", Templates.NODE_FAILURE_PROB),
    GEO("geo", Templates.GEO),
    DEMAND("demand", Templates.DEMAND),
    INVENTORY("inventory", Templates.INVENTORY),
    INVENTORY_BARE("inventory-bare", Templates.INVENTORY_BARE),
    INVENTORY_INITIAL("inventory-initial", Templates.INVENTORY_INITIAL),
    INVENTORY_SAFETY("inventory-safety", Templates.INVENTORY_SAFETY),
    INVENTORY_HOLDING("inventory-holding", Templates.INVENTORY_HOLDING),
    LINK("link", Templates.LINK),
    LINK_LEAD_TIME("link-lead-time", Templates.LINK_LEAD_TIME),
    LINK_DISTANCE("link-distance", Templates.LINK_DISTANCE),
    LINK_CAPACITY("link-capacity", Templates.LINK_CAPACITY),
    LINK_UNIT_COST("link-unit-cost", Templates.LINK_UNIT_COST),
    LINK_FAILURE_PROB("link-failure-prob", Templates.LINK_FAILURE_PROB),
    LINK_GROUP("link-group", Templates.LINK_GROUP);

    private final String id;
    private final String template;

    Slot(String id, String template) {
        this.id = id;
        this.template = template;
    }

    /** The pool file name stem. */
    public String id() {
        return id;
    }

    /** The template-style wording. */
    public String template() {
        return template;
    }
}
