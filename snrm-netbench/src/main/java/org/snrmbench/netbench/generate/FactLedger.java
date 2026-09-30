package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.Quantity;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.schema.Conventions;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.util.Numbers;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stage coverage: one fact per scored field of the full network, in canonical order (meta, products, nodes, links, node-products; fields in Field order). */
public final class FactLedger {

    private FactLedger() {
    }

    /** Every fact of the full network with its {@code stated} flag; sentences are assigned later. */
    public static List<Fact> build(Network full, GenerateParams p, Rng rng) {
        List<Fact> facts = new ArrayList<>();
        TimeBase tb = full.timeBase();
        String meta = Fact.META_ENTITY;
        add(facts, Field.META_NAME, meta, tb.name(), null);
        add(facts, Field.META_PERIOD_LENGTH, meta, Numbers.integer(tb.periodValue()), tb.periodUnit().name());
        add(facts, Field.META_HORIZON, meta, Integer.toString(tb.horizonPeriods()), null);
        add(facts, Field.META_ROUNDING, meta, tb.policy().name(), null);
        for (Product pr : full.products()) {
            add(facts, Field.PRODUCT_EXISTS, pr.name(), "true", null);
            add(facts, Field.PRODUCT_UNIT_VALUE, pr.name(), num(Field.PRODUCT_UNIT_VALUE, pr.unitValue()), null);
        }
        for (Node n : full.nodes()) {
            add(facts, Field.NODE_EXISTS, n.name(), n.type().name(), null);
            add(facts, Field.NODE_REGION, n.name(), n.region(), null);
            if (n.type() != NodeType.CUSTOMER) {
                quantity(facts, Field.NODE_CAPACITY, n.name(), n.capacity());
                quantity(facts, Field.NODE_PROCESSING_TIME, n.name(), n.processingTime());
                add(facts, Field.NODE_FIXED_COST, n.name(), num(Field.NODE_FIXED_COST, n.fixedCost()), null);
                add(facts, Field.NODE_VAR_COST, n.name(), num(Field.NODE_VAR_COST, n.varCost()), null);
                add(facts, Field.NODE_FAILURE_PROB, n.name(), num(Field.NODE_FAILURE_PROB, n.failureProb()), null);
            }
            if (p.geo()) {
                add(facts, Field.NODE_GEO, n.name(), Numbers.decimal2(n.lat()) + "," + Numbers.decimal2(n.lng()), null);
            }
        }
        for (Link l : full.links()) {
            add(facts, Field.LINK_EXISTS, l.key(), "true", null);
            quantity(facts, Field.LINK_LEAD_TIME, l.key(), l.leadTime());
            quantity(facts, Field.LINK_CAPACITY, l.key(), l.capacity());
            add(facts, Field.LINK_UNIT_COST, l.key(), num(Field.LINK_UNIT_COST, l.unitCost()), null);
            add(facts, Field.LINK_FAILURE_PROB, l.key(), num(Field.LINK_FAILURE_PROB, l.failureProb()), null);
        }
        Map<String, NodeType> types = new LinkedHashMap<>();
        full.nodes().forEach(n -> types.put(n.name(), n.type()));
        for (NodeProduct np : full.nodeProducts()) {
            NodeType type = types.get(np.node());
            add(facts, Field.NP_EXISTS, np.key(), "true", null);
            if (!Conventions.isConvention(Field.NP_DEMAND, type)) {
                quantity(facts, Field.NP_DEMAND, np.key(), np.demand());
            }
            if (!Conventions.isConvention(Field.NP_INITIAL_INVENTORY, type)) {
                add(facts, Field.NP_INITIAL_INVENTORY, np.key(),
                        num(Field.NP_INITIAL_INVENTORY, np.initialInventory()), null);
                add(facts, Field.NP_SAFETY_STOCK, np.key(), num(Field.NP_SAFETY_STOCK, np.safetyStock()), null);
                quantity(facts, Field.NP_HOLDING_COST, np.key(), np.holdingCost());
            }
        }
        List<Fact> out = new ArrayList<>(facts.size());
        for (Fact f : facts) {
            Field field = field(f);
            boolean stated = !field.optional() || rng.bernoulli(Stage.COVERAGE, p.attributeCoverage().doubleValue());
            out.add(f.withStated(stated));
        }
        return out;
    }

