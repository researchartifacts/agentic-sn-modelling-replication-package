package org.snrmbench.netbench.schema;

import org.snrmbench.netbench.schema.Column.Check;
import org.snrmbench.netbench.schema.Column.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The SNRM canonical CSV contract, encoded once. */
public final class Schema {

    private Schema() {
    }

    // ---- network_meta -----------------------------------------------------------------------
    public static final Column META_PERIOD_VALUE = col(Sheet.NETWORK_META, "period_length_value", true, Kind.INTEGER,
            Check.POSITIVE, "length of one simulation period, in period_length_unit");
    public static final Column META_PERIOD_UNIT = col(Sheet.NETWORK_META, "period_length_unit", true, Kind.TIME_UNIT,
            Check.NONE, "unit of the period length");
    public static final Column META_HORIZON = col(Sheet.NETWORK_META, "horizon_periods", true, Kind.INTEGER,
            Check.AT_LEAST_ONE, "number of periods simulated");
    public static final Column META_ROUNDING = col(Sheet.NETWORK_META, "rounding_policy", false, Kind.ROUNDING_POLICY,
            Check.NONE, "how durations round to whole periods; default NEAREST");
    public static final Column META_NAME = col(Sheet.NETWORK_META, "name", false, Kind.TEXT,
            Check.NONE, "the network's own name");

    // ---- nodes ------------------------------------------------------------------------------
    public static final Column NODE_NAME = col(Sheet.NODES, "name", true, Kind.TEXT, Check.NONE,
            "unique node name");
    public static final Column NODE_TYPE = col(Sheet.NODES, "type", true, Kind.NODE_TYPE, Check.NONE,
            "SUPPLIER, PLANT, DC or CUSTOMER");
    public static final Column NODE_CAPACITY_VALUE = col(Sheet.NODES, "capacity_value", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "rate: units per capacity_time_unit; blank = uncapped");
    public static final Column NODE_CAPACITY_UNIT = col(Sheet.NODES, "capacity_time_unit", false, Kind.TIME_UNIT,
            Check.NONE, "denominator of capacity_value");
    public static final Column NODE_PROCESSING_VALUE = col(Sheet.NODES, "processing_time_value", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "duration in processing_time_unit");
    public static final Column NODE_PROCESSING_UNIT = col(Sheet.NODES, "processing_time_unit", false, Kind.TIME_UNIT,
            Check.NONE, "unit of processing_time_value");
    public static final Column NODE_FIXED_COST = col(Sheet.NODES, "fixed_cost", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "cost per period");
    public static final Column NODE_VAR_COST = col(Sheet.NODES, "var_cost", false, Kind.DECIMAL2,
            Check.NON_NEGATIVE, "cost per unit");
    public static final Column NODE_FAILURE_PROB = col(Sheet.NODES, "failure_prob", false, Kind.DECIMAL2,
            Check.PROBABILITY, "probability in [0, 1]");
    public static final Column NODE_REGION = col(Sheet.NODES, "region", false, Kind.TEXT, Check.NONE,
            "region tag");
    public static final Column NODE_LAT = col(Sheet.NODES, "lat", false, Kind.DECIMAL2, Check.NONE,
            "latitude in degrees");
    public static final Column NODE_LNG = col(Sheet.NODES, "lng", false, Kind.DECIMAL2, Check.NONE,
            "longitude in degrees");
    public static final Column NODE_POS_X = col(Sheet.NODES, "pos_x", false, Kind.NUMBER, Check.NONE,
            "editor canvas x (not geography)");
    public static final Column NODE_POS_Y = col(Sheet.NODES, "pos_y", false, Kind.NUMBER, Check.NONE,
            "editor canvas y (not geography)");
    public static final Column NODE_CAPTION = col(Sheet.NODES, "caption", false, Kind.TEXT, Check.NONE,
            "annotation drawn under the name");
    public static final Column NODE_CAPTION_VISIBLE = col(Sheet.NODES, "caption_visible", false, Kind.BOOLEAN,
            Check.NONE, "true/false; blank = visible");

