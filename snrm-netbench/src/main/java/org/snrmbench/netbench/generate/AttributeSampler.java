package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.generate.Ranges.DecRange;
import org.snrmbench.netbench.generate.Ranges.IntRange;
import org.snrmbench.netbench.generate.Ranges.Lane;
import org.snrmbench.netbench.generate.Ranges.NodeRanges;
import org.snrmbench.netbench.generate.Skeleton.SkLink;
import org.snrmbench.netbench.generate.Skeleton.SkNode;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.Quantity;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.util.Numbers;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stage attributes: draws every attribute of the full network in a fixed order — regions, products, demand, capacities, lead times (then processing times), costs, probabilities, inventory — then re-expresses units. */
public final class AttributeSampler {

    private static final Map<String, double[]> CENTROIDS = centroids();

    private AttributeSampler() {
    }

    private static Map<String, double[]> centroids() {
        Map<String, double[]> m = new LinkedHashMap<>();
        m.put("EU-West", new double[] {50.0, 3.0});
        m.put("EU-North", new double[] {60.0, 16.0});
        m.put("EU-South", new double[] {41.0, 10.0});
        m.put("EU-East", new double[] {50.0, 23.0});
        m.put("EU-Central", new double[] {50.0, 11.0});
        m.put("APAC", new double[] {30.0, 115.0});
        m.put("AMER", new double[] {40.0, -90.0});
        return m;
    }

    /** Draws the full network (nothing blanked yet) for a skeleton and its names. */
    public static Network sample(Skeleton s, String[] names, GenerateParams p, Rng rng) {
        final Stage A = Stage.ATTRIBUTES;
        TimeUnit unit = p.periodUnit();
        BigDecimal pv = BigDecimal.valueOf(p.periodValue());
        int n = s.nodes().size();
        List<Integer> customers = s.tiers().get(s.tiers().size() - 1);

        // 1. regions
        String[] region = new String[n];
        boolean[] overseas = new boolean[n];
        for (SkNode node : s.nodes()) {
            if (node.type() == NodeType.SUPPLIER) {
                overseas[node.id()] = rng.bernoulli(A, Ranges.SUPPLIER_OVERSEAS_PROBABILITY);
            }
            region[node.id()] = rng.choice(A, overseas[node.id()] ? Ranges.OVERSEAS_REGIONS : Ranges.DOMESTIC_REGIONS);
        }

        // 2. products
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < p.products(); i++) {
            String name = p.products() == 1 ? p.finalProductName() : p.finalProductName() + "-" + letters(i);
            products.add(new Product(name, BigDecimal.valueOf(draw(rng, Ranges.UNIT_VALUE))));
        }

        // 3. demand, per customer and product (0 = the customer does not demand it)
        long[][] demand = new long[n][p.products()];
        for (int c : customers) {
            demand[c][0] = draw(rng, Ranges.DEMAND_PRIMARY);
        }
        for (int prod = 1; prod < p.products(); prod++) {
            int size = rng.uniformInt(A, (customers.size() + 1) / 2, customers.size());
            List<Integer> chosen = new ArrayList<>(rng.sample(A, customers, size));
            chosen.sort(Integer::compare);
            for (int c : chosen) {
                demand[c][prod] = draw(rng, Ranges.DEMAND_SECONDARY);
            }
        }
        long[][] downstream = downstreamDemand(s, demand, p.products());

        // 4. capacities: slack x downstream demand, rounded up to a multiple of 10
        BigDecimal[] nodeCap = new BigDecimal[n];
        for (SkNode node : s.nodes()) {
            if (node.type() != NodeType.CUSTOMER) {
                nodeCap[node.id()] = capacity(rng, total(downstream[node.id()]));
            }
        }
        List<SkLink> links = s.links();
        BigDecimal[] linkCap = new BigDecimal[links.size()];
        for (int i = 0; i < links.size(); i++) {
            linkCap[i] = capacity(rng, total(downstream[links.get(i).target()]));
        }

