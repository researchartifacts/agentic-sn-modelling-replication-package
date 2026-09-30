package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.csv.CanonicalCsvWriter;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.FactLedger;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Ranges;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.model.TierRole;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.util.Numbers;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders A: the title, the header paragraph, the fact statements in --order, and the noise; then numbers the sentences and records each stated fact's sentence in the ledger. */
public final class Describer {

    private Describer() {
    }

    public static Description describe(Network truth, List<Fact> facts, GenerateParams p, Rng rng) {
        NumberStyle numbers = NumberStyle.of(p.locale());
        Map<String, StatementBuilder.Distance> distances =
                p.distanceMode() == GenerateParams.DistanceMode.KM ? distances(truth, facts) : Map.of();
        List<Statement> header = header(truth, new StatementBuilder(facts, Slot::template, numbers, distances),
                numbers, !distances.isEmpty());
        ParaphrasePool pool = new ParaphrasePool(rng);
        List<Section> body;
        List<Fact> ledger = facts;
        switch (p.style()) {
            case TEMPLATE -> body = order(truth, new StatementBuilder(facts, Slot::template, numbers, distances), p,
                    rng, false);
            case PARAPHRASED -> body = order(truth, new StatementBuilder(facts, pool::draw, numbers, distances), p,
                    rng, true);
            case NARRATIVE -> {
                NarrativeRenderer.Body narrative = NarrativeRenderer.render(truth,
                        new StatementBuilder(facts, pool::draw, numbers, distances), pool, p, rng);
                body = narrative.sections();
                ledger = facts.stream().map(f -> narrative.relative().contains(f.id()) ? f.withRelative(true) : f)
                        .toList();
            }
            default -> throw new IllegalStateException(p.style().cli());
        }
        NoiseInjector.Result noisy = NoiseInjector.inject(body, truth, ledger, p, rng);
        return number(p.finalProductName(), header, noisy.sections(), ledger, noisy.decoys(), noisy.decoyEntities(),
                p.style() == GenerateParams.Style.NARRATIVE);
    }

    // ---- header -----------------------------------------------------------------------------

    /** The header sentences: name; echelon and node counts; time base; products; and, in distance mode km, the lane speeds. */
    static List<Statement> header(Network truth, StatementBuilder sb, NumberStyle numbers, boolean speeds) {
        String meta = Fact.META_ENTITY;
        List<Statement> out = new ArrayList<>();
        Fact name = sb.stated(Field.META_NAME, meta);
        out.add(Statement.fact(Templates.fill(Templates.HEADER_NAME, Map.of("network", name.value())),
                List.of(name.id())));

        List<String> tierCounts = new ArrayList<>();
        for (Tier t : truth.tiers()) {
            int k = t.nodeNames().size();
            tierCounts.add(Templates.fill(Templates.TIER_COUNT, Map.of("count", sb.num(Integer.toString(k)),
                    "role", t.role().word(k))));
        }
        out.add(Statement.fact(Templates.fill(Templates.HEADER_COUNTS, Map.of(
                "echelons", sb.num(Integer.toString(truth.tiers().size())),
                "nodes", sb.num(Integer.toString(truth.nodes().size())),
                "tiers", Templates.join(tierCounts))), List.of()));

        Fact period = sb.stated(Field.META_PERIOD_LENGTH, meta);
        Fact horizon = sb.stated(Field.META_HORIZON, meta);
        Fact policy = sb.stated(Field.META_ROUNDING, meta);
        out.add(Statement.fact(Templates.fill(Templates.HEADER_TIME, Map.of(
                "periodValue", sb.num(period.value()),
                "periodUnit", Templates.durationUnit(new BigDecimal(period.value()), TimeUnit.valueOf(period.unit())),
                "horizon", sb.num(horizon.value()),
                "policy", RoundingPolicy.valueOf(policy.value()).phrase())),
                List.of(period.id(), horizon.id(), policy.id())));

        List<String> items = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (Product pr : truth.products()) {
            Fact exists = sb.stated(Field.PRODUCT_EXISTS, pr.name());
            Fact uv = sb.stated(Field.PRODUCT_UNIT_VALUE, pr.name());
            items.add(Templates.fill(Templates.PRODUCT_ITEM, Map.of("product", pr.name(),
                    "unitValue", sb.num(uv.value()))));
            ids.add(exists.id());
            ids.add(uv.id());
        }
        int count = truth.products().size();
        out.add(Statement.fact(Templates.fill(Templates.HEADER_PRODUCTS, Map.of(
                "count", sb.num(Integer.toString(count)),
                "productWord", count == 1 ? "product" : "products",
                "products", String.join(Templates.PRODUCT_SEPARATOR, items))), ids));
        if (numbers.headerSentence() != null) {
            out.add(Statement.fact(numbers.headerSentence(), List.of()));
        }
        if (speeds) {
            out.add(Statement.fact(Templates.fill(Templates.HEADER_SPEEDS, Map.of(
                    "road", sb.num(Integer.toString(Ranges.ROAD_KM_PER_DAY)),
                    "sea", sb.num(Integer.toString(Ranges.OCEAN_KM_PER_DAY)))), List.of()));
        }
        return out;
    }

