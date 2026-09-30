package org.snrmbench.netbench.schema;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** the schema equals exactly; the lists are spelled out here, {@code *} = required. */
class SchemaTest {

    /** Names in canonical order, each marked {@code *} when required. */
    private static List<String> spelled(List<Column> columns) {
        return columns.stream().map(c -> c.name() + (c.required() ? "*" : "")).toList();
    }

    private static List<String> qualified(Column.Kind kind) {
        return allColumns().stream().filter(c -> c.kind() == kind).map(Column::qualified).toList();
    }

    private static List<String> qualified(Column.Check check) {
        return allColumns().stream().filter(c -> c.check() == check).map(Column::qualified).toList();
    }

    private static List<Column> allColumns() {
        return Arrays.stream(Sheet.values()).flatMap(s -> Schema.columns(s).stream()).toList();
    }

    @Test
    void sheetsAreTheFiveCanonicalFilesInOrder() {
        assertThat(Arrays.stream(Sheet.values()).map(Sheet::fileName)).containsExactly(
                "network_meta.csv", "nodes.csv", "links.csv", "products.csv", "node_products.csv");
    }

    @Test
    void columnListsEqualSection41() {
        assertThat(spelled(Schema.columns(Sheet.NETWORK_META))).containsExactly(
                "period_length_value*", "period_length_unit*", "horizon_periods*", "rounding_policy", "name");
        assertThat(spelled(Schema.columns(Sheet.NODES))).containsExactly(
                "name*", "type*", "capacity_value", "capacity_time_unit", "processing_time_value",
                "processing_time_unit", "fixed_cost", "var_cost", "failure_prob", "region", "lat", "lng", "pos_x",
                "pos_y", "caption", "caption_visible");
        assertThat(spelled(Schema.columns(Sheet.LINKS))).containsExactly(
                "source*", "target*", "lead_time_value", "lead_time_unit", "capacity_value", "capacity_time_unit",
                "unit_cost", "failure_prob", "caption", "caption_visible");
        assertThat(spelled(Schema.columns(Sheet.PRODUCTS))).containsExactly("name*", "unit_value");
        assertThat(spelled(Schema.columns(Sheet.NODE_PRODUCTS))).containsExactly(
                "node*", "product*", "demand_value", "demand_time_unit", "initial_inventory", "safety_stock",
                "holding_cost_value", "holding_cost_time_unit");
    }

    @Test
    void everyColumnBelongsToItsSheetAndIsLowerCase() {
        for (Sheet s : Sheet.values()) {
            assertThat(Schema.columns(s)).allSatisfy(c -> {
                assertThat(c.sheet()).isEqualTo(s);
                assertThat(c.name()).matches("[a-z_]+");
            });
            assertThat(Schema.required(s)).containsExactlyElementsOf(
                    Schema.columns(s).stream().filter(Column::required).toList());
        }
    }

    @Test
    void writtenColumnsWithoutGeo() {
        assertThat(spelled(Schema.writtenColumns(Sheet.NETWORK_META, false))).containsExactly(
                "period_length_value*", "period_length_unit*", "horizon_periods*", "rounding_policy", "name");
        assertThat(spelled(Schema.writtenColumns(Sheet.NODES, false))).containsExactly(
                "name*", "type*", "capacity_value", "capacity_time_unit", "processing_time_value",
                "processing_time_unit", "fixed_cost", "var_cost", "failure_prob", "region");
        assertThat(spelled(Schema.writtenColumns(Sheet.LINKS, false))).containsExactly(
                "source*", "target*", "lead_time_value", "lead_time_unit", "capacity_value", "capacity_time_unit",
                "unit_cost", "failure_prob");
        assertThat(spelled(Schema.writtenColumns(Sheet.PRODUCTS, false))).containsExactly("name*", "unit_value");
        assertThat(spelled(Schema.writtenColumns(Sheet.NODE_PRODUCTS, false))).containsExactly(
                "node*", "product*", "demand_value", "demand_time_unit", "initial_inventory", "safety_stock",
                "holding_cost_value", "holding_cost_time_unit");
    }

    @Test
    void writtenColumnsWithGeoAddOnlyLatAndLngToNodes() {
        assertThat(spelled(Schema.writtenColumns(Sheet.NODES, true))).containsExactly(
                "name*", "type*", "capacity_value", "capacity_time_unit", "processing_time_value",
                "processing_time_unit", "fixed_cost", "var_cost", "failure_prob", "region", "lat", "lng");
        for (Sheet s : List.of(Sheet.NETWORK_META, Sheet.LINKS, Sheet.PRODUCTS, Sheet.NODE_PRODUCTS)) {
            assertThat(Schema.writtenColumns(s, true)).isEqualTo(Schema.writtenColumns(s, false));
        }
    }

    /** which columns are written with two decimals and which as integers. */
    @Test
    void numericKindsFollowSection45() {
        assertThat(qualified(Column.Kind.DECIMAL2)).containsExactlyInAnyOrder(
                "nodes.var_cost", "links.unit_cost", "node_products.holding_cost_value", "nodes.failure_prob",
                "links.failure_prob", "nodes.lat", "nodes.lng");
        assertThat(qualified(Column.Kind.INTEGER)).containsExactlyInAnyOrder(
                "nodes.fixed_cost", "products.unit_value", "nodes.capacity_value", "links.capacity_value",
                "nodes.processing_time_value", "links.lead_time_value", "node_products.demand_value",
                "node_products.initial_inventory", "node_products.safety_stock", "network_meta.period_length_value",
                "network_meta.horizon_periods");
        assertThat(qualified(Column.Kind.TIME_UNIT)).allMatch(q -> q.endsWith("_unit"))
                .hasSize(Schema.unitPairs().size());
    }

    /** the scope of PROBABILITY_OUT_OF_RANGE and OUT_OF_RANGE. */
    @Test
    void rangeChecksFollowSection44() {
        assertThat(qualified(Column.Check.PROBABILITY)).containsExactlyInAnyOrder(
                "nodes.failure_prob", "links.failure_prob");
        assertThat(qualified(Column.Check.POSITIVE)).containsExactly("network_meta.period_length_value");
        assertThat(qualified(Column.Check.AT_LEAST_ONE)).containsExactly("network_meta.horizon_periods");
        assertThat(qualified(Column.Check.NON_NEGATIVE)).containsExactlyInAnyOrder(
                "nodes.capacity_value", "links.capacity_value",
                "nodes.fixed_cost", "nodes.var_cost", "links.unit_cost", "node_products.holding_cost_value",
                "node_products.demand_value", "node_products.initial_inventory", "node_products.safety_stock",
                "links.lead_time_value", "nodes.processing_time_value", "products.unit_value");
    }

    @Test
    void headersMatchCaseInsensitivelyAndTrimmed() {
        assertThat(Schema.column(Sheet.NODES, "  Capacity_VALUE ")).contains(Schema.NODE_CAPACITY_VALUE);
        assertThat(Schema.column(Sheet.NODES, "notes")).isEmpty();
        assertThat(Sheet.byFileName("Node_Products.CSV")).contains(Sheet.NODE_PRODUCTS);
        assertThat(Sheet.byFileName("assumptions.csv")).isEmpty();
    }
}