        // 5. lead times (links), then processing times (nodes), in periods
        Lane[] lane = new Lane[links.size()];
        int[] lead = new int[links.size()];
        for (int i = 0; i < links.size(); i++) {
            lane[i] = lane(s, links.get(i), overseas);
            lead[i] = lane[i] == Lane.SUPPLIER_PLANT_OVERSEAS
                    ? rng.choice(A, unit == TimeUnit.WEEK ? Ranges.OVERSEAS_LEAD_TIMES_WEEK : Ranges.OVERSEAS_LEAD_TIMES)
                    : draw(rng, lane[i].leadTime());
        }
        int[] processing = new int[n];
        for (SkNode node : s.nodes()) {
            NodeRanges r = Ranges.node(node.type());
            if (r != null && r.processingTime() != null) {
                processing[node.id()] = draw(rng, r.processingTime());
            }
        }

        // 6. costs
        BigDecimal[] fixed = new BigDecimal[n];
        BigDecimal[] var = new BigDecimal[n];
        for (SkNode node : s.nodes()) {
            NodeRanges r = Ranges.node(node.type());
            if (r != null) {
                fixed[node.id()] = rng.uniformStep(A, r.fixedCost().min(), r.fixedCost().max(), r.fixedCost().step());
                var[node.id()] = draw(rng, r.varCost());
            }
        }
        BigDecimal[] unitCost = new BigDecimal[links.size()];
        for (int i = 0; i < links.size(); i++) {
            unitCost[i] = draw(rng, lane[i].unitCost());
        }

        // 7. probabilities
        BigDecimal[] nodeFail = new BigDecimal[n];
        for (SkNode node : s.nodes()) {
            NodeRanges r = Ranges.node(node.type());
            if (r != null) {
                nodeFail[node.id()] = draw(rng, r.failureProb());
            }
        }
        BigDecimal[] linkFail = new BigDecimal[links.size()];
        for (int i = 0; i < links.size(); i++) {
            linkFail[i] = draw(rng, Ranges.LINK_FAILURE_PROB);
        }

        // 8. inventory, per supply-side node and product
        BigDecimal[][] initial = new BigDecimal[n][p.products()];
        BigDecimal[][] holding = new BigDecimal[n][p.products()];
        for (SkNode node : s.nodes()) {
            if (node.type() == NodeType.CUSTOMER) {
                continue;
            }
            for (int prod = 0; prod < p.products(); prod++) {
                int m = draw(rng, Ranges.INVENTORY_MULTIPLE);
                initial[node.id()][prod] = BigDecimal.valueOf(m * downstream[node.id()][prod]);
                holding[node.id()][prod] = draw(rng, Ranges.HOLDING_COST);
            }
        }

        // assemble quantities in the period unit
        Quantity[] nodeCapQ = new Quantity[n];
        Quantity[] processingQ = new Quantity[n];
        for (SkNode node : s.nodes()) {
            nodeCapQ[node.id()] = new Quantity(nodeCap[node.id()], unit);
            processingQ[node.id()] = new Quantity(pv.multiply(BigDecimal.valueOf(processing[node.id()])), unit);
        }
        Quantity[] leadQ = new Quantity[links.size()];
        Quantity[] linkCapQ = new Quantity[links.size()];
        for (int i = 0; i < links.size(); i++) {
            leadQ[i] = new Quantity(pv.multiply(BigDecimal.valueOf(lead[i])), unit);
            linkCapQ[i] = new Quantity(linkCap[i], unit);
        }
        Quantity[][] demandQ = new Quantity[n][p.products()];
        Quantity[][] holdingQ = new Quantity[n][p.products()];
        for (SkNode node : s.nodes()) {
            for (int prod = 0; prod < p.products(); prod++) {
                boolean customer = node.type() == NodeType.CUSTOMER;
                demandQ[node.id()][prod] = Quantity.of(customer ? demand[node.id()][prod] : 0, unit);
                holdingQ[node.id()][prod] = customer ? Quantity.of(0, unit) : new Quantity(holding[node.id()][prod], unit);
            }
        }

        // 9. --units mixed: re-express some quantities where the conversion is exact
        if (p.units() == GenerateParams.Units.MIXED) {
            mixUnits(s, p, rng, unit, nodeCapQ, demandQ, holdingQ, leadQ);
        }

        // 10. geo, last, so that --geo changes nothing else
        BigDecimal[] lat = new BigDecimal[n];
        BigDecimal[] lng = new BigDecimal[n];
        if (p.geo()) {
            for (SkNode node : s.nodes()) {
                double[] c = CENTROIDS.get(region[node.id()]);
                lat[node.id()] = BigDecimal.valueOf(c[0]).setScale(2).add(draw(rng, Ranges.GEO_JITTER));
                lng[node.id()] = BigDecimal.valueOf(c[1]).setScale(2).add(draw(rng, Ranges.GEO_JITTER));
            }
        }

