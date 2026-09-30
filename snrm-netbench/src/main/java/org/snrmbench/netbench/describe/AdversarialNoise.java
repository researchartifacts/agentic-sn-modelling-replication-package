package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Namer;
import org.snrmbench.netbench.generate.Ranges;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.model.TierRole;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.util.Numbers;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Adversarial noise: fact-template sentences opened by a marker that places them outside the current network ("Until 2021, …", "In the competitor's network, …"). */
final class AdversarialNoise {

    private static final List<String> KINDS = List.of("node", "link", "demand", "inventory");
    private static final BigDecimal MIN_CHANGE = new BigDecimal("0.20");
    /** Marker years ("Until {year}", "Before the {year} restructuring") lie clearly in the past for any reader: A carries no date, so a recent year could be read as still current. */
    static final int FIRST_MARKER_YEAR = 2008;
    static final int LAST_MARKER_YEAR = 2019;

    private final Network truth;
    private final StatementBuilder facts;
    private final List<Fact> ledger;
    private final Map<String, StatementBuilder.Distance> distances;
    private final GenerateParams p;
    private final Rng rng;
    private final List<Decoy> decoys = new ArrayList<>();
    private final Set<String> decoyEntities = new LinkedHashSet<>();
    private final Set<String> usedNames = new LinkedHashSet<>();

    AdversarialNoise(Network truth, List<Fact> facts, GenerateParams p, Rng rng) {
        this.truth = truth;
        this.ledger = facts;
        this.distances = p.distanceMode() == GenerateParams.DistanceMode.KM ? Describer.distances(truth, facts)
                : Map.of();
        this.facts = new StatementBuilder(facts, Slot::template, NumberStyle.of(p.locale()), Map.of());
        this.p = p;
        this.rng = rng;
        truth.nodes().forEach(n -> usedNames.add(norm(n.name())));
    }

    List<Decoy> decoys() {
        return decoys;
    }

    List<String> decoyEntities() {
        return List.copyOf(decoyEntities);
    }

    /** The next decoy sentence; its decoy gets the given id. */
    Statement next(int id) {
        String marker = Templates.fill(rng.choice(Stage.NOISE, Templates.ADVERSARIAL_MARKERS), Map.of(
                "year", Integer.toString(rng.uniformInt(Stage.NOISE, FIRST_MARKER_YEAR, LAST_MARKER_YEAR)),
                "product", p.finalProductName()));
        String kind = rng.choice(Stage.NOISE, KINDS);
        boolean realEntity = rng.bernoulli(Stage.NOISE, 0.5);
        Draft d = realEntity ? realValue(kind) : null;
        if (d == null) {
            d = decoyEntity(kind);
        }
        decoys.add(new Decoy(id, marker.trim(), kind, d.entity, d.field, d.value, d.unit, d.decoyEntity, 0));
        return Statement.decoy(marker + d.text, id);
    }

    /** One false fact: the sentence body and what the decoy ledger records. */
    private record Draft(String text, String entity, String field, String value, String unit, boolean decoyEntity) {
    }

    // ---- a real entity with a false value ---------------------------------------------------

    private Draft realValue(String kind) {
        return switch (kind) {
            case "node" -> realNode();
            case "link" -> realLink();
            case "demand" -> realDemand();
            default -> realInventory();
        };
    }

