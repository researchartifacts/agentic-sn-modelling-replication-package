package org.snrmbench.netbench.validate;

import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.csv.RawTable.Row;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.schema.UnitTokens;
import org.snrmbench.netbench.util.Numbers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Re-implementation of SNRM's two-stage import validation: row level first (a row with any error is dropped, but every finding on it is reported), then network level on the rows that survived. */
public final class SnrmRules {

    /** The fallback time base (1 DAY / NEAREST / 52 periods). */
    public static final int DEFAULT_HORIZON = 52;
    private static final BigDecimal TEN_PERCENT = new BigDecimal("0.10");
    private static final BigDecimal MAX_PERIODS = BigDecimal.valueOf(1000);

    private record SNode(String name, NodeType type, int line) {
    }

    private record SLink(String source, String target, int line) {
        String key() {
            return source + "->" + target;
        }
    }

    private record SNodeProduct(String node, String product, BigDecimal demand, int line) {
    }

    private record Duration(String label, Sheet sheet, int line, String element, BigDecimal value, TimeUnit unit) {
    }

    private final RawNetwork net;
    private final List<Finding> findings = new ArrayList<>();
    private boolean rowsDropped;

    private BigDecimal periodValue = BigDecimal.ONE;
    private TimeUnit periodUnit = TimeUnit.DAY;
    private int horizon = DEFAULT_HORIZON;
    private RoundingPolicy policy = RoundingPolicy.NEAREST;
    private String networkName;

    private final Set<String> declaredNodes = new LinkedHashSet<>();
    private final Set<String> declaredProducts = new LinkedHashSet<>();
    private final Map<String, SNode> nodes = new LinkedHashMap<>();
    private final List<SLink> links = new ArrayList<>();
    private final List<SNodeProduct> nodeProducts = new ArrayList<>();
    private final List<Duration> durations = new ArrayList<>();

    private SnrmRules(RawNetwork net) {
        this.net = net;
    }

    /** Validates a network as read. Never throws on bad content: everything becomes a finding. */
    public static ValidationResult validate(RawNetwork net) {
        return new SnrmRules(net).run();
    }

    private ValidationResult run() {
        checkUnrecognisedSheets();
        for (Sheet s : Sheet.values()) {
            net.table(s).ifPresent(t -> {
                checkUnmappedColumns(t);
                checkRowWidth(t);
            });
        }
        readTimeBase();
        net.table(Sheet.NODES).ifPresent(t -> collectDeclared(t, Schema.NODE_NAME, declaredNodes));
        net.table(Sheet.PRODUCTS).ifPresent(t -> collectDeclared(t, Schema.PRODUCT_NAME, declaredProducts));
        net.table(Sheet.NODES).ifPresent(this::readNodes);
        net.table(Sheet.LINKS).ifPresent(this::readLinks);
        net.table(Sheet.PRODUCTS).ifPresent(this::readProducts);
        net.table(Sheet.NODE_PRODUCTS).ifPresent(this::readNodeProducts);

        List<SLink> graphLinks = links.stream()
                .filter(l -> nodes.containsKey(l.source()) && nodes.containsKey(l.target()))
                .toList();
        checkNoSupplyNode();
        checkNoCustomerNode();
        checkNoDemandDeclared();
        checkCustomerUnreachable(graphLinks);
        checkDurations(graphLinks);
        checkLateralLink(graphLinks);
        checkEchelonDirection(graphLinks);
        checkOrphanNode(graphLinks);
        checkDemandOnNonCustomer();
        checkPartialGraph();
        checkProductCreatedImplicitly();
        TimeBase tb = new TimeBase(periodValue, periodUnit, horizon, policy, networkName);
        return new ValidationResult(findings, tb);
    }

    // ---- sheet level ------------------------------------------------------------------------

    private void checkUnrecognisedSheets() {
        for (String f : net.unrecognisedFiles()) {
            findings.add(Finding.row(Code.UNRECOGNISED_SHEET, f, null, null,
                    f + " is not one of the canonical sheets; skipped"));
        }
    }

    private void checkUnmappedColumns(RawTable t) {
        for (String h : t.unmappedHeaders()) {
            findings.add(Finding.row(Code.UNMAPPED_COLUMN, t.sheet().fileName(), t.headerLine(), h,
                    t.sheet().fileName() + ": column \"" + h + "\" has no home in the schema; ignored"));
        }
    }

