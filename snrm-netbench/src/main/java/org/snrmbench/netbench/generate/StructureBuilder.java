package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.generate.Skeleton.SkLink;
import org.snrmbench.netbench.generate.Skeleton.SkNode;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.TierRole;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stage {@code structure}: tiers, node allocation and links. */
public final class StructureBuilder {

    private StructureBuilder() {
    }

    /** The tier roles fixed by the echelon count. */
    public static List<TierRole> roles(int echelons) {
        return switch (echelons) {
            case 3 -> List.of(TierRole.SUPPLIER, TierRole.PLANT, TierRole.CUSTOMER);
            case 4 -> List.of(TierRole.SUPPLIER, TierRole.PLANT, TierRole.DC, TierRole.CUSTOMER);
            case 5 -> List.of(TierRole.SUPPLIER, TierRole.PLANT, TierRole.DC_CENTRAL, TierRole.DC_REGIONAL,
                    TierRole.CUSTOMER);
            case 6 -> List.of(TierRole.SUPPLIER, TierRole.PLANT_COMPONENT, TierRole.PLANT_ASSEMBLY,
                    TierRole.DC_CENTRAL, TierRole.DC_REGIONAL, TierRole.CUSTOMER);
            case 7 -> List.of(TierRole.SUPPLIER_TIER2, TierRole.SUPPLIER_TIER1, TierRole.PLANT_COMPONENT,
                    TierRole.PLANT_ASSEMBLY, TierRole.DC_CENTRAL, TierRole.DC_REGIONAL, TierRole.CUSTOMER);
            default -> throw new IllegalArgumentException("echelons must be 3..7: " + echelons);
        };
    }

    /** Tier sizes: one node per tier, then of the remaining R = N − E the customers get ⌊R/2⌋ and the other ⌈R/2⌉ are shared equally by the E−1 upstream tiers, the remainder one at a time from tier 0. */
    public static int[] allocate(int nodes, int echelons) {
        int[] sizes = new int[echelons];
        java.util.Arrays.fill(sizes, 1);
        int r = nodes - echelons;
        sizes[echelons - 1] += r / 2;
        int upstream = r - r / 2;
        int share = upstream / (echelons - 1);
        int remainder = upstream % (echelons - 1);
        for (int t = 0; t < echelons - 1; t++) {
            sizes[t] += share + (t < remainder ? 1 : 0);
        }
        return sizes;
    }

