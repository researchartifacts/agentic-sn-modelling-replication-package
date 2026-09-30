package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.csv.CanonicalCsvWriter;
import org.snrmbench.netbench.describe.ParaphrasePool.Narrative;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The narrative style: sections Overview, Facilities, Logistics lanes, Demand and inventory, worded from the paraphrase and narrative pools. */
final class NarrativeRenderer {

    /** The sections, and the ids of facts stated relative to another entity. */
    record Body(List<Section> sections, Set<Integer> relative) {
    }

    private static final Map<Field, String> FIELD_WORDS = Map.of(
            Field.NODE_CAPACITY, "capacity",
            Field.NODE_PROCESSING_TIME, "processing time",
            Field.NODE_FIXED_COST, "fixed cost",
            Field.NODE_VAR_COST, "variable cost",
            Field.NODE_FAILURE_PROB, "failure probability",
            Field.NP_INITIAL_INVENTORY, "initial inventory",
            Field.NP_SAFETY_STOCK, "safety stock",
            Field.NP_HOLDING_COST, "holding cost");

    private final Network truth;
    private final StatementBuilder sb;
    private final ParaphrasePool pool;
    private final GenerateParams p;
    private final Rng rng;
    private final Set<Integer> relative = new LinkedHashSet<>();
    /** tier|field|product|value unit → the first node that stated it absolutely. */
    private final Map<String, String> firstStated = new LinkedHashMap<>();
    /** A relative fact id → the node it is stated relative to. */
    private final Map<Integer, String> relativeTo = new LinkedHashMap<>();

    private NarrativeRenderer(Network truth, StatementBuilder sb, ParaphrasePool pool, GenerateParams p, Rng rng) {
        this.truth = truth;
        this.sb = sb;
        this.pool = pool;
        this.p = p;
        this.rng = rng;
    }

    static Body render(Network truth, StatementBuilder sb, ParaphrasePool pool, GenerateParams p, Rng rng) {
        return new NarrativeRenderer(truth, sb, pool, p, rng).run();
    }

    private Body run() {
        Map<Integer, List<Node>> tiers = entityOrder();
        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Overview", List.of(overview(tiers))));
        sections.add(new Section("Facilities", facilities(tiers)));
        sections.add(new Section("Logistics lanes", lanes(tiers)));
        sections.add(new Section("Demand and inventory", demandAndInventory(tiers)));
        return new Body(sections, relative);
    }

    /** Nodes per tier in name order, or shuffled within the tier for {@code --order shuffled}. */
    private Map<Integer, List<Node>> entityOrder() {
        Map<Integer, List<Node>> out = new LinkedHashMap<>();
        for (Tier t : truth.tiers()) {
            List<Node> nodes = new ArrayList<>(CanonicalCsvWriter.sortedNodes(truth.nodes()).stream()
                    .filter(n -> n.tierIndex() == t.index()).toList());
            if (p.order() == GenerateParams.Order.SHUFFLED) {
                rng.shuffle(nodes, Stage.STYLE);
            }
            out.put(t.index(), nodes);
        }
        return out;
    }

    // ---- Overview ---------------------------------------------------------------------------

    private List<Statement> overview(Map<Integer, List<Node>> tiers) {
        List<Statement> out = new ArrayList<>();
        for (Tier t : truth.tiers()) {
            List<Node> members = tiers.get(t.index());
            List<Integer> ids = members.stream().map(n -> sb.stated(Field.NODE_EXISTS, n.name()).id()).toList();
            Map<String, String> v = new LinkedHashMap<>();
            v.put("name", members.get(0).name());
            v.put("role", t.role().word());
            v.put("roles", t.role().plural());
            v.put("members", Templates.join(members.stream().map(Node::name).toList()));
            v.put("count", sb.num(Integer.toString(members.size())));
            Narrative phrase = members.size() == 1 ? Narrative.OVERVIEW_TIER_ONE : Narrative.OVERVIEW_TIER_MANY;
            out.add(Statement.fact(Templates.fill(pool.draw(phrase), v), ids));
        }
        return out;
    }

    // ---- Facilities -------------------------------------------------------------------------

    private List<List<Statement>> facilities(Map<Integer, List<Node>> tiers) {
        List<List<Statement>> paragraphs = new ArrayList<>();
        for (Tier t : truth.tiers()) {
            List<Statement> para = new ArrayList<>();
            if (t.type() == NodeType.CUSTOMER) {
                para.addAll(customerRegions(tiers.get(t.index())));
                for (Node n : tiers.get(t.index())) {
                    Statement geo = sb.geo(n);
                    if (geo != null) {
                        para.add(geo);
                    }
                }
            } else {
                for (Node n : tiers.get(t.index())) {
                    Statement s = facility(n);
                    if (s != null) {
                        para.add(s);
                    }
                }
            }
            paragraphs.add(para);
        }
        return paragraphs;
    }