    private void checkRowWidth(RawTable t) {
        int width = t.headers().size();
        for (Row r : t.rows()) {
            if (r.width() != width) {
                String what = r.width() > width ? "surplus cells dropped" : "missing trailing cells read as blank";
                findings.add(Finding.row(Code.ROW_WIDTH_MISMATCH, t.sheet().fileName(), r.line(), null,
                        where(t, r) + "has " + r.width() + " cells but the header has " + width + "; " + what));
            }
        }
    }

    /** MISSING_REQUIRED_COLUMN for each absent required column; true when none is absent. */
    private boolean checkMissingRequiredColumns(RawTable t) {
        boolean ok = true;
        for (Column c : Schema.required(t.sheet())) {
            if (!t.has(c)) {
                findings.add(Finding.row(Code.MISSING_REQUIRED_COLUMN, t.sheet().fileName(), t.headerLine(),
                        c.name(), t.sheet().fileName() + ": required column " + c.name() + " is missing"));
                ok = false;
            }
        }
        return ok;
    }

    private void checkProductCreatedImplicitly() {
        if (!net.has(Sheet.PRODUCTS)) {
            findings.add(Finding.row(Code.PRODUCT_CREATED_IMPLICITLY, Sheet.PRODUCTS.fileName(), null, null,
                    "products.csv is absent; a default product is created"));
        }
    }

    // ---- network_meta -----------------------------------------------------------------------

    private void readTimeBase() {
        Optional<RawTable> meta = net.table(Sheet.NETWORK_META);
        if (meta.isEmpty()) {
            return; // SNRM's defaults, no finding (verify adds NETWORK_META_DEFAULTED)
        }
        RawTable t = meta.get();
        checkMissingRequiredColumns(t);
        if (t.rows().isEmpty()) {
            findings.add(Finding.row(Code.REQUIRED_VALUE_MISSING, t.sheet().fileName(), null, null,
                    "network_meta.csv has no data row; 1 DAY / NEAREST / " + DEFAULT_HORIZON + " assumed"));
            return;
        }
        Row r = t.rows().get(0);
        checkCells(t, r);
        Optional<BigDecimal> pv = validNumber(t, r, Schema.META_PERIOD_VALUE).filter(v -> v.signum() > 0);
        Optional<TimeUnit> pu = Optional.ofNullable(t.cell(r, Schema.META_PERIOD_UNIT)).flatMap(UnitTokens::parse);
        if (pv.isPresent() && pu.isPresent()) {
            periodValue = pv.get();
            periodUnit = pu.get();
        }
        validNumber(t, r, Schema.META_HORIZON)
                .filter(v -> v.compareTo(BigDecimal.ONE) >= 0 && Numbers.isInteger(v)
                        && v.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0)
                .ifPresent(v -> horizon = v.intValueExact());
        parseEnum(RoundingPolicy.class, t.cell(r, Schema.META_ROUNDING)).ifPresent(p -> policy = p);
        String name = t.cell(r, Schema.META_NAME);
        networkName = name == null || name.isEmpty() ? null : name;
    }

    // ---- row level --------------------------------------------------------------------------

    private void collectDeclared(RawTable t, Column key, Set<String> into) {
        if (!t.has(key)) {
            return;
        }
        for (Row r : t.rows()) {
            String v = t.cell(r, key);
            if (!v.isEmpty()) {
                into.add(v);
            }
        }
    }

    private void readNodes(RawTable t) {
        boolean columnsOk = checkMissingRequiredColumns(t);
        if (!columnsOk) {
            rowsDropped |= !t.rows().isEmpty();
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Row r : t.rows()) {
            boolean ok = checkCells(t, r);
            String name = t.cell(r, Schema.NODE_NAME);
            ok &= checkDuplicateKey(t, r, name, seen, Schema.NODE_NAME, "node " + name);
            if (!ok) {
                rowsDropped = true;
                continue;
            }
            NodeType type = parseEnum(NodeType.class, t.cell(r, Schema.NODE_TYPE)).orElseThrow();
            nodes.put(name, new SNode(name, type, r.line()));
            duration(t, r, Schema.NODE_PROCESSING_VALUE, Schema.NODE_PROCESSING_UNIT, "Processing time", name);
        }
    }