    // ---- links ------------------------------------------------------------------------------
    public static final Column LINK_SOURCE = col(Sheet.LINKS, "source", true, Kind.TEXT, Check.NONE,
            "name of the node the link leaves");
    public static final Column LINK_TARGET = col(Sheet.LINKS, "target", true, Kind.TEXT, Check.NONE,
            "name of the node the link enters");
    public static final Column LINK_LEAD_VALUE = col(Sheet.LINKS, "lead_time_value", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "duration in lead_time_unit");
    public static final Column LINK_LEAD_UNIT = col(Sheet.LINKS, "lead_time_unit", false, Kind.TIME_UNIT,
            Check.NONE, "unit of lead_time_value");
    public static final Column LINK_CAPACITY_VALUE = col(Sheet.LINKS, "capacity_value", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "rate: units per capacity_time_unit; blank = uncapped");
    public static final Column LINK_CAPACITY_UNIT = col(Sheet.LINKS, "capacity_time_unit", false, Kind.TIME_UNIT,
            Check.NONE, "denominator of capacity_value");
    public static final Column LINK_UNIT_COST = col(Sheet.LINKS, "unit_cost", false, Kind.DECIMAL2,
            Check.NON_NEGATIVE, "transport cost per unit");
    public static final Column LINK_FAILURE_PROB = col(Sheet.LINKS, "failure_prob", false, Kind.DECIMAL2,
            Check.PROBABILITY, "probability in [0, 1]");
    public static final Column LINK_CAPTION = col(Sheet.LINKS, "caption", false, Kind.TEXT, Check.NONE,
            "annotation drawn under the lead time");
    public static final Column LINK_CAPTION_VISIBLE = col(Sheet.LINKS, "caption_visible", false, Kind.BOOLEAN,
            Check.NONE, "true/false; blank = visible");

    // ---- products ---------------------------------------------------------------------------
    public static final Column PRODUCT_NAME = col(Sheet.PRODUCTS, "name", true, Kind.TEXT, Check.NONE,
            "unique product name");
    public static final Column PRODUCT_UNIT_VALUE = col(Sheet.PRODUCTS, "unit_value", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "value of one unit (the cost of a unit of unmet demand)");

    // ---- node_products ----------------------------------------------------------------------
    public static final Column NP_NODE = col(Sheet.NODE_PRODUCTS, "node", true, Kind.TEXT, Check.NONE,
            "node name");
    public static final Column NP_PRODUCT = col(Sheet.NODE_PRODUCTS, "product", true, Kind.TEXT, Check.NONE,
            "product name");
    public static final Column NP_DEMAND_VALUE = col(Sheet.NODE_PRODUCTS, "demand_value", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "rate: units per demand_time_unit (customers only)");
    public static final Column NP_DEMAND_UNIT = col(Sheet.NODE_PRODUCTS, "demand_time_unit", false, Kind.TIME_UNIT,
            Check.NONE, "denominator of demand_value");
    public static final Column NP_INITIAL_INVENTORY = col(Sheet.NODE_PRODUCTS, "initial_inventory", false,
            Kind.INTEGER, Check.NON_NEGATIVE, "units on hand at period 0");
    public static final Column NP_SAFETY_STOCK = col(Sheet.NODE_PRODUCTS, "safety_stock", false, Kind.INTEGER,
            Check.NON_NEGATIVE, "units");
    public static final Column NP_HOLDING_VALUE = col(Sheet.NODE_PRODUCTS, "holding_cost_value", false,
            Kind.DECIMAL2, Check.NON_NEGATIVE, "rate: cost per unit per holding_cost_time_unit");
    public static final Column NP_HOLDING_UNIT = col(Sheet.NODE_PRODUCTS, "holding_cost_time_unit", false,
            Kind.TIME_UNIT, Check.NONE, "denominator of holding_cost_value");