    /** The schema field a fact is about. */
    public static Field field(Fact f) {
        return Field.byStem(org.snrmbench.netbench.schema.Sheet.byFactKind(f.kind()).orElseThrow(), f.field())
                .orElseThrow(() -> new IllegalArgumentException("unknown fact field " + f.kind() + "." + f.field()));
    }

    /** {@code kind|entity|field}, the lookup key of a fact. */
    public static String key(String kind, String entity, String field) {
        return kind + "|" + entity + "|" + field;
    }

    public static String key(Fact f) {
        return key(f.kind(), f.entity(), f.field());
    }

    public static Map<String, Fact> index(List<Fact> facts) {
        Map<String, Fact> m = new LinkedHashMap<>();
        facts.forEach(f -> m.put(key(f), f));
        return m;
    }

    /** B: the full network with every unstated fact blanked (value and unit cell alike). */
    public static Network blank(Network full, List<Fact> facts) {
        Map<String, Fact> idx = index(facts);
        List<Node> nodes = new ArrayList<>();
        for (Node n : full.nodes()) {
            String k = Field.NODE_EXISTS.sheet().factKind();
            nodes.add(new Node(n.name(), n.type(), n.tierIndex(),
                    keep(idx, k, n.name(), Field.NODE_CAPACITY) ? n.capacity() : null,
                    keep(idx, k, n.name(), Field.NODE_PROCESSING_TIME) ? n.processingTime() : null,
                    keep(idx, k, n.name(), Field.NODE_FIXED_COST) ? n.fixedCost() : null,
                    keep(idx, k, n.name(), Field.NODE_VAR_COST) ? n.varCost() : null,
                    keep(idx, k, n.name(), Field.NODE_FAILURE_PROB) ? n.failureProb() : null,
                    keep(idx, k, n.name(), Field.NODE_REGION) ? n.region() : null,
                    keep(idx, k, n.name(), Field.NODE_GEO) ? n.lat() : null,
                    keep(idx, k, n.name(), Field.NODE_GEO) ? n.lng() : null));
        }
        List<Link> links = new ArrayList<>();
        for (Link l : full.links()) {
            String k = Field.LINK_EXISTS.sheet().factKind();
            links.add(new Link(l.source(), l.target(), l.leadTime(),
                    keep(idx, k, l.key(), Field.LINK_CAPACITY) ? l.capacity() : null,
                    keep(idx, k, l.key(), Field.LINK_UNIT_COST) ? l.unitCost() : null,
                    keep(idx, k, l.key(), Field.LINK_FAILURE_PROB) ? l.failureProb() : null));
        }
        List<NodeProduct> nps = new ArrayList<>();
        for (NodeProduct np : full.nodeProducts()) {
            String k = Field.NP_EXISTS.sheet().factKind();
            nps.add(new NodeProduct(np.node(), np.product(), np.demand(),
                    keep(idx, k, np.key(), Field.NP_INITIAL_INVENTORY) ? np.initialInventory() : null,
                    keep(idx, k, np.key(), Field.NP_SAFETY_STOCK) ? np.safetyStock() : null,
                    keep(idx, k, np.key(), Field.NP_HOLDING_COST) ? np.holdingCost() : null));
        }
        return new Network(full.timeBase(), full.tiers(), full.products(), nodes, links, nps);
    }

    /** A field is kept unless the ledger holds it as an unstated fact (convention fields are not facts). */
    private static boolean keep(Map<String, Fact> idx, String kind, String entity, Field field) {
        Fact f = idx.get(key(kind, entity, field.stem()));
        return f == null || f.stated();
    }

    private static void add(List<Fact> facts, Field field, String entity, String value, String unit) {
        facts.add(new Fact(facts.size() + 1, field.sheet().factKind(), entity, field.stem(), value, unit, true,
                false, null));
    }

    private static void quantity(List<Fact> facts, Field field, String entity, Quantity q) {
        add(facts, field, entity, num(field, q.value()), q.unit().name());
    }

    private static String num(Field field, BigDecimal v) {
        return Numbers.format(v, field.column().kind());
    }
}
