package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.FactLedger;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.TierRole;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Field;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Builds the fact statements of the sentence-per-fact styles (template, paraphrased) from the ledger, and the clauses the narrative style assembles into its own sentences. */
public final class StatementBuilder {

    /** The node clauses in template order, with the field each states. */
    public static final Map<Field, Slot> NODE_CLAUSES = orderedMap(
            Field.NODE_CAPACITY, Slot.NODE_CAPACITY, Field.NODE_PROCESSING_TIME, Slot.NODE_PROCESSING_TIME,
            Field.NODE_FIXED_COST, Slot.NODE_FIXED_COST, Field.NODE_VAR_COST, Slot.NODE_VAR_COST,
            Field.NODE_FAILURE_PROB, Slot.NODE_FAILURE_PROB);
    /** The link clauses in template order. */
    public static final Map<Field, Slot> LINK_CLAUSES = orderedMap(
            Field.LINK_LEAD_TIME, Slot.LINK_LEAD_TIME, Field.LINK_CAPACITY, Slot.LINK_CAPACITY,
            Field.LINK_UNIT_COST, Slot.LINK_UNIT_COST, Field.LINK_FAILURE_PROB, Slot.LINK_FAILURE_PROB);
    /** The inventory clauses in template order. */
    public static final Map<Field, Slot> INVENTORY_CLAUSES = orderedMap(
            Field.NP_INITIAL_INVENTORY, Slot.INVENTORY_INITIAL, Field.NP_SAFETY_STOCK, Slot.INVENTORY_SAFETY,
            Field.NP_HOLDING_COST, Slot.INVENTORY_HOLDING);

    /** A lane's distance in {@code --distance-mode km}: the lane mode word and the kilometres (canonical). */
    public record Distance(String mode, String km) {
    }

    private final Map<String, Fact> facts;
    private final Function<Slot, String> phrasing;
    private final NumberStyle numbers;
    private final Map<String, Distance> distances;

    public StatementBuilder(List<Fact> facts, Function<Slot, String> phrasing) {
        this(facts, phrasing, NumberStyle.PLAIN, Map.of());
    }

    public StatementBuilder(List<Fact> facts, Function<Slot, String> phrasing, NumberStyle numbers,
                            Map<String, Distance> distances) {
        this.facts = FactLedger.index(facts);
        this.phrasing = phrasing;
        this.numbers = numbers;
        this.distances = distances;
    }

    /** A canonical number as A writes it. */
    public String num(String canonical) {
        return numbers.format(canonical);
    }

    private static Map<Field, Slot> orderedMap(Object... pairs) {
        Map<Field, Slot> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Field) pairs[i], (Slot) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The stated fact, or null when it is unstated or does not exist. */
    public Fact stated(Field field, String entity) {
        Fact f = facts.get(FactLedger.key(field.sheet().factKind(), entity, field.stem()));
        return f != null && f.stated() ? f : null;
    }

    /** The fact whether stated or not (null when it does not exist). */
    public Fact any(Field field, String entity) {
        return facts.get(FactLedger.key(field.sheet().factKind(), entity, field.stem()));
    }

    /** A wording of a slot, filled. */
    public String phrase(Slot slot, Map<String, String> values) {
        return Templates.fill(phrasing.apply(slot), values);
    }

    /** One clause stating a fact: the value, its unit word (a rate's denominator, or a duration's singular/plural word) and, for inventory clauses, the product. */
    public String clause(Slot slot, Fact f, String product) {
        Distance distance = slot == Slot.LINK_LEAD_TIME ? distances.get(f.entity()) : null;
        if (distance != null) {
            return phrase(Slot.LINK_DISTANCE, Map.of("mode", distance.mode(), "value", num(distance.km())));
        }
        Map<String, String> v = new LinkedHashMap<>();
        v.put("value", num(f.value()));
        if (f.unit() != null) {
            TimeUnit unit = TimeUnit.valueOf(f.unit());
            v.put("unit", FactLedger.field(f).type() == Field.Type.DURATION
                    ? Templates.durationUnit(new BigDecimal(f.value()), unit) : Templates.rateUnit(unit));
        }
        if (product != null) {
            v.put("product", product);
        }
        return phrase(slot, v);
    }

    /** {@code X is a plant in the EU-West region. It has a capacity of …}; customers stop after the region. */
    public Statement node(Node n, TierRole role) {
        List<Integer> ids = new ArrayList<>();
        ids.add(stated(Field.NODE_EXISTS, n.name()).id());
        Fact region = stated(Field.NODE_REGION, n.name());
        String regionPart = "";
        if (region != null) {
            regionPart = phrase(Slot.NODE_REGION, Map.of("region", region.value()));
            ids.add(region.id());
        }
        String text = phrase(Slot.NODE, Map.of("name", n.name(), "role", role.withArticle(), "region", regionPart));
        if (n.type() != NodeType.CUSTOMER) {
            List<String> clauses = new ArrayList<>();
            NODE_CLAUSES.forEach((field, slot) -> add(clauses, ids, slot, stated(field, n.name()), null));
            if (!clauses.isEmpty()) {
                text += phrase(Slot.NODE_ATTRIBUTES, Map.of("clauses", Templates.join(clauses)));
            }
        }
        return Statement.fact(text, ids);
    }

