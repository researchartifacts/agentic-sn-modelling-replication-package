package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.Quantity;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Numbers;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Writes a network as the five canonical files: UTF-8 without BOM, \n, comma, minimal RFC 4180 quoting, headers exactly as the schema, numbers through Numbers, and a fixed row order (nodes by tier then name. */
public final class CanonicalCsvWriter {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setRecordSeparator("\n").build();

    private CanonicalCsvWriter() {
    }

    /** The five files' contents, in sheet order. */
    public static Map<Sheet, String> render(Network network, boolean geo) {
        List<Node> nodes = sortedNodes(network.nodes());
        Map<String, Integer> nodeOrder = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            nodeOrder.put(nodes.get(i).name(), i);
        }
        Map<String, Integer> productOrder = new HashMap<>();
        for (int i = 0; i < network.products().size(); i++) {
            productOrder.put(network.products().get(i).name(), i);
        }
        List<Link> links = network.links().stream()
                .sorted(Comparator.comparingInt((Link l) -> nodeOrder.get(l.source()))
                        .thenComparingInt(l -> nodeOrder.get(l.target())))
                .toList();
        List<NodeProduct> nps = network.nodeProducts().stream()
                .sorted(Comparator.comparingInt((NodeProduct np) -> nodeOrder.get(np.node()))
                        .thenComparingInt(np -> productOrder.get(np.product())))
                .toList();

        Map<Sheet, String> out = new EnumMap<>(Sheet.class);
        out.put(Sheet.NETWORK_META, table(Sheet.NETWORK_META, geo, List.of(network.timeBase()),
                CanonicalCsvWriter::metaCell));
        out.put(Sheet.NODES, table(Sheet.NODES, geo, nodes, CanonicalCsvWriter::nodeCell));
        out.put(Sheet.LINKS, table(Sheet.LINKS, geo, links, CanonicalCsvWriter::linkCell));
        out.put(Sheet.PRODUCTS, table(Sheet.PRODUCTS, geo, network.products(), CanonicalCsvWriter::productCell));
        out.put(Sheet.NODE_PRODUCTS, table(Sheet.NODE_PRODUCTS, geo, nps, CanonicalCsvWriter::nodeProductCell));
        return out;
    }

    /** Writes the five files into {@code dir} (created if needed). */
    public static void write(Network network, boolean geo, Path dir) {
        try {
            Files.createDirectories(dir);
            for (Map.Entry<Sheet, String> e : render(network, geo).entrySet()) {
                Files.writeString(dir.resolve(e.getKey().fileName()), e.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Nodes by tier, then by name (ordinal String order, locale-free). */
    public static List<Node> sortedNodes(List<Node> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparingInt(Node::tierIndex).thenComparing(Node::name))
                .toList();
    }

    private interface CellFn<T> {
        String cell(T row, Column column);
    }

    private static <T> String table(Sheet sheet, boolean geo, List<T> rows, CellFn<T> fn) {
        List<Column> columns = Schema.writtenColumns(sheet, geo);
        StringBuilder sb = new StringBuilder();
        try (CSVPrinter printer = new CSVPrinter(sb, FORMAT)) {
            printer.printRecord(columns.stream().map(Column::name).toList());
            for (T row : rows) {
                printer.printRecord(columns.stream().map(c -> fn.cell(row, c)).toList());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }

    private static String num(BigDecimal v, Column c) {
        return Numbers.format(v, c.kind());
    }

    private static String value(Quantity q, Column c) {
        return q == null ? "" : num(q.value(), c);
    }

    private static String unit(Quantity q) {
        return q == null || q.unit() == null ? "" : q.unit().name();
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }

    private static String metaCell(TimeBase tb, Column c) {
        if (c.equals(Schema.META_PERIOD_VALUE)) {
            return num(tb.periodValue(), c);
        } else if (c.equals(Schema.META_PERIOD_UNIT)) {
            return tb.periodUnit().name();
        } else if (c.equals(Schema.META_HORIZON)) {
            return Integer.toString(tb.horizonPeriods());
        } else if (c.equals(Schema.META_ROUNDING)) {
            return tb.policy().name();
        } else if (c.equals(Schema.META_NAME)) {
            return text(tb.name());
        }
        throw new IllegalArgumentException(c.qualified());
    }

    private static String nodeCell(Node n, Column c) {
        if (c.equals(Schema.NODE_NAME)) {
            return n.name();
        } else if (c.equals(Schema.NODE_TYPE)) {
            return n.type().name();
        } else if (c.equals(Schema.NODE_CAPACITY_VALUE)) {
            return value(n.capacity(), c);
        } else if (c.equals(Schema.NODE_CAPACITY_UNIT)) {
            return unit(n.capacity());
        } else if (c.equals(Schema.NODE_PROCESSING_VALUE)) {
            return value(n.processingTime(), c);
        } else if (c.equals(Schema.NODE_PROCESSING_UNIT)) {
            return unit(n.processingTime());
        } else if (c.equals(Schema.NODE_FIXED_COST)) {
            return num(n.fixedCost(), c);
        } else if (c.equals(Schema.NODE_VAR_COST)) {
            return num(n.varCost(), c);
        } else if (c.equals(Schema.NODE_FAILURE_PROB)) {
            return num(n.failureProb(), c);
        } else if (c.equals(Schema.NODE_REGION)) {
            return text(n.region());
        } else if (c.equals(Schema.NODE_LAT)) {
            return num(n.lat(), c);
        } else if (c.equals(Schema.NODE_LNG)) {
            return num(n.lng(), c);
        }
        throw new IllegalArgumentException(c.qualified());
    }

    private static String linkCell(Link l, Column c) {
        if (c.equals(Schema.LINK_SOURCE)) {
            return l.source();
        } else if (c.equals(Schema.LINK_TARGET)) {
            return l.target();
        } else if (c.equals(Schema.LINK_LEAD_VALUE)) {
            return value(l.leadTime(), c);
        } else if (c.equals(Schema.LINK_LEAD_UNIT)) {
            return unit(l.leadTime());
        } else if (c.equals(Schema.LINK_CAPACITY_VALUE)) {
            return value(l.capacity(), c);
        } else if (c.equals(Schema.LINK_CAPACITY_UNIT)) {
            return unit(l.capacity());
        } else if (c.equals(Schema.LINK_UNIT_COST)) {
            return num(l.unitCost(), c);
        } else if (c.equals(Schema.LINK_FAILURE_PROB)) {
            return num(l.failureProb(), c);
        }
        throw new IllegalArgumentException(c.qualified());
    }

    private static String productCell(Product p, Column c) {
        if (c.equals(Schema.PRODUCT_NAME)) {
            return p.name();
        } else if (c.equals(Schema.PRODUCT_UNIT_VALUE)) {
            return num(p.unitValue(), c);
        }
        throw new IllegalArgumentException(c.qualified());
    }

    private static String nodeProductCell(NodeProduct np, Column c) {
        if (c.equals(Schema.NP_NODE)) {
            return np.node();
        } else if (c.equals(Schema.NP_PRODUCT)) {
            return np.product();
        } else if (c.equals(Schema.NP_DEMAND_VALUE)) {
            return value(np.demand(), c);
        } else if (c.equals(Schema.NP_DEMAND_UNIT)) {
            return unit(np.demand());
        } else if (c.equals(Schema.NP_INITIAL_INVENTORY)) {
            return num(np.initialInventory(), c);
        } else if (c.equals(Schema.NP_SAFETY_STOCK)) {
            return num(np.safetyStock(), c);
        } else if (c.equals(Schema.NP_HOLDING_VALUE)) {
            return value(np.holdingCost(), c);
        } else if (c.equals(Schema.NP_HOLDING_UNIT)) {
            return unit(np.holdingCost());
        }
        throw new IllegalArgumentException(c.qualified());
    }
}