    /** A supply-side node's unit: region, absolute attributes, relative attributes, geo; null when empty. */
    private Statement facility(Node n) {
        Chain chain = new Chain(n);
        Fact region = sb.stated(Field.NODE_REGION, n.name());
        if (region != null) {
            chain.add(Templates.fill(pool.draw(Narrative.FACILITY_REGION),
                    Map.of("name", n.name(), "region", region.value())), region.id());
        }
        List<String> clauses = new ArrayList<>();
        List<Integer> clauseIds = new ArrayList<>();
        Map<Field, Fact> relatives = new LinkedHashMap<>();
        StatementBuilder.NODE_CLAUSES.forEach((field, slot) -> {
            Fact f = sb.stated(field, n.name());
            if (f == null) {
                return;
            }
            String other = referent(n.tierIndex(), field, null, f, n.name());
            if (other != null) {
                relatives.put(field, f);
                relativeTo.put(f.id(), other);
            } else {
                clauses.add(sb.clause(slot, f, null));
                clauseIds.add(f.id());
            }
        });
        if (!clauses.isEmpty()) {
            chain.add(Templates.fill(pool.draw(Narrative.FACILITY_ATTRIBUTES),
                    Map.of("subject", chain.subject(), "clauses", Templates.join(clauses))), clauseIds);
        }
        relatives.forEach((field, f) -> {
            chain.addRelative(Templates.fill(pool.draw(Narrative.RELATIVE_NODE), Map.of("subject", chain.subject(),
                    "field", FIELD_WORDS.get(field), "other", relativeTo.get(f.id()))), f.id());
            relative.add(f.id());
        });
        Statement geo = sb.geo(n);
        if (geo != null) {
            chain.add(geo.text(), geo.factIds());
        }
        return chain.statement();
    }

    /** The earlier node of the same tier whose absolute statement of the same field (and product) has the same value and unit; null (and this node becomes the referent) when there is none. */
    private String referent(int tier, Field field, String product, Fact f, String node) {
        String key = tier + "|" + field + "|" + product + "|" + f.value() + " " + f.unit();
        String first = firstStated.get(key);
        if (first == null) {
            firstStated.put(key, node);
            return null;
        }
        return first;
    }

    /** Customers grouped by stated region, in order of first appearance. */
    private List<Statement> customerRegions(List<Node> customers) {
        Map<String, List<Node>> byRegion = new LinkedHashMap<>();
        for (Node c : customers) {
            Fact r = sb.stated(Field.NODE_REGION, c.name());
            if (r != null) {
                byRegion.computeIfAbsent(r.value(), k -> new ArrayList<>()).add(c);
            }
        }
        List<Statement> out = new ArrayList<>();
        byRegion.forEach((region, members) -> {
            List<Integer> ids = members.stream().map(c -> sb.stated(Field.NODE_REGION, c.name()).id()).toList();
            Map<String, String> v = new LinkedHashMap<>();
            v.put("name", members.get(0).name());
            v.put("members", Templates.join(members.stream().map(Node::name).toList()));
            v.put("region", region);
            Narrative phrase = members.size() == 1 ? Narrative.CUSTOMER_REGION_ONE : Narrative.CUSTOMER_REGION_MANY;
            out.add(Statement.fact(Templates.fill(pool.draw(phrase), v), ids));
        });
        return out;
    }

    // ---- Logistics lanes --------------------------------------------------------------------

    private List<List<Statement>> lanes(Map<Integer, List<Node>> tiers) {
        List<Link> links = Describer.sortedLinks(truth, CanonicalCsvWriter.sortedNodes(truth.nodes()));
        Map<Link, List<Link>> groups = Describer.linkGroups(links, sb);
        List<List<Statement>> paragraphs = new ArrayList<>();
        for (Tier t : truth.tiers()) {
            List<Statement> para = new ArrayList<>();
            for (Node source : tiers.get(t.index())) {
                for (Link l : links) {
                    if (l.source().equals(source.name())) {
                        Describer.linkStatement(l, groups, sb, para);
                    }
                }
            }
            if (!para.isEmpty()) {
                paragraphs.add(para);
            }
        }
        return paragraphs;
    }

    // ---- Demand and inventory ---------------------------------------------------------------