    /** {@code X is located at latitude … and longitude …}, or null when the geo fact is unstated or absent. */
    public Statement geo(Node n) {
        Fact g = stated(Field.NODE_GEO, n.name());
        if (g == null) {
            return null;
        }
        String[] latLng = g.value().split(",");
        return Statement.fact(phrase(Slot.GEO, Map.of("name", n.name(), "lat", num(latLng[0]), "lng",
                num(latLng[1]))), List.of(g.id()));
    }

    /** A customer's demand for one product. */
    public Statement demand(NodeProduct np) {
        Fact exists = stated(Field.NP_EXISTS, np.key());
        Fact d = stated(Field.NP_DEMAND, np.key());
        String text = phrase(Slot.DEMAND, Map.of("name", np.node(), "value", num(d.value()), "product", np.product(),
                "unit", Templates.rateUnit(TimeUnit.valueOf(d.unit()))));
        return Statement.fact(text, List.of(exists.id(), d.id()));
    }

    /** A supply-side node's inventory of one product. */
    public Statement inventory(NodeProduct np) {
        List<Integer> ids = new ArrayList<>();
        ids.add(stated(Field.NP_EXISTS, np.key()).id());
        List<String> clauses = inventoryClauses(np, List.of(), ids);
        String text = phrase(Slot.INVENTORY, Map.of("name", np.node(), "clauses", Templates.join(clauses)));
        return Statement.fact(text, ids);
    }

    /** The inventory clauses of a node-product row, skipping excluded fields (stated elsewhere); opens with "stocks {product}" when no clause that names the product remains. */
    public List<String> inventoryClauses(NodeProduct np, List<Field> excluded, List<Integer> ids) {
        List<String> clauses = new ArrayList<>();
        Fact ii = excluded.contains(Field.NP_INITIAL_INVENTORY) ? null : stated(Field.NP_INITIAL_INVENTORY, np.key());
        Fact ss = excluded.contains(Field.NP_SAFETY_STOCK) ? null : stated(Field.NP_SAFETY_STOCK, np.key());
        Fact hc = excluded.contains(Field.NP_HOLDING_COST) ? null : stated(Field.NP_HOLDING_COST, np.key());
        if (ii == null && ss == null) {
            clauses.add(phrase(Slot.INVENTORY_BARE, Map.of("product", np.product())));
        }
        add(clauses, ids, Slot.INVENTORY_INITIAL, ii, np.product());
        add(clauses, ids, Slot.INVENTORY_SAFETY, ss, np.product());
        add(clauses, ids, Slot.INVENTORY_HOLDING, hc, np.product());
        return clauses;
    }

    /** {@code S ships to T with a lead time of …}. */
    public Statement link(Link l) {
        List<Integer> ids = new ArrayList<>();
        ids.add(stated(Field.LINK_EXISTS, l.key()).id());
        List<String> clauses = new ArrayList<>();
        LINK_CLAUSES.forEach((field, slot) -> add(clauses, ids, slot, stated(field, l.key()), null));
        String text = phrase(Slot.LINK, Map.of("source", l.source(), "target", l.target(),
                "clauses", Templates.join(clauses)));
        return Statement.fact(text, ids);
    }

    /** Links from one source with identical stated attributes, as one statement that carries every link's facts: S supplies A, B and C; each lane has a lead time of …. */
    public Statement linkGroup(List<Link> group) {
        List<Integer> ids = new ArrayList<>();
        for (Link l : group) {
            ids.add(stated(Field.LINK_EXISTS, l.key()).id());
            for (Field field : LINK_CLAUSES.keySet()) {
                Fact f = stated(field, l.key());
                if (f != null) {
                    ids.add(f.id());
                }
            }
        }
        List<String> clauses = new ArrayList<>();
        LINK_CLAUSES.forEach((field, slot) -> {
            Fact f = stated(field, group.get(0).key());
            if (f != null) {
                clauses.add(clause(slot, f, null));
            }
        });
        String text = phrase(Slot.LINK_GROUP, Map.of("source", group.get(0).source(),
                "targets", Templates.join(group.stream().map(Link::target).toList()),
                "clauses", Templates.join(clauses)));
        return Statement.fact(text, ids);
    }

    /** The attribute signature a link is grouped by: every link-clause field's stated value and unit (or "unstated"). */
    public List<String> linkSignature(Link l) {
        List<String> sig = new ArrayList<>();
        for (Field field : LINK_CLAUSES.keySet()) {
            Fact f = stated(field, l.key());
            sig.add(f == null ? "unstated" : f.value() + " " + f.unit());
        }
        return sig;
    }

    private void add(List<String> clauses, List<Integer> ids, Slot slot, Fact f, String product) {
        if (f == null) {
            return;
        }
        clauses.add(clause(slot, f, product));
        ids.add(f.id());
    }
}
