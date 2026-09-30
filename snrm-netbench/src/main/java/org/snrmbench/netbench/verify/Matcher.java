package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Entity matching: nodes and products on the normalised name, links on the normalised ordered pair, node-product rows on the normalised pair. */
public final class Matcher {

    /** A truth entity and the candidate entity matched to it (null when missing). */
    public record Pair(Entity truth, Entity candidate, boolean reversed) {
        public boolean matched() {
            return candidate != null && !reversed;
        }
    }

    /** The matching of one sheet. */
    public record SheetMatch(Sheet sheet, List<Entity> truth, List<Entity> candidate, List<Pair> pairs,
                             List<Entity> extra, List<Entity> fabricated) {
        public int matched() {
            return (int) pairs.stream().filter(Pair::matched).count();
        }
    }

    private Matcher() {
    }

    /** The key columns of a sheet (none for network_meta). */
    public static List<Column> keyColumns(Sheet sheet) {
        return switch (sheet) {
            case NETWORK_META -> List.of();
            case NODES -> List.of(Schema.NODE_NAME);
            case LINKS -> List.of(Schema.LINK_SOURCE, Schema.LINK_TARGET);
            case PRODUCTS -> List.of(Schema.PRODUCT_NAME);
            case NODE_PRODUCTS -> List.of(Schema.NP_NODE, Schema.NP_PRODUCT);
        };
    }

    /** The entity key as the ledger and reports write it: a name, {@code source->target}, {@code node|product}. */
    public static String key(Sheet sheet, List<String> parts) {
        return switch (sheet) {
            case NETWORK_META -> Fact.META_ENTITY;
            case LINKS -> parts.get(0) + "->" + parts.get(1);
            case NODE_PRODUCTS -> parts.get(0) + "|" + parts.get(1);
            default -> parts.get(0);
        };
    }

    /** The normalised key of a ledger or decoy entity key; empty when it does not split into the sheet's parts. */
    public static Optional<List<String>> parseKey(Sheet sheet, String entity) {
        if (entity == null) {
            return Optional.empty();
        }
        List<String> parts = switch (sheet) {
            case NETWORK_META -> List.of();
            case LINKS -> List.of(entity.split("->", -1));
            case NODE_PRODUCTS -> List.of(entity.split("\\|", 2)); // a product name may contain '|'
            default -> List.of(entity);
        };
        if (parts.size() != keyColumns(sheet).size()) {
            return Optional.empty();
        }
        return Optional.of(parts.stream().map(Normaliser::name).toList());
    }

    /** Every entity of a sheet, in row order. */
    public static List<Entity> entities(RawNetwork net, Sheet sheet) {
        Optional<RawTable> t = net.table(sheet);
        if (t.isEmpty()) {
            return List.of();
        }
        RawTable table = t.get();
        if (sheet == Sheet.NETWORK_META) {
            return table.rows().isEmpty() ? List.of()
                    : List.of(new Entity(sheet, Fact.META_ENTITY, List.of(), table, table.rows().get(0)));
        }
        Map<List<String>, Entity> out = new LinkedHashMap<>();
        for (RawTable.Row r : table.rows()) {
            List<String> raw = new ArrayList<>();
            for (Column c : keyColumns(sheet)) {
                String v = table.cell(r, c);
                raw.add(v == null ? "" : v);
            }
            List<String> norm = raw.stream().map(Normaliser::name).toList();
            if (norm.stream().noneMatch(String::isEmpty)) {
                out.putIfAbsent(norm, new Entity(sheet, key(sheet, raw), norm, table, r));
            }
        }
        return List.copyOf(out.values());
    }

    /** Matches one sheet. */
    public static SheetMatch match(Sheet sheet, List<Entity> truth, List<Entity> candidate) {
        Map<List<String>, Entity> byKey = new LinkedHashMap<>();
        candidate.forEach(c -> byKey.put(c.norm(), c));
        Set<List<String>> truthKeys = new LinkedHashSet<>();
        truth.forEach(t -> truthKeys.add(t.norm()));
        Set<List<String>> used = new LinkedHashSet<>();
        List<Pair> pairs = new ArrayList<>();
        for (Entity t : truth) {
            Entity c = byKey.get(t.norm());
            if (c != null) {
                used.add(c.norm());
                pairs.add(new Pair(t, c, false));
            } else {
                Entity reversed = sheet == Sheet.LINKS ? reversedMatch(t, byKey, truthKeys) : null;
                if (reversed != null) {
                    used.add(reversed.norm());
                }
                pairs.add(new Pair(t, reversed, reversed != null));
            }
        }
        List<Entity> extra = candidate.stream().filter(c -> !truthKeys.contains(c.norm())).toList();
        List<Entity> fabricated = candidate.stream().filter(c -> !used.contains(c.norm())).toList();
        return new SheetMatch(sheet, truth, candidate, List.copyOf(pairs), extra, fabricated);
    }

    private static Entity reversedMatch(Entity truthLink, Map<List<String>, Entity> byKey,
                                        Set<List<String>> truthKeys) {
        List<String> rev = List.of(truthLink.norm().get(1), truthLink.norm().get(0));
        return truthKeys.contains(rev) ? null : byKey.get(rev);
    }
}