    /** Distance mode km: every lane's mode and length. */
    public static Map<String, StatementBuilder.Distance> distances(Network truth, List<Fact> facts) {
        Map<String, Fact> ledger = FactLedger.index(facts);
        Map<String, StatementBuilder.Distance> out = new LinkedHashMap<>();
        for (Link l : truth.links()) {
            Node source = truth.node(l.source()).orElseThrow();
            Node target = truth.node(l.target()).orElseThrow();
            Fact region = ledger.get(FactLedger.key(Field.NODE_REGION.sheet().factKind(), l.source(),
                    Field.NODE_REGION.stem()));
            boolean ocean = source.type() == NodeType.SUPPLIER && target.type() == NodeType.PLANT
                    && region != null && Ranges.OVERSEAS_REGIONS.contains(region.value());
            int speed = ocean ? Ranges.OCEAN_KM_PER_DAY : Ranges.ROAD_KM_PER_DAY;
            BigDecimal seconds = l.leadTime().value().multiply(BigDecimal.valueOf(l.leadTime().unit().seconds()));
            BigDecimal km = seconds.multiply(BigDecimal.valueOf(speed))
                    .divide(BigDecimal.valueOf(TimeUnit.DAY.seconds()), java.math.MathContext.DECIMAL128);
            if (!Numbers.isInteger(km)) {
                throw new IllegalStateException("lane " + l.key() + " is not a whole number of km: " + km);
            }
            out.put(l.key(), new StatementBuilder.Distance(ocean ? Templates.MODE_OCEAN : Templates.MODE_ROAD,
                    Numbers.plain(km)));
        }
        return out;
    }

    // ---- order ------------------------------------------------------------------------------

    /** A node's own statements: the node sentence, its geo sentence, then its demand or inventory. */
    static List<Statement> nodeBlock(Network truth, Node n, StatementBuilder sb) {
        List<Statement> out = new ArrayList<>();
        TierRole role = truth.tiers().get(n.tierIndex()).role();
        out.add(sb.node(n, role));
        Statement geo = sb.geo(n);
        if (geo != null) {
            out.add(geo);
        }
        for (NodeProduct np : nodeProducts(truth, n.name())) {
            out.add(n.type() == NodeType.CUSTOMER ? sb.demand(np) : sb.inventory(np));
        }
        return out;
    }

    /** by-tier: per tier, the node blocks in name order, then every link grouped by source tier; by-node: per node, its block and its outbound links. */
    static List<Section> order(Network truth, StatementBuilder sb, GenerateParams p, Rng rng, boolean mergeLinks) {
        List<Node> nodes = CanonicalCsvWriter.sortedNodes(truth.nodes());
        List<Link> links = sortedLinks(truth, nodes);
        Map<Link, List<Link>> groups = mergeLinks ? linkGroups(links, sb) : Map.of();
        List<List<Statement>> paragraphs = new ArrayList<>();
        switch (p.order()) {
            case BY_TIER, SHUFFLED -> {
                for (Tier t : truth.tiers()) {
                    List<Statement> para = new ArrayList<>();
                    nodes.stream().filter(n -> n.tierIndex() == t.index())
                            .forEach(n -> para.addAll(nodeBlock(truth, n, sb)));
                    paragraphs.add(para);
                }
                for (Tier t : truth.tiers()) {
                    List<Statement> para = new ArrayList<>();
                    for (Link l : links) {
                        if (tierOf(truth, l.source()) == t.index()) {
                            linkStatement(l, groups, sb, para);
                        }
                    }
                    if (!para.isEmpty()) {
                        paragraphs.add(para);
                    }
                }
                if (p.order() == GenerateParams.Order.SHUFFLED) {
                    List<Statement> all = new ArrayList<>();
                    paragraphs.forEach(all::addAll);
                    rng.shuffle(all, Stage.STYLE);
                    paragraphs = new ArrayList<>(List.of(all));
                }
            }
            case BY_NODE -> {
                for (Node n : nodes) {
                    List<Statement> para = new ArrayList<>(nodeBlock(truth, n, sb));
                    for (Link l : links) {
                        if (l.source().equals(n.name())) {
                            linkStatement(l, groups, sb, para);
                        }
                    }
                    paragraphs.add(para);
                }
            }
        }
        return List.of(new Section(null, paragraphs));
    }

