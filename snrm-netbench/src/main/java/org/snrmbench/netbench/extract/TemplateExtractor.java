package org.snrmbench.netbench.extract;

import org.snrmbench.netbench.describe.NumberStyle;
import org.snrmbench.netbench.describe.Templates;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.Quantity;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.model.TierRole;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The rule-based parser of template-style descriptions: the non-LLM baseline and the A ⇔ B round-trip check. */
public final class TemplateExtractor {

    /** What was extracted, and how every line was treated. */
    public record Result(Network network, boolean geo, int matched, int skipped, int ignored,
                         List<String> ignoredSentences, List<String> problems) {
    }

    private final NumberStyle numbers;
    /** One number in the description's style ({@code --locale}). */
    private final String number;
    private BigDecimal roadSpeed;
    private BigDecimal oceanSpeed;

    private final Map<String, TimeUnit> unitWords = new LinkedHashMap<>();
    private final String unitRegex;
    private final Map<String, TierRole> roleWords = new LinkedHashMap<>();
    private final String roleRegex;
    private final Map<String, RoundingPolicy> policyPhrases = new LinkedHashMap<>();

    // parsed state
    private TimeBase timeBase;
    private final List<TierRole> roles = new ArrayList<>();
    private final List<Product> products = new ArrayList<>();
    private String productRegex;
    private final Map<String, NodeBuilder> nodes = new LinkedHashMap<>();
    private final Map<String, LinkBuilder> links = new LinkedHashMap<>();
    private final Map<String, NpBuilder> nodeProducts = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    private static final class NodeBuilder {
        String name;
        TierRole role;
        String region;
        Quantity capacity;
        Quantity processing;
        BigDecimal fixed;
        BigDecimal var;
        BigDecimal failure;
        BigDecimal lat;
        BigDecimal lng;
    }

    private static final class LinkBuilder {
        String source;
        String target;
        Quantity lead;
        Quantity capacity;
        BigDecimal cost;
        BigDecimal failure;
    }

    private static final class NpBuilder {
        String node;
        String product;
        Quantity demand;
        BigDecimal initial;
        BigDecimal safety;
        Quantity holding;
    }

    private TemplateExtractor(NumberStyle numbers) {
        this.numbers = numbers;
        this.number = numbers.regex();
        for (TimeUnit u : TimeUnit.values()) {
            unitWords.put(u.plural(), u);
            unitWords.put(u.singular(), u);
        }
        unitRegex = alternation(unitWords.keySet().stream());
        for (TierRole r : TierRole.values()) {
            roleWords.put(r.plural(), r);
            roleWords.put(r.word(), r);
        }
        roleRegex = alternation(roleWords.keySet().stream());
        for (RoundingPolicy p : RoundingPolicy.values()) {
            policyPhrases.put(p.phrase(), p);
        }
    }

    /** Parses a whole description whose numbers are plain ({@code --locale plain}). */
    public static Result extract(String markdown) {
        return extract(markdown, NumberStyle.PLAIN);
    }

    /** Parses a whole description whose numbers are written in the given style. */
    public static Result extract(String markdown, NumberStyle numbers) {
        return new TemplateExtractor(numbers).run(markdown);
    }