    private void readLinks(RawTable t) {
        if (!checkMissingRequiredColumns(t)) {
            rowsDropped |= !t.rows().isEmpty();
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Row r : t.rows()) {
            boolean ok = checkCells(t, r);
            String source = t.cell(r, Schema.LINK_SOURCE);
            String target = t.cell(r, Schema.LINK_TARGET);
            String key = source + "->" + target;
            if (!source.isEmpty() && !target.isEmpty()) {
                ok &= checkSelfLoop(t, r, source, target);
                ok &= checkDuplicateKey(t, r, key, seen, Schema.LINK_SOURCE, "link " + source + " -> " + target);
            }
            ok &= checkNodeReference(t, r, Schema.LINK_SOURCE, source);
            ok &= checkNodeReference(t, r, Schema.LINK_TARGET, target);
            if (!ok) {
                rowsDropped = true;
                continue;
            }
            links.add(new SLink(source, target, r.line()));
            duration(t, r, Schema.LINK_LEAD_VALUE, Schema.LINK_LEAD_UNIT, "Lead time", key);
        }
    }

    private void readProducts(RawTable t) {
        if (!checkMissingRequiredColumns(t)) {
            rowsDropped |= !t.rows().isEmpty();
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Row r : t.rows()) {
            boolean ok = checkCells(t, r);
            String name = t.cell(r, Schema.PRODUCT_NAME);
            ok &= checkDuplicateKey(t, r, name, seen, Schema.PRODUCT_NAME, "product " + name);
            if (!ok) {
                rowsDropped = true;
            }
        }
    }

    private void readNodeProducts(RawTable t) {
        if (!checkMissingRequiredColumns(t)) {
            rowsDropped |= !t.rows().isEmpty();
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Row r : t.rows()) {
            boolean ok = checkCells(t, r);
            String node = t.cell(r, Schema.NP_NODE);
            String product = t.cell(r, Schema.NP_PRODUCT);
            if (!node.isEmpty() && !product.isEmpty()) {
                ok &= checkDuplicateKey(t, r, node + "|" + product, seen, Schema.NP_NODE,
                        "(" + node + ", " + product + ")");
            }
            ok &= checkNodeReference(t, r, Schema.NP_NODE, node);
            ok &= checkProductReference(t, r, product);
            if (!ok) {
                rowsDropped = true;
                continue;
            }
            BigDecimal demand = validNumber(t, r, Schema.NP_DEMAND_VALUE).orElse(BigDecimal.ZERO);
            nodeProducts.add(new SNodeProduct(node, product, demand, r.line()));
        }
    }

    /** Runs every cell-level rule over a row's mapped cells; false when any of them is an error. */
    private boolean checkCells(RawTable t, Row r) {
        boolean ok = true;
        for (Map.Entry<Column, Integer> e : t.columnIndex().entrySet()) {
            Column c = e.getKey();
            String v = r.cells().get(e.getValue());
            if (v.isEmpty()) {
                ok &= checkRequiredValue(t, r, c);
                continue;
            }
            switch (c.kind()) {
                case NODE_TYPE -> ok &= checkEnumValue(t, r, c, v, NodeType.class);
                case ROUNDING_POLICY -> ok &= checkEnumValue(t, r, c, v, RoundingPolicy.class);
                case TIME_UNIT -> ok &= checkTimeUnit(t, r, c, v);
                case INTEGER, DECIMAL2, NUMBER -> {
                    Optional<BigDecimal> n = checkNumber(t, r, c, v);
                    if (n.isEmpty()) {
                        ok = false;
                    } else {
                        ok &= checkProbability(t, r, c, n.get()) & checkRange(t, r, c, n.get());
                    }
                }
                default -> {
                }
            }
        }
        return ok;
    }

    private boolean checkRequiredValue(RawTable t, Row r, Column c) {
        if (!c.required()) {
            return true;
        }
        findings.add(Finding.row(Code.REQUIRED_VALUE_MISSING, t.sheet().fileName(), r.line(), c.name(),
                where(t, r) + c.name() + " is blank"));
        return false;
    }

    private <E extends Enum<E>> boolean checkEnumValue(RawTable t, Row r, Column c, String v, Class<E> type) {
        if (parseEnum(type, v).isPresent()) {
            return true;
        }
        findings.add(Finding.row(Code.UNKNOWN_ENUM_VALUE, t.sheet().fileName(), r.line(), c.name(),
                where(t, r) + c.name() + " \"" + v + "\" is not one of " + java.util.Arrays.toString(
                        type.getEnumConstants())));
        return false;
    }