    /** Appends a link's statement: on its own, or — for the first link of a group of two or more with identical attributes from one source — the group statement; later group members add nothing. */
    static void linkStatement(Link l, Map<Link, List<Link>> groups, StatementBuilder sb, List<Statement> out) {
        List<Link> group = groups.get(l);
        if (group == null || group.size() < 2) {
            out.add(sb.link(l));
        } else if (group.get(0).equals(l)) {
            out.add(sb.linkGroup(group));
        }
    }

    /** Each link's group: the links of the same source with the same attribute signature, in B order. */
    static Map<Link, List<Link>> linkGroups(List<Link> links, StatementBuilder sb) {
        Map<String, List<Link>> bySignature = new LinkedHashMap<>();
        for (Link l : links) {
            bySignature.computeIfAbsent(l.source() + "|" + sb.linkSignature(l), k -> new ArrayList<>()).add(l);
        }
        Map<Link, List<Link>> out = new LinkedHashMap<>();
        bySignature.values().forEach(g -> g.forEach(l -> out.put(l, g)));
        return out;
    }

    static List<NodeProduct> nodeProducts(Network truth, String node) {
        return truth.nodeProducts().stream().filter(np -> np.node().equals(node)).toList();
    }

    /** Links in B's row order: by source order, then target order. */
    static List<Link> sortedLinks(Network truth, List<Node> sortedNodes) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < sortedNodes.size(); i++) {
            order.put(sortedNodes.get(i).name(), i);
        }
        return truth.links().stream()
                .sorted(java.util.Comparator.comparingInt((Link l) -> order.get(l.source()))
                        .thenComparingInt(l -> order.get(l.target())))
                .toList();
    }

    private static int tierOf(Network truth, String node) {
        return truth.node(node).orElseThrow().tierIndex();
    }

    // ---- numbering and rendering ------------------------------------------------------------

    /** Numbers every statement (header first, 1-based), stamps each stated fact with its sentence — failing loudly unless every stated fact is stated exactly once — and renders the Markdown. */
    static Description number(String titleProduct, List<Statement> header, List<Section> body, List<Fact> facts,
                              List<Decoy> decoys, List<String> decoyEntities, boolean flowing) {
        Map<Integer, Integer> sentenceOf = new LinkedHashMap<>();
        Map<Integer, Integer> decoySentence = new LinkedHashMap<>();
        int index = 0;
        int factSentences = 0;
        int noiseSentences = 0;
        List<Statement> all = new ArrayList<>(header);
        body.forEach(sec -> sec.paragraphs().forEach(all::addAll));
        for (Statement s : all) {
            index++;
            for (int id : s.factIds()) {
                if (sentenceOf.put(id, index) != null) {
                    throw new IllegalStateException("fact " + id + " is stated twice (sentence " + index + ")");
                }
            }
            if (s.decoyId() != null) {
                decoySentence.put(s.decoyId(), index);
            }
            if (index > header.size()) {
                if (s.kind() == Statement.Kind.FACT) {
                    factSentences++;
                } else {
                    noiseSentences++;
                }
            }
        }
        List<Fact> stamped = new ArrayList<>();
        for (Fact f : facts) {
            Integer sentence = sentenceOf.get(f.id());
            if (f.stated() && sentence == null) {
                throw new IllegalStateException("stated fact " + FactLedger.key(f) + " appears in no sentence");
            }
            if (!f.stated() && sentence != null) {
                throw new IllegalStateException("unstated fact " + FactLedger.key(f) + " appears in sentence "
                        + sentence);
            }
            stamped.add(f.withSentence(sentence));
        }
        List<Decoy> stampedDecoys = decoys.stream()
                .map(d -> new Decoy(d.id(), d.marker(), d.kind(), d.entity(), d.field(), d.value(), d.unit(),
                        d.decoyEntity(), decoySentence.get(d.id())))
                .toList();

        List<String> blocks = new ArrayList<>();
        blocks.add(Templates.fill(Templates.TITLE, Map.of("product", titleProduct)));
        blocks.add(String.join(" ", header.stream().map(Statement::text).toList()));
        for (Section sec : body) {
            if (sec.heading() != null) {
                blocks.add("## " + sec.heading());
            }
            for (List<Statement> para : sec.paragraphs()) {
                if (!para.isEmpty()) {
                    blocks.add(String.join(flowing ? " " : "\n", para.stream().map(Statement::text).toList()));
                }
            }
        }
        String markdown = String.join("\n\n", blocks) + "\n";
        return new Description(markdown, stamped, stampedDecoys, header.size(), factSentences, noiseSentences,
                decoyEntities, all.stream().map(Statement::text).toList());
    }
}