    public static Skeleton build(GenerateParams p, Rng rng) {
        List<TierRole> roles = roles(p.echelons());
        int[] sizes = allocate(p.nodes(), p.echelons());
        List<SkNode> nodes = new ArrayList<>();
        List<List<Integer>> tiers = new ArrayList<>();
        for (int t = 0; t < roles.size(); t++) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < sizes[t]; i++) {
                ids.add(nodes.size());
                nodes.add(new SkNode(nodes.size(), t, i, roles.get(t)));
            }
            tiers.add(ids);
        }

        Set<Long> present = new LinkedHashSet<>();
        List<SkLink> links = new ArrayList<>();
        for (int t = 0; t + 1 < tiers.size(); t++) {
            List<Integer> parents = tiers.get(t);
            List<Integer> children = tiers.get(t + 1);
            // 1. coverage pass: every child gets a parent and every parent a child
            List<Integer> shuffled = new ArrayList<>(children);
            rng.shuffle(shuffled, Stage.STRUCTURE);
            for (int i = 0; i < Math.max(parents.size(), children.size()); i++) {
                add(links, present, parents.get(i % parents.size()), shuffled.get(i % shuffled.size()), false);
            }
            // 2. inbound links: each child draws k in [min, max] (capped at the tier size) parents
            for (int child : children) {
                int k = Math.min(rng.uniformInt(Stage.STRUCTURE, p.inboundMin(), p.inboundMax()), parents.size());
                List<Integer> candidates = new ArrayList<>();
                for (int parent : parents) {
                    if (!present.contains(key(parent, child))) {
                        candidates.add(parent);
                    }
                }
                while (parentCount(links, child) < k) {
                    int pick = candidates.remove(rng.stream(Stage.STRUCTURE).nextInt(candidates.size()));
                    add(links, present, pick, child, false);
                }
            }
        }

        // 3. lateral links between two DC nodes of one tier
        List<long[]> candidates = new ArrayList<>();
        for (int t = 0; t < tiers.size(); t++) {
            if (roles.get(t).type() != NodeType.DC) {
                continue;
            }
            for (int a : tiers.get(t)) {
                for (int b : tiers.get(t)) {
                    if (a != b) {
                        candidates.add(new long[] {a, b});
                    }
                }
            }
        }
        int added = 0;
        String note = null;
        if (p.lateralLinks() > 0 && candidates.isEmpty()) {
            note = "no tier has two or more DC nodes; --lateral-links " + p.lateralLinks() + " skipped";
        }
        while (added < p.lateralLinks() && !candidates.isEmpty()) {
            long[] c = candidates.get(rng.stream(Stage.STRUCTURE).nextInt(candidates.size()));
            int a = (int) c[0];
            int b = (int) c[1];
            add(links, present, a, b, true);
            candidates.removeIf(x -> (x[0] == a && x[1] == b) || (x[0] == b && x[1] == a));
            added++;
        }
        if (added < p.lateralLinks() && note == null) {
            note = "only " + added + " of " + p.lateralLinks() + " lateral links fit (every same-tier DC pair is used)";
        }

        links.sort(Comparator.comparingInt(SkLink::source).thenComparingInt(SkLink::target));
        Skeleton s = new Skeleton(roles, tiers, nodes, links, added, note);
        assertGuarantees(s);
        return s;
    }

    private static long key(int source, int target) {
        return ((long) source << 32) | target;
    }

    private static void add(List<SkLink> links, Set<Long> present, int source, int target, boolean lateral) {
        if (present.add(key(source, target))) {
            links.add(new SkLink(source, target, lateral));
        }
    }

    private static int parentCount(List<SkLink> links, int child) {
        int n = 0;
        for (SkLink l : links) {
            if (l.target() == child) {
                n++;
            }
        }
        return n;
    }

    /** The guarantees: no self-loops, no duplicate pairs, no upstream links, inbound links outside tier 0, outbound links outside the customer tier, every customer reachable from tier 0. */
    static void assertGuarantees(Skeleton s) {
        Set<Long> seen = new LinkedHashSet<>();
        int last = s.tiers().size() - 1;
        boolean[] in = new boolean[s.nodes().size()];
        boolean[] out = new boolean[s.nodes().size()];
        Graph<Integer, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
        s.nodes().forEach(n -> g.addVertex(n.id()));
        for (SkLink l : s.links()) {
            check(l.source() != l.target(), "self-loop at " + l.source());
            check(seen.add(key(l.source(), l.target())), "duplicate link " + l);
            int ts = s.node(l.source()).tier();
            int tt = s.node(l.target()).tier();
            check(tt == ts + 1 || (l.lateral() && tt == ts), "link " + l + " does not run one tier downstream");
            in[l.target()] = true;
            out[l.source()] = true;
            g.addEdge(l.source(), l.target());
        }
        for (SkNode n : s.nodes()) {
            check(n.tier() == 0 || in[n.id()], "node " + n.id() + " has no inbound link");
            check(n.tier() == last || out[n.id()], "node " + n.id() + " has no outbound link");
        }
        Set<Integer> reached = new LinkedHashSet<>();
        new BreadthFirstIterator<>(g, s.tiers().get(0)).forEachRemaining(reached::add);
        for (int c : s.tiers().get(last)) {
            check(reached.contains(c), "customer " + c + " is not reachable from tier 0");
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException("structure guarantee violated: " + message);
        }
    }
}