    private Draft realNode() {
        Node n = rng.choice(Stage.NOISE, truth.nodes());
        TierRole role = role(n);
        List<Field> fields = new ArrayList<>();
        for (Field f : List.of(Field.NODE_REGION, Field.NODE_CAPACITY, Field.NODE_FIXED_COST, Field.NODE_VAR_COST,
                Field.NODE_FAILURE_PROB)) {
            if (facts.stated(f, n.name()) != null) {
                fields.add(f);
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        Field field = rng.choice(Stage.NOISE, fields);
        Fact truthFact = facts.stated(field, n.name());
        if (field == Field.NODE_REGION) {
            List<String> others = new ArrayList<>(NoiseInjector.allRegions());
            others.remove(truthFact.value());
            String region = rng.choice(Stage.NOISE, others);
            String text = Templates.fill(Templates.NODE, Map.of("name", n.name(), "role", role.withArticle(),
                    "region", Templates.fill(Templates.NODE_REGION, Map.of("region", region))));
            return new Draft(text, n.name(), field.stem(), region, null, false);
        }
        Fact decoy = perturbed(truthFact, field);
        String text = Templates.fill(Templates.NODE, Map.of("name", n.name(), "role", role.withArticle(),
                "region", "")) + Templates.fill(Templates.NODE_ATTRIBUTES,
                Map.of("clauses", facts.clause(StatementBuilder.NODE_CLAUSES.get(field), decoy, null)));
        return new Draft(text, n.name(), field.stem(), decoy.value(), decoy.unit(), false);
    }

    private Draft realLink() {
        Link l = rng.choice(Stage.NOISE, truth.links());
        List<Field> fields = new ArrayList<>();
        for (Field f : List.of(Field.LINK_LEAD_TIME, Field.LINK_CAPACITY, Field.LINK_UNIT_COST)) {
            if (facts.stated(f, l.key()) != null) {
                fields.add(f);
            }
        }
        Field field = rng.choice(Stage.NOISE, fields);
        Fact decoy = perturbed(facts.stated(field, l.key()), field);
        String clause = field == Field.LINK_LEAD_TIME
                ? leadClause(decoy, distances.containsKey(l.key())
                        && distances.get(l.key()).mode().equals(Templates.MODE_OCEAN))
                : facts.clause(StatementBuilder.LINK_CLAUSES.get(field), decoy, null);
        String text = Templates.fill(Templates.LINK, Map.of("source", l.source(), "target", l.target(),
                "clauses", clause));
        return new Draft(text, l.key(), field.stem(), decoy.value(), decoy.unit(), false);
    }

    /** A lead-time clause: the lead time itself, or — in distance mode, where every lane states a distance — the distance that lead time covers at the lane's speed. */
    private String leadClause(Fact lead, boolean ocean) {
        if (p.distanceMode() != GenerateParams.DistanceMode.KM) {
            return facts.clause(Slot.LINK_LEAD_TIME, lead, null);
        }
        int speed = ocean ? Ranges.OCEAN_KM_PER_DAY : Ranges.ROAD_KM_PER_DAY;
        BigDecimal km = new BigDecimal(lead.value()).multiply(BigDecimal.valueOf(TimeUnit.valueOf(lead.unit()).seconds()))
                .multiply(BigDecimal.valueOf(speed))
                .divide(BigDecimal.valueOf(TimeUnit.DAY.seconds()), java.math.MathContext.DECIMAL128);
        StatementBuilder.Distance d = new StatementBuilder.Distance(ocean ? Templates.MODE_OCEAN : Templates.MODE_ROAD,
                Numbers.plain(km));
        return new StatementBuilder(ledger, Slot::template, NumberStyle.of(p.locale()), Map.of(lead.entity(), d))
                .clause(Slot.LINK_LEAD_TIME, lead, null);
    }

    private Draft realDemand() {
        List<NodeProduct> rows = truth.nodeProducts().stream().filter(np -> type(np.node()) == NodeType.CUSTOMER)
                .toList();
        NodeProduct np = rng.choice(Stage.NOISE, rows);
        Fact decoy = perturbed(facts.stated(Field.NP_DEMAND, np.key()), Field.NP_DEMAND);
        String text = Templates.fill(Templates.DEMAND, Map.of("name", np.node(), "value", this.facts.num(decoy.value()),
                "product", np.product(), "unit", Templates.rateUnit(TimeUnit.valueOf(decoy.unit()))));
        return new Draft(text, np.key(), Field.NP_DEMAND.stem(), decoy.value(), decoy.unit(), false);
    }

    private Draft realInventory() {
        List<NodeProduct> rows = truth.nodeProducts().stream().filter(np -> type(np.node()) != NodeType.CUSTOMER)
                .toList();
        NodeProduct np = rng.choice(Stage.NOISE, rows);
        List<Field> fields = new ArrayList<>();
        for (Field f : StatementBuilder.INVENTORY_CLAUSES.keySet()) {
            if (facts.stated(f, np.key()) != null) {
                fields.add(f);
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        Field field = rng.choice(Stage.NOISE, fields);
        Fact decoy = perturbed(facts.stated(field, np.key()), field);
        List<String> clauses = new ArrayList<>();
        if (field == Field.NP_HOLDING_COST) {
            clauses.add(Templates.fill(Templates.INVENTORY_BARE, Map.of("product", np.product())));
        }
        clauses.add(facts.clause(StatementBuilder.INVENTORY_CLAUSES.get(field), decoy, np.product()));
        String text = Templates.fill(Templates.INVENTORY, Map.of("name", np.node(),
                "clauses", Templates.join(clauses)));
        return new Draft(text, np.key(), field.stem(), decoy.value(), decoy.unit(), false);
    }

    /** The fact with a value at least 20% away from the truth (up by 25–100% or down by 25–70%), in the same unit and format; a zero becomes a small positive value. */
    private Fact perturbed(Fact f, Field field) {
        Column.Kind kind = field.column().kind();
        int scale = kind == Column.Kind.DECIMAL2 ? 2 : 0;
        BigDecimal v = new BigDecimal(f.value());
        BigDecimal min = BigDecimal.ONE.movePointLeft(scale);
        BigDecimal nv;
        if (v.signum() == 0) {
            nv = scale == 0 ? BigDecimal.valueOf(rng.uniformInt(Stage.NOISE, 10, 100))
                    : rng.uniformDecimal(Stage.NOISE, new BigDecimal("0.05"), new BigDecimal("0.50"), 2);
        } else {
            boolean up = rng.bernoulli(Stage.NOISE, 0.5);
            BigDecimal factor = up
                    ? rng.uniformDecimal(Stage.NOISE, new BigDecimal("1.25"), new BigDecimal("2.00"), 2)
                    : rng.uniformDecimal(Stage.NOISE, new BigDecimal("0.30"), new BigDecimal("0.75"), 2);
            nv = v.multiply(factor).setScale(scale, RoundingMode.HALF_UP).max(min);
            if (nv.subtract(v).abs().compareTo(v.multiply(MIN_CHANGE)) < 0) {
                // rounding a small value can erase the change: move up far enough instead
                nv = v.multiply(new BigDecimal("1.5")).setScale(scale, RoundingMode.CEILING).max(v.add(min));
            }
        }
        if (field == Field.NODE_FAILURE_PROB) {
            nv = nv.min(BigDecimal.ONE);
        }
        return new Fact(0, f.kind(), f.entity(), f.field(), Numbers.format(nv, kind), f.unit(), true, false, null);
    }

    // ---- an entity that does not exist ------------------------------------------------------

    private Draft decoyEntity(String kind) {
        TimeUnit unit = p.periodUnit();
        switch (kind) {
            case "node" -> {
                Tier t = rng.choice(Stage.NOISE, truth.tiers());
                String region = rng.choice(Stage.NOISE, NoiseInjector.allRegions());
                String name = decoyName(t, region);
                String text = Templates.fill(Templates.NODE, Map.of("name", name, "role", t.role().withArticle(),
                        "region", Templates.fill(Templates.NODE_REGION, Map.of("region", region))));
                if (t.type() != NodeType.CUSTOMER) {
                    Fact cap = new Fact(0, "node", name, Field.NODE_CAPACITY.stem(),
                            Integer.toString(rng.uniformInt(Stage.NOISE, 5, 200) * 10), unit.name(), true, false, null);
                    text += Templates.fill(Templates.NODE_ATTRIBUTES,
                            Map.of("clauses", facts.clause(Slot.NODE_CAPACITY, cap, null)));
                }
                return new Draft(text, name, Field.NODE_EXISTS.stem(), t.type().name(), null, true);
            }
            case "link" -> {
                int from = rng.uniformInt(Stage.NOISE, 0, truth.tiers().size() - 2);
                Tier source = truth.tiers().get(from);
                Tier target = truth.tiers().get(from + 1);
                String a = rng.choice(Stage.NOISE, source.nodeNames());
                String drawn = rng.choice(Stage.NOISE, target.nodeNames());
                boolean exists = truth.links().stream().anyMatch(l -> l.source().equals(a) && l.target().equals(drawn));
                String b = exists ? decoyName(target, null) : drawn; // an existing pair leads to a node that does not exist
                String lead = Integer.toString(rng.uniformInt(Stage.NOISE, 1, 5) * p.periodValue());
                Fact lt = new Fact(0, "link", a + "->" + b, Field.LINK_LEAD_TIME.stem(), lead, unit.name(), true,
                        false, null);
                Node sourceNode = truth.node(a).orElseThrow();
                Fact sourceRegion = facts.any(Field.NODE_REGION, a);
                boolean ocean = sourceNode.type() == NodeType.SUPPLIER && target.type() == NodeType.PLANT
                        && sourceRegion != null && Ranges.OVERSEAS_REGIONS.contains(sourceRegion.value());
                String text = Templates.fill(Templates.LINK, Map.of("source", a, "target", b,
                        "clauses", leadClause(lt, ocean)));
                return new Draft(text, lt.entity(), lt.field(), lead, unit.name(), true);
            }
            case "demand" -> {
                Tier customers = truth.tiers().get(truth.tiers().size() - 1);
                String name = decoyName(customers, null);
                String product = rng.choice(Stage.NOISE, NoiseInjector.productNames(truth));
                String value = Integer.toString(rng.uniformInt(Stage.NOISE, 10, 60));
                String text = Templates.fill(Templates.DEMAND, Map.of("name", name, "value", facts.num(value),
                        "product", product, "unit", Templates.rateUnit(unit)));
                return new Draft(text, name + "|" + product, Field.NP_DEMAND.stem(), value, unit.name(), true);
            }
            default -> {
                Tier t = truth.tiers().get(rng.uniformInt(Stage.NOISE, 0, truth.tiers().size() - 2));
                String name = decoyName(t, null);
                String product = rng.choice(Stage.NOISE, NoiseInjector.productNames(truth));
                Fact ii = new Fact(0, "node_product", name + "|" + product, Field.NP_INITIAL_INVENTORY.stem(),
                        Integer.toString(rng.uniformInt(Stage.NOISE, 5, 100) * 10), null, true, false, null);
                String text = Templates.fill(Templates.INVENTORY, Map.of("name", name,
                        "clauses", facts.clause(Slot.INVENTORY_INITIAL, ii, product)));
                return new Draft(text, ii.entity(), ii.field(), ii.value(), null, true);
            }
        }
    }

    /** A plausible name that no real node and no earlier decoy has: the tier's next typed index, the next neutral number, or an unused company name. */
    private String decoyName(Tier t, String region) {
        String name;
        switch (p.naming()) {
            case TYPED -> {
                String prefix = Namer.typedPrefixes(truth.tiers().size()).get(t.index());
                int width = t.nodeNames().size() >= 100 ? 3 : 2;
                int k = t.nodeNames().size() + 1;
                do {
                    name = prefix + "-" + pad(k++, width);
                } while (usedNames.contains(norm(name)));
            }
            case NEUTRAL -> {
                int n = truth.nodes().size();
                int width = Math.max(2, Integer.toString(n).length());
                int k = n + 1;
                do {
                    name = "N" + pad(k++, width);
                } while (usedNames.contains(norm(name)));
            }
            default -> {
                List<String> all = t.type() == NodeType.SUPPLIER ? NoiseInjector.companies() : cities(region);
                List<String> unused = new ArrayList<>(all);
                unused.removeIf(x -> usedNames.contains(norm(x)));
                if (!unused.isEmpty()) {
                    name = rng.choice(Stage.NOISE, unused);
                } else {
                    // every name is taken: reuse one with the next free Roman numeral, as Namer does
                    String base = rng.choice(Stage.NOISE, all);
                    int k = 2;
                    do {
                        name = base + " " + Namer.roman(k++);
                    } while (usedNames.contains(norm(name)));
                }
            }
        }
        usedNames.add(norm(name));
        decoyEntities.add(name);
        return name;
    }

    /** The cities of a region, or of every European region when none is given. */
    private static List<String> cities(String region) {
        Map<String, List<String>> byRegion = Namer.citiesByRegion();
        if (region != null && byRegion.containsKey(region)) {
            return byRegion.get(region);
        }
        List<String> european = new ArrayList<>();
        byRegion.forEach((r, list) -> {
            if (r.startsWith("EU-")) {
                european.addAll(list);
            }
        });
        return european;
    }

    private TierRole role(Node n) {
        return truth.tiers().get(n.tierIndex()).role();
    }

    private NodeType type(String node) {
        return truth.node(node).orElseThrow().type();
    }

    private static String norm(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String pad(int value, int width) {
        String s = Integer.toString(value);
        return "0".repeat(Math.max(0, width - s.length())) + s;
    }
}