        // assemble the network
        List<Tier> tiers = new ArrayList<>();
        for (int t = 0; t < s.tiers().size(); t++) {
            tiers.add(new Tier(t, s.tierRoles().get(t), s.tierRoles().get(t).type(),
                    s.tiers().get(t).stream().map(id -> names[id]).toList()));
        }
        List<Node> nodes = new ArrayList<>();
        for (SkNode node : s.nodes()) {
            int id = node.id();
            boolean customer = node.type() == NodeType.CUSTOMER;
            nodes.add(new Node(names[id], node.type(), node.tier(), nodeCapQ[id], processingQ[id],
                    customer ? BigDecimal.ZERO : fixed[id], customer ? BigDecimal.ZERO : var[id],
                    customer ? BigDecimal.ZERO : nodeFail[id], region[id], lat[id], lng[id]));
        }
        List<Link> modelLinks = new ArrayList<>();
        for (int i = 0; i < links.size(); i++) {
            SkLink l = links.get(i);
            modelLinks.add(new Link(names[l.source()], names[l.target()], leadQ[i], linkCapQ[i], unitCost[i],
                    linkFail[i]));
        }
        List<NodeProduct> nps = new ArrayList<>();
        for (SkNode node : s.nodes()) {
            int id = node.id();
            for (int prod = 0; prod < p.products(); prod++) {
                String product = products.get(prod).name();
                if (node.type() == NodeType.CUSTOMER) {
                    if (demand[id][prod] > 0) {
                        nps.add(new NodeProduct(names[id], product, demandQ[id][prod], BigDecimal.ZERO,
                                BigDecimal.ZERO, holdingQ[id][prod]));
                    }
                } else {
                    BigDecimal ii = initial[id][prod];
                    BigDecimal ss = ii.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
                    nps.add(new NodeProduct(names[id], product, demandQ[id][prod], ii, ss, holdingQ[id][prod]));
                }
            }
        }
        TimeBase tb = new TimeBase(pv, unit, p.horizon(), RoundingPolicy.NEAREST,
                p.finalProductName() + " supply network");
        return new Network(tb, tiers, products, nodes, modelLinks, nps);
    }

    /** Per node and product: the total demand of the customers reachable downstream (a customer reaches itself). */
    static long[][] downstreamDemand(Skeleton s, long[][] demand, int productCount) {
        int n = s.nodes().size();
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
        }
        for (SkLink l : s.links()) {
            out.get(l.source()).add(l.target());
        }
        long[][] result = new long[n][productCount];
        for (int start = 0; start < n; start++) {
            boolean[] seen = new boolean[n];
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            seen[start] = true;
            while (!queue.isEmpty()) {
                int v = queue.poll();
                if (s.node(v).type() == NodeType.CUSTOMER) {
                    for (int prod = 0; prod < productCount; prod++) {
                        result[start][prod] += demand[v][prod];
                    }
                }
                for (int w : out.get(v)) {
                    if (!seen[w]) {
                        seen[w] = true;
                        queue.add(w);
                    }
                }
            }
        }
        return result;
    }

    private static void mixUnits(Skeleton s, GenerateParams p, Rng rng, TimeUnit unit, Quantity[] nodeCapQ,
                                 Quantity[][] demandQ, Quantity[][] holdingQ, Quantity[] leadQ) {
        final Stage A = Stage.ATTRIBUTES;
        if (unit != TimeUnit.WEEK) {
            for (SkNode node : s.nodes()) {
                if (node.type() == NodeType.SUPPLIER && rng.bernoulli(A, Ranges.MIXED_SUPPLIER_CAPACITY)) {
                    nodeCapQ[node.id()] = rate(nodeCapQ[node.id()], TimeUnit.WEEK, 0);
                }
            }
            for (SkNode node : s.nodes()) {
                if (node.type() != NodeType.CUSTOMER) {
                    continue;
                }
                for (int prod = 0; prod < p.products(); prod++) {
                    Quantity q = demandQ[node.id()][prod];
                    if (q.value().signum() > 0 && rng.bernoulli(A, Ranges.MIXED_CUSTOMER_DEMAND)) {
                        demandQ[node.id()][prod] = rate(q, TimeUnit.WEEK, 0);
                    }
                }
            }
            for (SkNode node : s.nodes()) {
                if (node.type() == NodeType.CUSTOMER) {
                    continue;
                }
                for (int prod = 0; prod < p.products(); prod++) {
                    if (rng.bernoulli(A, Ranges.MIXED_HOLDING_COST)) {
                        holdingQ[node.id()][prod] = rate(holdingQ[node.id()][prod], TimeUnit.WEEK, 2);
                    }
                }
            }
        }
        boolean[] done = new boolean[leadQ.length];
        if (unit != TimeUnit.WEEK) {
            for (int i = 0; i < leadQ.length; i++) {
                if (rng.bernoulli(A, Ranges.MIXED_LEAD_TIME_WEEK)) {
                    Quantity q = duration(leadQ[i], TimeUnit.WEEK);
                    done[i] = q != leadQ[i];
                    leadQ[i] = q;
                }
            }
        }
        TimeUnit finer = switch (unit) {
            case DAY -> TimeUnit.HOUR;
            case WEEK -> TimeUnit.DAY;
            default -> null;
        };
        if (finer != null) {
            for (int i = 0; i < leadQ.length; i++) {
                if (!done[i] && rng.bernoulli(A, Ranges.MIXED_LEAD_TIME_FINER)) {
                    leadQ[i] = duration(leadQ[i], finer);
                }
            }
        }
    }

    /** A rate in another denominator, when the result has at most {@code scale} decimals; else unchanged. */
    static Quantity rate(Quantity q, TimeUnit target, int scale) {
        return exact(q.value().multiply(BigDecimal.valueOf(target.seconds())),
                BigDecimal.valueOf(q.unit().seconds()), scale).map(v -> new Quantity(v, target)).orElse(q);
    }

    /** A duration in another unit, when the result is a whole number; else unchanged (the same instance). */
    static Quantity duration(Quantity q, TimeUnit target) {
        return exact(q.value().multiply(BigDecimal.valueOf(q.unit().seconds())),
                BigDecimal.valueOf(target.seconds()), 0).map(v -> new Quantity(v, target)).orElse(q);
    }

    private static java.util.Optional<BigDecimal> exact(BigDecimal numerator, BigDecimal denominator, int scale) {
        try {
            BigDecimal v = numerator.divide(denominator);
            if (v.stripTrailingZeros().scale() > scale) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(v.setScale(scale, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException nonTerminating) {
            return java.util.Optional.empty();
        }
    }

    private static Lane lane(Skeleton s, SkLink l, boolean[] overseas) {
        NodeType a = s.node(l.source()).type();
        NodeType b = s.node(l.target()).type();
        return switch (a) {
            case SUPPLIER -> b == NodeType.SUPPLIER ? Lane.SUPPLIER_SUPPLIER
                    : overseas[l.source()] ? Lane.SUPPLIER_PLANT_OVERSEAS : Lane.SUPPLIER_PLANT_DOMESTIC;
            case PLANT -> b == NodeType.PLANT ? Lane.PLANT_PLANT : Lane.PLANT_DOWNSTREAM;
            case DC -> b == NodeType.DC ? Lane.DC_DC : Lane.DC_CUSTOMER;
            case CUSTOMER -> throw new IllegalStateException("link out of a customer");
        };
    }

    private static BigDecimal capacity(Rng rng, long downstreamDemand) {
        BigDecimal slack = BigDecimal.valueOf(draw(rng, Ranges.SLACK_THOUSANDTHS), 3);
        return Numbers.ceilToMultipleOf10(slack.multiply(BigDecimal.valueOf(downstreamDemand)));
    }

    private static long total(long[] perProduct) {
        return Arrays.stream(perProduct).sum();
    }

    private static int draw(Rng rng, IntRange r) {
        return rng.uniformInt(Stage.ATTRIBUTES, r.min(), r.max());
    }

    private static BigDecimal draw(Rng rng, DecRange r) {
        return rng.uniformDecimal(Stage.ATTRIBUTES, r.min(), r.max(), 2);
    }

    /** A, B, …, Z, AA, AB, … */
    static String letters(int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = index; i >= 0; i = i / 26 - 1) {
            sb.insert(0, (char) ('A' + i % 26));
        }
        return sb.toString();
    }
}
