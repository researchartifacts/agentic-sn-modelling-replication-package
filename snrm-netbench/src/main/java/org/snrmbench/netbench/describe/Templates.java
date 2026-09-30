package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.model.TimeUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The sentences of the template style, as constants with {placeholders}. */
public final class Templates {

    private Templates() {
    }

    // ---- document ---------------------------------------------------------------------------
    public static final String TITLE = "# Supply network description: {product}";
    public static final String HEADER_NAME = "This document describes the \"{network}\".";
    public static final String HEADER_COUNTS = "The network has {echelons} echelons and {nodes} nodes: {tiers}.";
    public static final String TIER_COUNT = "{count} {role}";
    public static final String HEADER_TIME = "Planning uses periods of {periodValue} {periodUnit} over a horizon of "
            + "{horizon} periods; durations are rounded {policy}.";
    public static final String HEADER_PRODUCTS = "The network handles {count} {productWord}: {products}.";
    public static final String PRODUCT_ITEM = "{product} (unit value {unitValue})";
    public static final String PRODUCT_SEPARATOR = ", ";
    /** Speeds added to the header in distance mode km. */
    public static final String HEADER_SPEEDS = "Road lanes cover {road} km per day and ocean lanes {sea} km per day.";

    // ---- nodes ------------------------------------------------------------------------------
    /** {@code {role}} carries its article ("a plant", "an assembly plant"). */
    public static final String NODE = "{name} is {role}{region}.";
    public static final String NODE_REGION = " in the {region} region";
    public static final String NODE_ATTRIBUTES = " It has {clauses}.";
    public static final String NODE_CAPACITY = "a capacity of {value} units per {unit}";
    public static final String NODE_PROCESSING_TIME = "a processing time of {value} {unit}";
    public static final String NODE_FIXED_COST = "a fixed cost of {value} per period";
    public static final String NODE_VAR_COST = "a variable cost of {value} per unit";
    public static final String NODE_FAILURE_PROB = "a failure probability of {value}";
    public static final List<String> NODE_CLAUSES = List.of(NODE_CAPACITY, NODE_PROCESSING_TIME, NODE_FIXED_COST,
            NODE_VAR_COST, NODE_FAILURE_PROB);

    public static final String GEO = "{name} is located at latitude {lat} and longitude {lng}.";

    // ---- node-products ----------------------------------------------------------------------
    public static final String DEMAND = "{name} demands {value} units of {product} per {unit}.";
    public static final String INVENTORY = "{name} {clauses}.";
    public static final String INVENTORY_INITIAL = "holds an initial inventory of {value} units of {product}";
    public static final String INVENTORY_SAFETY = "keeps a safety stock of {value} units of {product}";
    public static final String INVENTORY_HOLDING = "has a holding cost of {value} per unit per {unit}";
    /** Opens an inventory sentence when neither clause that names the product is stated (--attribute-coverage below 1), so the sentence still says which product it is about. */
    public static final String INVENTORY_BARE = "stocks {product}";
    public static final List<String> INVENTORY_CLAUSES = List.of(INVENTORY_BARE, INVENTORY_INITIAL,
            INVENTORY_SAFETY, INVENTORY_HOLDING);

    // ---- links ------------------------------------------------------------------------------
    public static final String LINK = "{source} ships to {target} with {clauses}.";
    public static final String LINK_LEAD_TIME = "a lead time of {value} {unit}";
    /** Distance mode km: stated instead of the lead time; {mode} is "a road" or "an ocean" (it carries its article), so the lane's speed (stated in the header) is never ambiguous. */
    public static final String LINK_DISTANCE = "{mode} distance of {value} km";
    public static final String MODE_ROAD = "a road";
    public static final String MODE_OCEAN = "an ocean";
    public static final String LINK_CAPACITY = "a link capacity of {value} units per {unit}";
    public static final String LINK_UNIT_COST = "a transport cost of {value} per unit";
    public static final String LINK_FAILURE_PROB = "a link failure probability of {value}";
    public static final List<String> LINK_CLAUSES = List.of(LINK_LEAD_TIME, LINK_DISTANCE, LINK_CAPACITY,
            LINK_UNIT_COST, LINK_FAILURE_PROB);
    /** Links from one source whose attributes are all equal, stated once for every lane (paraphrased and narrative styles only; the template style states each link on its own). */
    public static final String LINK_GROUP = "{source} supplies {targets}; each lane has {clauses}.";

    // ---- noise ------------------------------------------------------------------------------
    /** Openers that mark a sentence as not about the current network; the extractor skips them. */
    public static final List<String> ADVERSARIAL_MARKERS = List.of(
            "Until {year}, ",
            "Before the {year} restructuring, ",
            "Under the proposed expansion, ",
            "In the competitor's network, ",
            "It was originally planned that ",
            "In the discontinued {product} Classic network, ");

    // ---- rendering helpers ------------------------------------------------------------------

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    /** Replaces every {@code {key}} with its value; a missing key is a bug. */
    public static String fill(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null) {
                throw new IllegalArgumentException("no value for {" + m.group(1) + "} in " + template);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** {@code a}, {@code a and b}, {@code a, b and c}. */
    public static String join(List<String> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("nothing to join");
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /** A duration's unit word: singular for exactly 1, plural otherwise ("1 day", "0 days", "3 weeks"). */
    public static String durationUnit(BigDecimal value, TimeUnit unit) {
        return value.compareTo(BigDecimal.ONE) == 0 ? unit.singular() : unit.plural();
    }

    /** A rate's denominator word ("per day"). */
    public static String rateUnit(TimeUnit unit) {
        return unit.singular();
    }

    // ---- regex compilation ------------------------------------------------------------------

    /** Compiles a template into a regex: literal text is quoted, and each {key} becomes the regex given for it (which may itself contain named groups). */
    public static String regex(String template, Map<String, String> parts) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(quote(template.substring(last, m.start())));
            String part = parts.get(m.group(1));
            if (part == null) {
                throw new IllegalArgumentException("no regex for {" + m.group(1) + "} in " + template);
            }
            sb.append(part);
            last = m.end();
        }
        sb.append(quote(template.substring(last)));
        return sb.toString();
    }

    private static String quote(String literal) {
        return literal.isEmpty() ? "" : Pattern.quote(literal);
    }
}
