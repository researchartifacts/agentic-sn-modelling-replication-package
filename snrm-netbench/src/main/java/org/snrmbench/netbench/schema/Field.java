package org.snrmbench.netbench.schema;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** A scored fact type: one field of one kind of entity. */
public enum Field {
    META_NAME(Sheet.NETWORK_META, "name", Type.TEXT, Schema.META_NAME, null, false),
    META_PERIOD_LENGTH(Sheet.NETWORK_META, "period_length", Type.DURATION, Schema.META_PERIOD_VALUE,
            Schema.META_PERIOD_UNIT, false),
    META_HORIZON(Sheet.NETWORK_META, "horizon_periods", Type.NUMBER, Schema.META_HORIZON, null, false),
    META_ROUNDING(Sheet.NETWORK_META, "rounding_policy", Type.ENUM, Schema.META_ROUNDING, null, false),

    PRODUCT_EXISTS(Sheet.PRODUCTS, "exists", Type.EXISTS, null, null, false),
    PRODUCT_UNIT_VALUE(Sheet.PRODUCTS, "unit_value", Type.NUMBER, Schema.PRODUCT_UNIT_VALUE, null, false),

    NODE_EXISTS(Sheet.NODES, "exists", Type.NODE_TYPE, Schema.NODE_TYPE, null, false),
    NODE_REGION(Sheet.NODES, "region", Type.TEXT, Schema.NODE_REGION, null, true),
    NODE_CAPACITY(Sheet.NODES, "capacity", Type.RATE, Schema.NODE_CAPACITY_VALUE, Schema.NODE_CAPACITY_UNIT, true),
    NODE_PROCESSING_TIME(Sheet.NODES, "processing_time", Type.DURATION, Schema.NODE_PROCESSING_VALUE,
            Schema.NODE_PROCESSING_UNIT, true),
    NODE_FIXED_COST(Sheet.NODES, "fixed_cost", Type.NUMBER, Schema.NODE_FIXED_COST, null, true),
    NODE_VAR_COST(Sheet.NODES, "var_cost", Type.NUMBER, Schema.NODE_VAR_COST, null, true),
    NODE_FAILURE_PROB(Sheet.NODES, "failure_prob", Type.NUMBER, Schema.NODE_FAILURE_PROB, null, true),
    NODE_GEO(Sheet.NODES, "geo", Type.GEO, Schema.NODE_LAT, Schema.NODE_LNG, true),

    LINK_EXISTS(Sheet.LINKS, "exists", Type.EXISTS, null, null, false),
    LINK_LEAD_TIME(Sheet.LINKS, "lead_time", Type.DURATION, Schema.LINK_LEAD_VALUE, Schema.LINK_LEAD_UNIT, false),
    LINK_CAPACITY(Sheet.LINKS, "capacity", Type.RATE, Schema.LINK_CAPACITY_VALUE, Schema.LINK_CAPACITY_UNIT, true),
    LINK_UNIT_COST(Sheet.LINKS, "unit_cost", Type.NUMBER, Schema.LINK_UNIT_COST, null, true),
    LINK_FAILURE_PROB(Sheet.LINKS, "failure_prob", Type.NUMBER, Schema.LINK_FAILURE_PROB, null, true),

    NP_EXISTS(Sheet.NODE_PRODUCTS, "exists", Type.EXISTS, null, null, false),
    NP_DEMAND(Sheet.NODE_PRODUCTS, "demand", Type.RATE, Schema.NP_DEMAND_VALUE, Schema.NP_DEMAND_UNIT, false),
    NP_INITIAL_INVENTORY(Sheet.NODE_PRODUCTS, "initial_inventory", Type.NUMBER, Schema.NP_INITIAL_INVENTORY, null,
            true),
    NP_SAFETY_STOCK(Sheet.NODE_PRODUCTS, "safety_stock", Type.NUMBER, Schema.NP_SAFETY_STOCK, null, true),
    NP_HOLDING_COST(Sheet.NODE_PRODUCTS, "holding_cost", Type.RATE, Schema.NP_HOLDING_VALUE, Schema.NP_HOLDING_UNIT,
            true);

    /** How a field's value is compared. */
    public enum Type {
        /** Existence of a link, product or node-product row; value {@code true}. */
        EXISTS,
        /** Existence of a node; value is its SNRM type. */
        NODE_TYPE,
        /** A duration, compared in seconds. */
        DURATION,
        /** A rate, compared per second. */
        RATE,
        /** A plain number. */
        NUMBER,
        /** Text, compared after name normalisation. */
        TEXT,
        /** An enumeration value, compared after normalisation. */
        ENUM,
        /** The lat/lng pair, compared to two decimals. */
        GEO
    }

    private final Sheet sheet;
    private final String stem;
    private final Type type;
    private final Column column;
    private final Column second;
    private final boolean optional;

    Field(Sheet sheet, String stem, Type type, Column column, Column second, boolean optional) {
        this.sheet = sheet;
        this.stem = stem;
        this.type = type;
        this.column = column;
        this.second = second;
        this.optional = optional;
    }

    public Sheet sheet() {
        return sheet;
    }

    /** The field name used in the ledger and reports ({@code capacity}, {@code exists}, …). */
    public String stem() {
        return stem;
    }

    public Type type() {
        return type;
    }

    /** The value column (the type column for a node's existence fact; null for other existence facts). */
    public Column column() {
        return column;
    }

    /** The unit column of a duration or rate, or the {@code lng} column of a geo fact. */
    public Column second() {
        return second;
    }

    public boolean optional() {
        return optional;
    }

    public boolean isExistence() {
        return type == Type.EXISTS || type == Type.NODE_TYPE;
    }

    /** The per-field key of the report, e.g. {@code nodes.capacity}. */
    public String key() {
        return sheet.id() + "." + stem;
    }

    public static List<Field> of(Sheet sheet) {
        return Arrays.stream(values()).filter(f -> f.sheet == sheet).toList();
    }

    public static Optional<Field> byStem(Sheet sheet, String stem) {
        return Arrays.stream(values()).filter(f -> f.sheet == sheet && f.stem.equals(stem)).findFirst();
    }
}
