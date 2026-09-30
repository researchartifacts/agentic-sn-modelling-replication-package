package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.generate.Skeleton.SkNode;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stage {@code naming}: node names by scheme, indexed by canonical node id. */
public final class Namer {

    private Namer() {
    }

    /** Typed-name prefixes per tier, fixed by the echelon count. */
    public static List<String> typedPrefixes(int echelons) {
        return switch (echelons) {
            case 3 -> List.of("SUP", "PLT", "CUS");
            case 4 -> List.of("SUP", "PLT", "DC", "CUS");
            case 5 -> List.of("SUP", "PLT", "DCC", "DCR", "CUS");
            case 6 -> List.of("SUP", "PLC", "PLA", "DCC", "DCR", "CUS");
            case 7 -> List.of("SP2", "SP1", "PLC", "PLA", "DCC", "DCR", "CUS");
            default -> throw new IllegalArgumentException("echelons must be 3..7: " + echelons);
        };
    }

    /** Names by canonical node id. */
    public static String[] assign(Skeleton s, String[] regions, GenerateParams p, Rng rng) {
        return switch (p.naming()) {
            case TYPED -> typed(s);
            case NEUTRAL -> neutral(s, rng);
            case REALISTIC -> realistic(s, regions, rng);
        };
    }

    /** {@code PREFIX-NN}: 2 digits, 3 when the tier has 100 or more nodes; 1-based within the tier. */
    static String[] typed(Skeleton s) {
        List<String> prefixes = typedPrefixes(s.tierRoles().size());
        String[] names = new String[s.nodes().size()];
        for (SkNode n : s.nodes()) {
            int width = s.tiers().get(n.tier()).size() >= 100 ? 3 : 2;
            names[n.id()] = prefixes.get(n.tier()) + "-" + pad(n.position() + 1, width);
        }
        return names;
    }

    /** {@code N01…Nnn} assigned to the nodes in a shuffled order, so the index reveals nothing. */
    static String[] neutral(Skeleton s, Rng rng) {
        int n = s.nodes().size();
        int width = Math.max(2, Integer.toString(n).length());
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        rng.shuffle(order, Stage.NAMING);
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            names[order.get(i)] = "N" + pad(i + 1, width);
        }
        return names;
    }

    /** Suppliers take company names; every other node takes the name of a city in its own region (so a plant in EU-North is a Nordic city). */
    static String[] realistic(Skeleton s, String[] regions, Rng rng) {
        List<String> companies = new ArrayList<>(resourceLines("/names/companies.txt"));
        rng.shuffle(companies, Stage.NAMING);
        Map<String, List<String>> cities = new LinkedHashMap<>();
        citiesByRegion().forEach((region, list) -> {
            List<String> shuffled = new ArrayList<>(list);
            rng.shuffle(shuffled, Stage.NAMING);
            cities.put(region, shuffled);
        });
        String[] names = new String[s.nodes().size()];
        int nextCompany = 0;
        Map<String, Integer> nextCity = new LinkedHashMap<>();
        for (SkNode n : s.nodes()) {
            if (n.type() == NodeType.SUPPLIER) {
                names[n.id()] = cycle(companies, nextCompany++);
            } else {
                List<String> list = cities.get(regions[n.id()]);
                if (list == null || list.isEmpty()) {
                    throw new IllegalStateException("no city list for region " + regions[n.id()]);
                }
                int i = nextCity.merge(regions[n.id()], 1, Integer::sum) - 1;
                names[n.id()] = cycle(list, i);
            }
        }
        return names;
    }

    /** {@code names/cities.txt} by its {@code # region: …} sections, in file order. */
    public static Map<String, List<String>> citiesByRegion() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        String region = null;
        for (String line : rawLines("/names/cities.txt")) {
            String t = line.trim();
            if (t.startsWith("# region:")) {
                region = t.substring("# region:".length()).trim();
                out.putIfAbsent(region, new ArrayList<>());
            } else if (!t.isEmpty() && !t.startsWith("#")) {
                if (region == null) {
                    throw new IllegalStateException("/names/cities.txt: " + t + " precedes every region section");
                }
                out.get(region).add(t);
            }
        }
        return out;
    }

    private static String cycle(List<String> list, int i) {
        int round = i / list.size();
        String base = list.get(i % list.size());
        return round == 0 ? base : base + " " + roman(round + 1);
    }

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_DIGITS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV",
            "I"};

    /** A positive integer as a Roman numeral (2 → II, 14 → XIV); reused names count up without limit. */
    public static String roman(int n) {
        StringBuilder sb = new StringBuilder();
        int rest = n;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (rest >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_DIGITS[i]);
                rest -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    /** The network with every node renamed from {@code from[i]} to {@code to[i]}. */
    public static Network rename(Network n, String[] from, String[] to) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < from.length; i++) {
            map.put(from[i], to[i]);
        }
        return new Network(n.timeBase(),
                n.tiers().stream().map(t -> new Tier(t.index(), t.role(), t.type(),
                        t.nodeNames().stream().map(map::get).toList())).toList(),
                n.products(),
                n.nodes().stream().map(x -> x.withName(map.get(x.name()))).toList(),
                n.links().stream().map(l -> new Link(map.get(l.source()), map.get(l.target()), l.leadTime(),
                        l.capacity(), l.unitCost(), l.failureProb())).toList(),
                n.nodeProducts().stream().map(np -> new NodeProduct(map.get(np.node()), np.product(), np.demand(),
                        np.initialInventory(), np.safetyStock(), np.holdingCost())).toList());
    }

    private static String pad(int value, int width) {
        String s = Integer.toString(value);
        return "0".repeat(Math.max(0, width - s.length())) + s;
    }

    /** Non-blank, non-comment lines of a bundled word list, checked for uniqueness after normalisation. */
    public static List<String> resourceLines(String path) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : rawLines(path)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (!seen.add(t.toLowerCase(Locale.ROOT).replaceAll("\\s+", " "))) {
                throw new IllegalStateException(path + ": duplicate entry " + t);
            }
            out.add(t);
        }
        return out;
    }

    private static List<String> rawLines(String path) {
        try (InputStream in = Namer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + path);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines().toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