    private boolean checkTimeUnit(RawTable t, Row r, Column c, String v) {
        if (UnitTokens.parse(v).isPresent()) {
            return true;
        }
        findings.add(Finding.row(Code.UNKNOWN_TIME_UNIT, t.sheet().fileName(), r.line(), c.name(),
                where(t, r) + c.name() + " \"" + v + "\" is not a time unit"));
        return false;
    }

    private Optional<BigDecimal> checkNumber(RawTable t, Row r, Column c, String v) {
        Optional<BigDecimal> n = Numbers.parse(v);
        if (n.isEmpty()) {
            findings.add(Finding.row(Code.NOT_A_NUMBER, t.sheet().fileName(), r.line(), c.name(),
                    where(t, r) + c.name() + " \"" + v + "\" is not a number"));
        }
        return n;
    }

    private boolean checkProbability(RawTable t, Row r, Column c, BigDecimal v) {
        if (c.check() != Column.Check.PROBABILITY || (v.signum() >= 0 && v.compareTo(BigDecimal.ONE) <= 0)) {
            return true;
        }
        findings.add(Finding.row(Code.PROBABILITY_OUT_OF_RANGE, t.sheet().fileName(), r.line(), c.name(),
                where(t, r) + c.name() + " " + Numbers.plain(v) + " is outside [0, 1]"));
        return false;
    }

    private boolean checkRange(RawTable t, Row r, Column c, BigDecimal v) {
        String problem = switch (c.check()) {
            case NON_NEGATIVE -> v.signum() < 0 ? "is negative" : null;
            case POSITIVE -> v.signum() <= 0 ? "must be greater than 0" : null;
            case AT_LEAST_ONE -> v.compareTo(BigDecimal.ONE) < 0 ? "must be at least 1" : null;
            default -> null;
        };
        if (problem == null) {
            return true;
        }
        findings.add(Finding.row(Code.OUT_OF_RANGE, t.sheet().fileName(), r.line(), c.name(),
                where(t, r) + c.name() + " " + Numbers.plain(v) + " " + problem));
        return false;
    }

    /** DUPLICATE_KEY on a key already declared by an earlier row (exact comparison after trimming). */
    private boolean checkDuplicateKey(RawTable t, Row r, String key, Set<String> seen, Column column, String what) {
        if (key == null || key.isEmpty() || seen.add(key)) {
            return true;
        }
        findings.add(Finding.row(Code.DUPLICATE_KEY, t.sheet().fileName(), r.line(), column.name(),
                where(t, r) + what + " is declared twice"));
        return false;
    }

    private boolean checkSelfLoop(RawTable t, Row r, String source, String target) {
        if (!source.equals(target)) {
            return true;
        }
        findings.add(Finding.row(Code.SELF_LOOP, t.sheet().fileName(), r.line(), Schema.LINK_TARGET.name(),
                where(t, r) + "link " + source + " -> " + target + " is a self-loop"));
        return false;
    }

    private boolean checkNodeReference(RawTable t, Row r, Column c, String name) {
        if (name.isEmpty() || declaredNodes.contains(name)) {
            return true;
        }
        findings.add(Finding.row(Code.UNKNOWN_NODE_REFERENCE, t.sheet().fileName(), r.line(), c.name(),
                where(t, r) + c.name() + " \"" + name + "\" names no node"));
        return false;
    }

    private boolean checkProductReference(RawTable t, Row r, String product) {
        if (!net.has(Sheet.PRODUCTS) || product.isEmpty() || declaredProducts.contains(product)) {
            return true;
        }
        findings.add(Finding.row(Code.UNKNOWN_PRODUCT_REFERENCE, t.sheet().fileName(), r.line(),
                Schema.NP_PRODUCT.name(), where(t, r) + "product \"" + product + "\" names no product"));
        return false;
    }

    // ---- network level ----------------------------------------------------------------------

    private void checkNoSupplyNode() {
        if (nodes.values().stream().noneMatch(n -> n.type().isSupply())) {
            findings.add(Finding.element(Code.NO_SUPPLY_NODE, Sheet.NODES.fileName(), null, null,
                    "the network has no SUPPLIER or PLANT node"));
        }
    }