    private static final List<Column> META = List.of(META_PERIOD_VALUE, META_PERIOD_UNIT, META_HORIZON,
            META_ROUNDING, META_NAME);
    private static final List<Column> NODES = List.of(NODE_NAME, NODE_TYPE, NODE_CAPACITY_VALUE, NODE_CAPACITY_UNIT,
            NODE_PROCESSING_VALUE, NODE_PROCESSING_UNIT, NODE_FIXED_COST, NODE_VAR_COST, NODE_FAILURE_PROB,
            NODE_REGION, NODE_LAT, NODE_LNG, NODE_POS_X, NODE_POS_Y, NODE_CAPTION, NODE_CAPTION_VISIBLE);
    private static final List<Column> LINKS = List.of(LINK_SOURCE, LINK_TARGET, LINK_LEAD_VALUE, LINK_LEAD_UNIT,
            LINK_CAPACITY_VALUE, LINK_CAPACITY_UNIT, LINK_UNIT_COST, LINK_FAILURE_PROB, LINK_CAPTION,
            LINK_CAPTION_VISIBLE);
    private static final List<Column> PRODUCTS = List.of(PRODUCT_NAME, PRODUCT_UNIT_VALUE);
    private static final List<Column> NODE_PRODUCTS = List.of(NP_NODE, NP_PRODUCT, NP_DEMAND_VALUE, NP_DEMAND_UNIT,
            NP_INITIAL_INVENTORY, NP_SAFETY_STOCK, NP_HOLDING_VALUE, NP_HOLDING_UNIT);

    /** A value column and the unit column that qualifies it. */
    public record UnitPair(Column value, Column unit, boolean duration) {
    }

    private static final List<UnitPair> UNIT_PAIRS = List.of(
            new UnitPair(META_PERIOD_VALUE, META_PERIOD_UNIT, true),
            new UnitPair(NODE_CAPACITY_VALUE, NODE_CAPACITY_UNIT, false),
            new UnitPair(NODE_PROCESSING_VALUE, NODE_PROCESSING_UNIT, true),
            new UnitPair(LINK_LEAD_VALUE, LINK_LEAD_UNIT, true),
            new UnitPair(LINK_CAPACITY_VALUE, LINK_CAPACITY_UNIT, false),
            new UnitPair(NP_DEMAND_VALUE, NP_DEMAND_UNIT, false),
            new UnitPair(NP_HOLDING_VALUE, NP_HOLDING_UNIT, false));

    private static Column col(Sheet sheet, String name, boolean required, Kind kind, Check check, String note) {
        return new Column(sheet, name, required, kind, check, note);
    }

    /** Every column of a sheet in canonical order. */
    public static List<Column> columns(Sheet sheet) {
        return switch (sheet) {
            case NETWORK_META -> META;
            case NODES -> NODES;
            case LINKS -> LINKS;
            case PRODUCTS -> PRODUCTS;
            case NODE_PRODUCTS -> NODE_PRODUCTS;
        };
    }

    /** The required ({@code *}) columns of a sheet. */
    public static List<Column> required(Sheet sheet) {
        return columns(sheet).stream().filter(Column::required).toList();
    }

    /** A column by header text, case-insensitively and trimmed. */
    public static Optional<Column> column(Sheet sheet, String header) {
        String h = header.trim().toLowerCase(Locale.ROOT);
        return columns(sheet).stream().filter(c -> c.name().equals(h)).findFirst();
    }

    /** Every value/unit pair, durations and rates alike. */
    public static List<UnitPair> unitPairs() {
        return UNIT_PAIRS;
    }

    /** The pair a value or unit column belongs to. */
    public static Optional<UnitPair> unitPair(Column c) {
        return UNIT_PAIRS.stream().filter(p -> p.value().equals(c) || p.unit().equals(c)).findFirst();
    }

    /** The columns the writer emits for B: everything in network_meta, products and node_products; nodes through region (plus lat/lng with geo); links without the caption pair. */
    public static List<Column> writtenColumns(Sheet sheet, boolean geo) {
        return switch (sheet) {
            case NODES -> {
                List<Column> out = new ArrayList<>(NODES.subList(0, NODES.indexOf(NODE_REGION) + 1));
                if (geo) {
                    out.add(NODE_LAT);
                    out.add(NODE_LNG);
                }
                yield List.copyOf(out);
            }
            case LINKS -> LINKS.subList(0, LINKS.indexOf(LINK_CAPTION));
            default -> columns(sheet);
        };
    }
}