    private Result run(String markdown) {
        List<String> lines = Arrays.stream(markdown.split("\r?\n"))
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
        if (lines.isEmpty() || !parseHeader(lines.get(0))) {
            throw new IllegalArgumentException("the description has no template header paragraph");
        }
        List<Pattern> markers = Templates.ADVERSARIAL_MARKERS.stream()
                .map(m -> Pattern.compile(Templates.regex(m, Map.of("year", "\\d{4}", "product", ".+?"))))
                .toList();
        Parsers parsers = new Parsers();
        int matched = 0;
        int skipped = 0;
        List<String> ignored = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (markers.stream().anyMatch(m -> m.matcher(line).lookingAt())) {
                skipped++;
            } else if (parsers.parse(line)) {
                matched++;
            } else {
                ignored.add(line);
            }
        }
        boolean geo = nodes.values().stream().anyMatch(n -> n.lat != null);
        return new Result(build(), geo, matched, skipped, ignored.size(), ignored, problems);
    }

    // ---- header -----------------------------------------------------------------------------

    private boolean parseHeader(String paragraph) {
        String regex = "^" + Templates.regex(Templates.HEADER_NAME, Map.of("network", "(?<network>.+?)"))
                + " " + Templates.regex(Templates.HEADER_COUNTS, Map.of("echelons", "(?<echelons>" + number + ")",
                "nodes", "(?<nodes>" + number + ")", "tiers", "(?<tiers>.+?)"))
                + " " + Templates.regex(Templates.HEADER_TIME, Map.of("periodValue", "(?<periodValue>" + number + ")",
                "periodUnit", "(?<periodUnit>" + unitRegex + ")", "horizon", "(?<horizon>" + number + ")",
                "policy", "(?<policy>" + alternation(policyPhrases.keySet().stream()) + ")"))
                + " " + Templates.regex(Templates.HEADER_PRODUCTS, Map.of("count", "(?<count>" + number + ")",
                "productWord", "(?:products|product)", "products", "(?<products>.+?)"))
                + (numbers.headerSentence() == null ? "" : "(?: " + Pattern.quote(numbers.headerSentence()) + ")?")
                + "(?: " + Templates.regex(Templates.HEADER_SPEEDS, Map.of("road", "(?<road>" + number + ")",
                "sea", "(?<sea>" + number + ")")) + ")?"
                + "$";
        Matcher m = Pattern.compile(regex).matcher(paragraph);
        if (!m.matches()) {
            return false;
        }
        Pattern tier = Pattern.compile(Templates.regex(Templates.TIER_COUNT,
                Map.of("count", "(?<count>" + number + ")", "role", "(?<role>" + roleRegex + ")")));
        Matcher t = tier.matcher(m.group("tiers"));
        while (t.find()) {
            roles.add(roleWords.get(t.group("role")));
        }
        Pattern item = Pattern.compile("^" + Templates.regex(Templates.PRODUCT_ITEM,
                Map.of("product", "(?<product>.+)", "unitValue", "(?<unitValue>" + number + ")")) + "$");
        for (String p : m.group("products").split(Pattern.quote(Templates.PRODUCT_SEPARATOR))) {
            Matcher pm = item.matcher(p);
            if (!pm.matches()) {
                return false;
            }
            products.add(new Product(pm.group("product"), numbers.parse(pm.group("unitValue"))));
        }
        productRegex = alternation(products.stream().map(Product::name));
        timeBase = new TimeBase(numbers.parse(m.group("periodValue")), unitWords.get(m.group("periodUnit")),
                numbers.parse(m.group("horizon")).intValueExact(), policyPhrases.get(m.group("policy")),
                m.group("network"));
        if (m.group("road") != null) {
            roadSpeed = numbers.parse(m.group("road"));
            oceanSpeed = numbers.parse(m.group("sea"));
        }
        return true;
    }

    // ---- statements -------------------------------------------------------------------------

    /** The statement patterns, compiled once the header has named the products. */
    private final class Parsers {
        final Pattern link = Pattern.compile("^" + Templates.regex(Templates.LINK, Map.of(
                "source", "(?<source>.+?)", "target", "(?<target>.+?)",
                "clauses", "(?<clauses>" + prefixes(Templates.LINK_CLAUSES) + ".*)")) + "$");
        final Pattern demand = Pattern.compile("^" + Templates.regex(Templates.DEMAND, Map.of(
                "name", "(?<name>.+?)", "value", "(?<value>" + number + ")",
                "product", "(?<product>" + productRegex + ")", "unit", "(?<unit>" + unitRegex + ")")) + "$");
        final Pattern geo = Pattern.compile("^" + Templates.regex(Templates.GEO, Map.of(
                "name", "(?<name>.+?)", "lat", "(?<lat>" + number + ")", "lng", "(?<lng>" + number + ")")) + "$");
        final Pattern node = Pattern.compile("^" + Templates.regex(Templates.NODE, Map.of(
                "name", "(?<name>.+?)", "role", "(?:a|an) (?<role>" + roleRegex + ")",
                "region", "(?:" + Templates.regex(Templates.NODE_REGION, Map.of("region", "(?<region>.+?)")) + ")?"))
                + "(?:" + Templates.regex(Templates.NODE_ATTRIBUTES, Map.of("clauses", "(?<clauses>.+)")) + ")?$");
        final Pattern inventory = Pattern.compile("^" + Templates.regex(Templates.INVENTORY, Map.of(
                "name", "(?<name>.+?)", "clauses", "(?<clauses>" + prefixes(Templates.INVENTORY_CLAUSES) + ".*)"))
                + "$");
        final ClauseList nodeClauses = new ClauseList(Templates.NODE_CLAUSES);
        final ClauseList linkClauses = new ClauseList(Templates.LINK_CLAUSES);
        final ClauseList inventoryClauses = new ClauseList(Templates.INVENTORY_CLAUSES);

        boolean parse(String line) {
            Matcher m;
            if ((m = link.matcher(line)).matches()) {
                return parseLink(m);
            }
            if ((m = demand.matcher(line)).matches()) {
                NpBuilder np = np(m.group("name"), m.group("product"));
                np.demand = new Quantity(numbers.parse(m.group("value")), unitWords.get(m.group("unit")));
                return true;
            }
            if ((m = geo.matcher(line)).matches()) {
                NodeBuilder n = nodeBuilder(m.group("name"));
                n.lat = fit(numbers.parse(m.group("lat")), Schema.NODE_LAT);
                n.lng = fit(numbers.parse(m.group("lng")), Schema.NODE_LNG);
                return true;
            }
            if ((m = node.matcher(line)).matches()) {
                return parseNode(m);
            }
            if ((m = inventory.matcher(line)).matches()) {
                return parseInventory(m);
            }
            return false;
        }

        private boolean parseLink(Matcher m) {
            List<Matcher> c = linkClauses.parse(m.group("clauses"));
            if (c == null || (c.get(0) == null && c.get(1) == null)) {
                return false;
            }
            LinkBuilder l = links.computeIfAbsent(m.group("source") + "->" + m.group("target"), k -> new LinkBuilder());
            l.source = m.group("source");
            l.target = m.group("target");
            l.lead = c.get(0) != null ? duration(c.get(0), Schema.LINK_LEAD_VALUE) : leadFromDistance(c.get(1), l);
            l.capacity = rate(c.get(2), Schema.LINK_CAPACITY_VALUE);
            l.cost = value(c.get(3), Schema.LINK_UNIT_COST);
            l.failure = value(c.get(4), Schema.LINK_FAILURE_PROB);
            return true;
        }

        /** Distance mode km: the lead time is the distance ÷ the mode's speed from the header, written in the period unit (a whole number of periods, as the generator guarantees). */
        private Quantity leadFromDistance(Matcher distance, LinkBuilder l) {
            if (roadSpeed == null) {
                problems.add("link " + l.source + "->" + l.target + " states a distance but the header states no "
                        + "lane speeds; lead time left blank");
                return null;
            }
            BigDecimal speed = distance.group("mode").equals(Templates.MODE_OCEAN) ? oceanSpeed : roadSpeed;
            BigDecimal seconds = numbers.parse(distance.group("value")).multiply(BigDecimal.valueOf(TimeUnit.DAY.seconds()))
                    .divide(speed, java.math.MathContext.DECIMAL128);
            BigDecimal periods = seconds.divide(BigDecimal.valueOf(timeBase.periodUnit().seconds()),
                    java.math.MathContext.DECIMAL128).stripTrailingZeros();
            BigDecimal v = fit(periods.scale() < 0 ? periods.setScale(0) : periods, Schema.LINK_LEAD_VALUE);
            return v == null ? null : new Quantity(v, timeBase.periodUnit());
        }

        private boolean parseNode(Matcher m) {
            List<Matcher> c = m.group("clauses") == null ? null : nodeClauses.parse(m.group("clauses"));
            if (m.group("clauses") != null && c == null) {
                return false;
            }
            NodeBuilder n = nodeBuilder(m.group("name"));
            n.role = roleWords.get(m.group("role"));
            n.region = m.group("region");
            if (c != null) {
                n.capacity = rate(c.get(0), Schema.NODE_CAPACITY_VALUE);
                n.processing = duration(c.get(1), Schema.NODE_PROCESSING_VALUE);
                n.fixed = value(c.get(2), Schema.NODE_FIXED_COST);
                n.var = value(c.get(3), Schema.NODE_VAR_COST);
                n.failure = value(c.get(4), Schema.NODE_FAILURE_PROB);
            }
            return true;
        }

        private boolean parseInventory(Matcher m) {
            List<Matcher> c = inventoryClauses.parse(m.group("clauses"));
            if (c == null) {
                return false;
            }
            String product = Stream.of(c.get(0), c.get(1), c.get(2)).filter(x -> x != null)
                    .map(x -> x.group("product")).findFirst().orElse(null);
            if (product == null) {
                return false;
            }
            NpBuilder np = np(m.group("name"), product);
            np.initial = value(c.get(1), Schema.NP_INITIAL_INVENTORY);
            np.safety = value(c.get(2), Schema.NP_SAFETY_STOCK);
            np.holding = rate(c.get(3), Schema.NP_HOLDING_VALUE);
            return true;
        }
    }

    /** An ordered list of optional clauses joined by ", " and " and ". */
    private final class ClauseList {
        private final List<Pattern> first = new ArrayList<>();
        private final List<Pattern> later = new ArrayList<>();

        ClauseList(List<String> templates) {
            for (String t : templates) {
                String r = Templates.regex(t, Map.of("value", "(?<value>" + number + ")",
                        "unit", "(?<unit>" + unitRegex + ")", "product", "(?<product>" + productRegex + ")",
                        "mode", "(?<mode>" + Templates.MODE_ROAD + "|" + Templates.MODE_OCEAN + ")"));
                first.add(Pattern.compile(r));
                later.add(Pattern.compile("(?:, | and )" + r));
            }
        }

        /** One matcher per template (null when absent), or null when the text is not such a list. */
        List<Matcher> parse(String text) {
            Matcher[] out = new Matcher[first.size()];
            int pos = 0;
            int next = 0;
            while (pos < text.length()) {
                boolean found = false;
                for (int i = next; i < first.size() && !found; i++) {
                    Matcher m = (pos == 0 ? first : later).get(i).matcher(text).region(pos, text.length());
                    if (m.lookingAt()) {
                        out[i] = m;
                        pos = m.end();
                        next = i + 1;
                        found = true;
                    }
                }
                if (!found) {
                    return null;
                }
            }
            return pos == 0 ? null : Arrays.asList(out);
        }
    }

    // ---- assembly ---------------------------------------------------------------------------

    private NodeBuilder nodeBuilder(String name) {
        return nodes.computeIfAbsent(name, k -> {
            NodeBuilder n = new NodeBuilder();
            n.name = k;
            return n;
        });
    }

    private NpBuilder np(String node, String product) {
        return nodeProducts.computeIfAbsent(node + "|" + product, k -> {
            NpBuilder np = new NpBuilder();
            np.node = node;
            np.product = product;
            return np;
        });
    }

    private Quantity duration(Matcher m, Column column) {
        if (m == null) {
            return null;
        }
        BigDecimal v = fit(numbers.parse(m.group("value")), column);
        return v == null ? null : new Quantity(v, unitWords.get(m.group("unit")));
    }

    private Quantity rate(Matcher m, Column column) {
        return duration(m, column);
    }

    private BigDecimal value(Matcher m, Column column) {
        return m == null ? null : fit(numbers.parse(m.group("value")), column);
    }

    /** The value when its column can hold it (an integer column, or at most two decimals); otherwise a problem is recorded and the cell is left blank, so the files are still written. */
    private BigDecimal fit(BigDecimal v, Column column) {
        int scale = v.stripTrailingZeros().scale();
        boolean fits = switch (column.kind()) {
            case INTEGER -> scale <= 0;
            case DECIMAL2 -> scale <= 2;
            default -> true;
        };
        if (fits) {
            return v;
        }
        problems.add(column.qualified() + " value " + v.toPlainString() + " does not fit a "
                + column.kind().name().toLowerCase(java.util.Locale.ROOT) + " column; left blank");
        return null;
    }

    /** The network, with the customer and supply-side conventions filled in. */
    private Network build() {
        TimeUnit unit = timeBase.periodUnit();
        List<Node> nodeList = new ArrayList<>();
        Map<String, NodeType> types = new LinkedHashMap<>();
        for (NodeBuilder n : nodes.values()) {
            if (n.role == null) {
                problems.add("node " + n.name + " is mentioned but never introduced; dropped");
                continue;
            }
            int tier = roles.indexOf(n.role);
            if (tier < 0) {
                problems.add("node " + n.name + " has role " + n.role.word() + ", which the header does not list");
                continue;
            }
            types.put(n.name, n.role.type());
            if (n.role.type() == NodeType.CUSTOMER) {
                nodeList.add(new Node(n.name, NodeType.CUSTOMER, tier, new Quantity(null, unit), Quantity.of(0, unit),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, n.region, n.lat, n.lng));
            } else {
                nodeList.add(new Node(n.name, n.role.type(), tier, n.capacity, n.processing, n.fixed, n.var,
                        n.failure, n.region, n.lat, n.lng));
            }
        }
        List<Link> linkList = new ArrayList<>();
        for (LinkBuilder l : links.values()) {
            if (!types.containsKey(l.source) || !types.containsKey(l.target)) {
                problems.add("link " + l.source + "->" + l.target + " names an unknown node; dropped");
                continue;
            }
            linkList.add(new Link(l.source, l.target, l.lead, l.capacity, l.cost, l.failure));
        }
        List<NodeProduct> npList = new ArrayList<>();
        for (NpBuilder np : nodeProducts.values()) {
            NodeType type = types.get(np.node);
            if (type == null) {
                problems.add("node-product " + np.node + "|" + np.product + " names an unknown node; dropped");
                continue;
            }
            if (type == NodeType.CUSTOMER) {
                npList.add(new NodeProduct(np.node, np.product, np.demand, BigDecimal.ZERO, BigDecimal.ZERO,
                        Quantity.of(0, unit)));
            } else {
                npList.add(new NodeProduct(np.node, np.product, Quantity.of(0, unit), np.initial, np.safety,
                        np.holding));
            }
        }
        List<Tier> tiers = new ArrayList<>();
        for (int t = 0; t < roles.size(); t++) {
            final int ti = t;
            tiers.add(new Tier(t, roles.get(t), roles.get(t).type(), nodeList.stream()
                    .filter(n -> n.tierIndex() == ti).map(Node::name).sorted().toList()));
        }
        return new Network(timeBase, tiers, products, nodeList, linkList, npList);
    }

    // ---- regex helpers ----------------------------------------------------------------------

    /** Longest alternatives first, so "days" is tried before "day". */
    private static String alternation(Stream<String> words) {
        return words.distinct().sorted(Comparator.comparingInt(String::length).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .map(Pattern::quote).collect(Collectors.joining("|", "(?:", ")"));
    }

    /** A lookahead on the literal openings of a clause list, anchoring where the clauses begin. */
    private static String prefixes(List<String> clauseTemplates) {
        return "(?=" + alternation(clauseTemplates.stream().map(t -> t.substring(0, t.indexOf('{')))) + ")";
    }
}
