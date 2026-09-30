package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeProduct;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.model.Quantity;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.SnrmRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.snrmbench.netbench.model.TimeUnit.DAY;
import static org.snrmbench.netbench.model.TimeUnit.WEEK;
import static org.assertj.core.api.Assertions.assertThat;

/** header order, row order, minimal quoting, LF endings, no BOM. */
class CanonicalCsvWriterTest {

    private static final String PRODUCT_B = "Widget-B";
    private static final String PRODUCT_A = "Widget \"A\"";

    private static BigDecimal bd(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static Quantity q(long value, TimeUnit unit) {
        return Quantity.of(value, unit);
    }

    private static Quantity q(String value, TimeUnit unit) {
        return new Quantity(bd(value), unit);
    }

    private static Node node(String name, NodeType type, int tier, Quantity cap, Quantity pt, String fixed,
                             String var, String fp, String region, String lat, String lng) {
        return new Node(name, type, tier, cap, pt, bd(fixed), bd(var), bd(fp), region, bd(lat), bd(lng));
    }

    private static Network network() {
        TimeBase tb = new TimeBase(BigDecimal.ONE, DAY, 90, RoundingPolicy.NEAREST, "Widget supply network\n(draft)");
        List<Node> nodes = List.of(
                node("CUS-01", NodeType.CUSTOMER, 2, new Quantity(null, DAY), q(0, DAY), "0", "0", "0", "EU-North",
                        null, null),
                node("PLT-01", NodeType.PLANT, 1, q(1200, DAY), q(2, DAY), "12000", "5.25", "0.01", null, null, null),
                node("SUP-02", NodeType.SUPPLIER, 0, null, q(0, DAY), null, "2.5", "0.02", "EU-West", null, null),
                node("SUP-01", NodeType.SUPPLIER, 0, q(300, WEEK), q(0, DAY), "750", "1.8", "0.05", "EU, West",
                        "52.37", "4.9"));
        List<Link> links = List.of(
                new Link("PLT-01", "CUS-01", q(1, DAY), q(250, DAY), bd("0.2"), bd("0.02")),
                new Link("SUP-02", "PLT-01", q(2, DAY), q(600, DAY), bd("0.45"), bd("0.01")),
                new Link("SUP-01", "PLT-01", q(3, WEEK), q(400, DAY), bd("1.5"), bd("0.03")));
        List<Product> products = List.of(new Product(PRODUCT_B, bd("40")), new Product(PRODUCT_A, bd("25")));
        List<NodeProduct> nps = List.of(
                new NodeProduct("PLT-01", PRODUCT_A, q(0, DAY), bd("60"), bd("30"), q("0.1", DAY)),
                new NodeProduct("CUS-01", PRODUCT_B, q(30, DAY), bd("0"), bd("0"), q(0, DAY)),
                new NodeProduct("SUP-01", PRODUCT_B, q(0, DAY), bd("900"), bd("450"), q("0.02", DAY)),
                new NodeProduct("CUS-01", PRODUCT_A, q(12, WEEK), bd("0"), bd("0"), q(0, DAY)),
                new NodeProduct("PLT-01", PRODUCT_B, q(0, DAY), bd("150"), bd("75"), q("0.05", WEEK)));
        return new Network(tb, List.of(), products, nodes, links, nps);
    }

    @Test
    void networkMetaQuotesANameWithANewline() {
        assertThat(CanonicalCsvWriter.render(network(), false).get(Sheet.NETWORK_META)).isEqualTo("""
                period_length_value,period_length_unit,horizon_periods,rounding_policy,name
                1,DAY,90,NEAREST,"Widget supply network
                (draft)"
                """);
    }

    /** Tier then name; a comma forces quotes; an unstated capacity blanks its unit cell too. */
    @Test
    void nodesInTierThenNameOrder() {
        assertThat(CanonicalCsvWriter.render(network(), false).get(Sheet.NODES)).isEqualTo("""
                name,type,capacity_value,capacity_time_unit,processing_time_value,processing_time_unit,\
                fixed_cost,var_cost,failure_prob,region
                SUP-01,SUPPLIER,300,WEEK,0,DAY,750,1.80,0.05,"EU, West"
                SUP-02,SUPPLIER,,,0,DAY,,2.50,0.02,EU-West
                PLT-01,PLANT,1200,DAY,2,DAY,12000,5.25,0.01,
                CUS-01,CUSTOMER,,DAY,0,DAY,0,0,0,EU-North
                """);
    }

    @Test
    void nodesWithGeoAddLatAndLngWithTwoDecimals() {
        assertThat(CanonicalCsvWriter.render(network(), true).get(Sheet.NODES)).isEqualTo("""
                name,type,capacity_value,capacity_time_unit,processing_time_value,processing_time_unit,\
                fixed_cost,var_cost,failure_prob,region,lat,lng
                SUP-01,SUPPLIER,300,WEEK,0,DAY,750,1.80,0.05,"EU, West",52.37,4.90
                SUP-02,SUPPLIER,,,0,DAY,,2.50,0.02,EU-West,,
                PLT-01,PLANT,1200,DAY,2,DAY,12000,5.25,0.01,,,
                CUS-01,CUSTOMER,,DAY,0,DAY,0,0,0,EU-North,,
                """);
    }

    @Test
    void linksInSourceThenTargetNodeOrder() {
        assertThat(CanonicalCsvWriter.render(network(), false).get(Sheet.LINKS)).isEqualTo("""
                source,target,lead_time_value,lead_time_unit,capacity_value,capacity_time_unit,unit_cost,failure_prob
                SUP-01,PLT-01,3,WEEK,400,DAY,1.50,0.03
                SUP-02,PLT-01,2,DAY,600,DAY,0.45,0.01
                PLT-01,CUS-01,1,DAY,250,DAY,0.20,0.02
                """);
    }

    /** Product order is list order, not alphabetical; a quote forces quotes and is doubled. */
    @Test
    void productsInProductOrder() {
        assertThat(CanonicalCsvWriter.render(network(), false).get(Sheet.PRODUCTS)).isEqualTo("""
                name,unit_value
                Widget-B,40
                "Widget ""A\""",25
                """);
    }

    @Test
    void nodeProductsInNodeThenProductOrder() {
        assertThat(CanonicalCsvWriter.render(network(), false).get(Sheet.NODE_PRODUCTS)).isEqualTo("""
                node,product,demand_value,demand_time_unit,initial_inventory,safety_stock,holding_cost_value,\
                holding_cost_time_unit
                SUP-01,Widget-B,0,DAY,900,450,0.02,DAY
                PLT-01,Widget-B,0,DAY,150,75,0.05,WEEK
                PLT-01,"Widget ""A\""",0,DAY,60,30,0.10,DAY
                CUS-01,Widget-B,30,DAY,0,0,0,DAY
                CUS-01,"Widget ""A\""",12,WEEK,0,0,0,DAY
                """);
    }

    @Test
    void writesFiveUtf8FilesWithoutBomOrCarriageReturns(@TempDir Path dir) throws IOException {
        Network n = network();
        CanonicalCsvWriter.write(n, false, dir);
        Map<Sheet, String> rendered = CanonicalCsvWriter.render(n, false);
        try (var list = Files.list(dir)) {
            assertThat(list.map(p -> p.getFileName().toString()).sorted()).containsExactly(
                    "links.csv", "network_meta.csv", "node_products.csv", "nodes.csv", "products.csv");
        }
        for (Sheet s : Sheet.values()) {
            byte[] bytes = Files.readAllBytes(dir.resolve(s.fileName()));
            assertThat(bytes).as(s.fileName()).doesNotContain((byte) '\r');
            assertThat(bytes[0]).as(s.fileName()).isNotEqualTo((byte) 0xEF);
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(rendered.get(s)).endsWith("\n");
        }
    }

    /** What the writer emits, the lenient reader reads back and SnrmRules accepts without findings. */
    @Test
    void theWrittenNetworkReadsBackClean() {
        Map<String, String> files = new LinkedHashMap<>();
        CanonicalCsvWriter.render(network(), true).forEach((s, text) -> files.put(s.fileName(), text));
        RawNetwork raw = LenientCsvReader.read(files);
        assertThat(SnrmRules.validate(raw).findings()).isEmpty();
        RawTable meta = raw.table(Sheet.NETWORK_META).orElseThrow();
        assertThat(meta.rows().get(0).cells()).last().isEqualTo("Widget supply network\n(draft)");
    }
}