    private List<List<Statement>> demandAndInventory(Map<Integer, List<Node>> tiers) {
        List<List<Statement>> paragraphs = new ArrayList<>();
        for (Tier t : truth.tiers()) {
            List<Statement> para = new ArrayList<>();
            if (t.type() == NodeType.CUSTOMER) {
                for (Product pr : truth.products()) {
                    para.addAll(demand(tiers.get(t.index()), pr.name()));
                }
            } else {
                for (Node n : tiers.get(t.index())) {
                    for (NodeProduct np : Describer.nodeProducts(truth, n.name())) {
                        para.add(inventory(n, np));
                    }
                }
            }
            if (!para.isEmpty()) {
                paragraphs.add(para);
            }
        }
        return paragraphs;
    }

    /** Customers' demand for one product, grouped when several demand the same quantity. */
    private List<Statement> demand(List<Node> customers, String product) {
        Map<String, List<NodeProduct>> byQuantity = new LinkedHashMap<>();
        for (Node c : customers) {
            for (NodeProduct np : Describer.nodeProducts(truth, c.name())) {
                if (np.product().equals(product)) {
                    Fact d = sb.stated(Field.NP_DEMAND, np.key());
                    byQuantity.computeIfAbsent(d.value() + " " + d.unit(), k -> new ArrayList<>()).add(np);
                }
            }
        }
        List<Statement> out = new ArrayList<>();
        byQuantity.forEach((q, members) -> {
            if (members.size() == 1) {
                out.add(sb.demand(members.get(0)));
                return;
            }
            List<Integer> ids = new ArrayList<>();
            for (NodeProduct np : members) {
                ids.add(sb.stated(Field.NP_EXISTS, np.key()).id());
                ids.add(sb.stated(Field.NP_DEMAND, np.key()).id());
            }
            Fact d = sb.stated(Field.NP_DEMAND, members.get(0).key());
            Map<String, String> v = new LinkedHashMap<>();
            v.put("members", Templates.join(members.stream().map(NodeProduct::node).toList()));
            v.put("value", sb.num(d.value()));
            v.put("product", product);
            v.put("unit", Templates.rateUnit(org.snrmbench.netbench.model.TimeUnit.valueOf(d.unit())));
            out.add(Statement.fact(Templates.fill(pool.draw(Narrative.DEMAND_GROUP), v), ids));
        });
        return out;
    }

    /** A supply-side node's inventory of one product: its absolute clauses, then any relative ones. */
    private Statement inventory(Node n, NodeProduct np) {
        List<Field> relativeFields = new ArrayList<>();
        for (Field field : StatementBuilder.INVENTORY_CLAUSES.keySet()) {
            Fact f = sb.stated(field, np.key());
            if (f != null) {
                String other = referent(n.tierIndex(), field, np.product(), f, n.name());
                if (other != null) {
                    relativeFields.add(field);
                    relativeTo.put(f.id(), other);
                }
            }
        }
        Chain chain = new Chain(n);
        List<Integer> ids = new ArrayList<>();
        ids.add(sb.stated(Field.NP_EXISTS, np.key()).id());
        List<String> clauses = sb.inventoryClauses(np, relativeFields, ids);
        chain.add(sb.phrase(Slot.INVENTORY, Map.of("name", n.name(), "clauses", Templates.join(clauses))), ids);
        for (Field field : relativeFields) {
            Fact f = sb.stated(field, np.key());
            chain.addRelative(Templates.fill(pool.draw(Narrative.RELATIVE_INVENTORY), Map.of("subject", chain.subject(),
                    "field", FIELD_WORDS.get(field), "product", np.product(), "other", relativeTo.get(f.id()))),
                    f.id());
            relative.add(f.id());
        }
        return chain.statement();
    }

    // ---- coreference chains -----------------------------------------------------------------

    /** The sentences about one node, as one statement unit. */
    private final class Chain {
        private final Node node;
        private final List<String> sentences = new ArrayList<>();
        private final List<Integer> ids = new ArrayList<>();
        private String noun;
        private boolean namedOther;

        Chain(Node node) {
            this.node = node;
        }

        /** The subject of the next sentence: the name first and after another node was named, else the noun. */
        String subject() {
            if (sentences.isEmpty() || namedOther) {
                return node.name();
            }
            if (noun == null) {
                noun = pool.draw(switch (node.type()) {
                    case SUPPLIER -> Narrative.COREF_SUPPLIER;
                    case PLANT -> Narrative.COREF_PLANT;
                    default -> Narrative.COREF_DC;
                });
            }
            return noun;
        }

        void add(String sentence, int id) {
            add(sentence, List.of(id));
        }

        void add(String sentence, List<Integer> factIds) {
            sentences.add(sentence);
            ids.addAll(factIds);
            namedOther = false;
        }

        /** A relative statement: it names another node, so the next sentence repeats this node's name. */
        void addRelative(String sentence, int id) {
            add(sentence, id);
            namedOther = true;
        }

        Statement statement() {
            return sentences.isEmpty() ? null : Statement.fact(String.join(" ", sentences), ids);
        }
    }
}