    private void checkNoCustomerNode() {
        if (nodes.values().stream().noneMatch(n -> n.type() == NodeType.CUSTOMER)) {
            findings.add(Finding.element(Code.NO_CUSTOMER_NODE, Sheet.NODES.fileName(), null, null,
                    "the network has no CUSTOMER node"));
        }
    }

    private void checkNoDemandDeclared() {
        if (nodeProducts.stream().noneMatch(np -> nodes.containsKey(np.node()) && np.demand().signum() > 0)) {
            findings.add(Finding.element(Code.NO_DEMAND_DECLARED, Sheet.NODE_PRODUCTS.fileName(), null, null,
                    "no node-product row declares a demand_value above 0"));
        }
    }

    /** A customer with no path from any SUPPLIER or PLANT node. */
    private void checkCustomerUnreachable(List<SLink> graphLinks) {
        Map<String, List<String>> out = adjacency(graphLinks);
        Set<String> reached = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (SNode n : nodes.values()) {
            if (n.type().isSupply()) {
                reached.add(n.name());
                queue.add(n.name());
            }
        }
        while (!queue.isEmpty()) {
            for (String next : out.getOrDefault(queue.poll(), List.of())) {
                if (reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (SNode n : nodes.values()) {
            if (n.type() == NodeType.CUSTOMER && !reached.contains(n.name())) {
                findings.add(Finding.element(Code.CUSTOMER_UNREACHABLE, Sheet.NODES.fileName(), n.line(), n.name(),
                        "customer " + n.name() + " has no path from any SUPPLIER or PLANT node"));
            }
        }
    }

    /** The time-resolution checks of the SNRM design document: DURATION_ROUNDS_TO_ZERO (a non-zero duration that becomes 0 periods), DURATION_ROUNDING_ERROR (relative error above 10%) and PERIOD_TOO_FINE. */
    private void checkDurations(List<SLink> graphLinks) {
        Set<String> graphKeys = new LinkedHashSet<>();
        graphLinks.forEach(l -> graphKeys.add(l.key()));
        BigDecimal periodSeconds = periodValue.multiply(BigDecimal.valueOf(periodUnit.seconds()));
        Duration longest = null;
        BigDecimal longestPeriods = BigDecimal.ZERO;
        for (Duration d : durations) {
            if (d.value().signum() <= 0 || (d.sheet() == Sheet.LINKS && !graphKeys.contains(d.element()))) {
                continue;
            }
            BigDecimal exact = d.value().multiply(BigDecimal.valueOf(d.unit().seconds()))
                    .divide(periodSeconds, MathContext.DECIMAL128);
            BigDecimal rounded = policy.round(exact);
            if (rounded.signum() == 0) {
                checkDurationRoundsToZero(d);
            } else {
                checkDurationRoundingError(d, exact, rounded);
            }
            if (exact.compareTo(longestPeriods) > 0) {
                longest = d;
                longestPeriods = exact;
            }
        }
        checkPeriodTooFine(longest, longestPeriods);
    }

    private void checkDurationRoundsToZero(Duration d) {
        findings.add(Finding.element(Code.DURATION_ROUNDS_TO_ZERO, d.sheet().fileName(), d.line(), d.element(),
                d.label() + " " + Numbers.plain(d.value()) + " " + d.unit() + " of " + d.element()
                        + " converts to 0 periods of " + Numbers.plain(periodValue) + " " + periodUnit
                        + " under " + policy + "; it would be treated as instantaneous"));
    }

    private void checkDurationRoundingError(Duration d, BigDecimal exact, BigDecimal rounded) {
        BigDecimal rel = rounded.subtract(exact).divide(exact, MathContext.DECIMAL128);
        if (rel.abs().compareTo(TEN_PERCENT) <= 0) {
            return;
        }
        BigDecimal pct = rel.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        findings.add(Finding.element(Code.DURATION_ROUNDING_ERROR, d.sheet().fileName(), d.line(), d.element(),
                d.label() + " " + Numbers.plain(d.value()) + " " + d.unit() + " of " + d.element() + " rounds to "
                        + rounded.toPlainString() + " periods (" + (pct.signum() > 0 ? "+" : "") + pct + "%)"));
    }

    private void checkPeriodTooFine(Duration longest, BigDecimal periods) {
        if (longest == null || periods.compareTo(MAX_PERIODS) <= 0) {
            return;
        }
        findings.add(Finding.element(Code.PERIOD_TOO_FINE, longest.sheet().fileName(), longest.line(),
                longest.element(), longest.label() + " " + Numbers.plain(longest.value()) + " " + longest.unit()
                        + " of " + longest.element() + " spans " + periods.setScale(0, RoundingMode.HALF_UP)
                        + " periods, more than 1000; the period may be too fine"));
    }

    private void checkLateralLink(List<SLink> graphLinks) {
        for (SLink l : graphLinks) {
            NodeType s = nodes.get(l.source()).type();
            if (s == nodes.get(l.target()).type()) {
                findings.add(Finding.element(Code.LATERAL_LINK, Sheet.LINKS.fileName(), l.line(), l.key(),
                        "link " + l.source() + " -> " + l.target() + " joins two " + s + " nodes (lateral)"));
            }
        }
    }

    private void checkEchelonDirection(List<SLink> graphLinks) {
        for (SLink l : graphLinks) {
            NodeType s = nodes.get(l.source()).type();
            NodeType t = nodes.get(l.target()).type();
            if (s.rank() > t.rank()) {
                findings.add(Finding.element(Code.ECHELON_DIRECTION, Sheet.LINKS.fileName(), l.line(), l.key(),
                        "link " + l.source() + " -> " + l.target() + " runs upstream (" + s + " -> " + t + ")"));
            }
        }
    }

    private void checkOrphanNode(List<SLink> graphLinks) {
        Set<String> linked = new LinkedHashSet<>();
        for (SLink l : graphLinks) {
            linked.add(l.source());
            linked.add(l.target());
        }
        for (SNode n : nodes.values()) {
            if (!linked.contains(n.name())) {
                findings.add(Finding.element(Code.ORPHAN_NODE, Sheet.NODES.fileName(), n.line(), n.name(),
                        "node " + n.name() + " has no links"));
            }
        }
    }

    private void checkDemandOnNonCustomer() {
        for (SNodeProduct np : nodeProducts) {
            SNode n = nodes.get(np.node());
            if (np.demand().signum() > 0 && n != null && n.type() != NodeType.CUSTOMER) {
                findings.add(Finding.element(Code.DEMAND_ON_NON_CUSTOMER, Sheet.NODE_PRODUCTS.fileName(), np.line(),
                        np.node() + "|" + np.product(), n.type() + " node " + np.node() + " carries a demand of "
                                + Numbers.plain(np.demand()) + " for " + np.product()
                                + "; the engine puts demand on customers only"));
            }
        }
    }

    private void checkPartialGraph() {
        if (rowsDropped) {
            findings.add(Finding.element(Code.NETWORK_CHECKS_ON_PARTIAL_GRAPH, null, null, null,
                    "rows with errors were dropped; the network-level findings describe the graph without them"));
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private Map<String, List<String>> adjacency(List<SLink> graphLinks) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (SLink l : graphLinks) {
            out.computeIfAbsent(l.source(), k -> new ArrayList<>()).add(l.target());
        }
        return out;
    }

    /** Records a valid duration cell for the network-level resolution checks. */
    private void duration(RawTable t, Row r, Column valueCol, Column unitCol, String label, String element) {
        Optional<BigDecimal> v = validNumber(t, r, valueCol);
        if (v.isEmpty()) {
            return;
        }
        TimeUnit unit = resolveUnit(t.cell(r, unitCol));
        durations.add(new Duration(label, t.sheet(), r.line(), element, v.get(), unit));
    }

    /** A present-but-blank (or absent) unit cell means the period unit. */
    private TimeUnit resolveUnit(String cell) {
        if (cell == null || cell.isEmpty()) {
            return periodUnit;
        }
        return UnitTokens.parse(cell).orElse(periodUnit);
    }

    private static Optional<BigDecimal> validNumber(RawTable t, Row r, Column c) {
        return Optional.ofNullable(t.cell(r, c)).flatMap(Numbers::parse);
    }

    private static <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String v) {
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        String s = v.trim().toUpperCase(Locale.ROOT);
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(s)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    private static String where(RawTable t, Row r) {
        return t.sheet().fileName() + " line " + r.line() + ": ";
    }
}
