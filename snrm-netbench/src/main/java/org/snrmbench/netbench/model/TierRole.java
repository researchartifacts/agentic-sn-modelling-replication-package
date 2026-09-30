package org.snrmbench.netbench.model;

/** The role word a description uses for a tier, and the SNRM type it maps to. */
public enum TierRole {
    SUPPLIER("supplier", NodeType.SUPPLIER),
    SUPPLIER_TIER2("tier-2 supplier", NodeType.SUPPLIER),
    SUPPLIER_TIER1("tier-1 supplier", NodeType.SUPPLIER),
    PLANT("plant", NodeType.PLANT),
    PLANT_COMPONENT("component plant", NodeType.PLANT),
    PLANT_ASSEMBLY("assembly plant", NodeType.PLANT),
    DC("distribution centre", NodeType.DC),
    DC_CENTRAL("central distribution centre", NodeType.DC),
    DC_REGIONAL("regional distribution centre", NodeType.DC),
    CUSTOMER("customer", NodeType.CUSTOMER);

    private final String word;
    private final NodeType type;

    TierRole(String word, NodeType type) {
        this.word = word;
        this.type = type;
    }

    public String word() {
        return word;
    }

    /** "distribution centre" becomes "distribution centres"; every other role adds "s". */
    public String plural() {
        return word + "s";
    }

    /** Singular for exactly one, plural otherwise. */
    public String word(int count) {
        return count == 1 ? word : plural();
    }

    /** "a supplier", "an assembly plant". */
    public String withArticle() {
        return ("aeiou".indexOf(word.charAt(0)) >= 0 ? "an " : "a ") + word;
    }

    public NodeType type() {
        return type;
    }
}
