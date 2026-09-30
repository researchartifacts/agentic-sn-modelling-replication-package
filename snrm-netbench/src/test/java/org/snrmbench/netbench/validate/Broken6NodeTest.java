package org.snrmbench.netbench.validate;

import org.snrmbench.netbench.csv.LenientCsvReader;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.snrmbench.netbench.validate.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** SNRM's broken-6-node sample rebuilt from the samples README: minimal-6-node with all the damage at once (11 node rows, 9 link rows, 9 node-product rows, assumptions.csv), every damaged row on the line the README names. */
class Broken6NodeTest {

    private static final String META = """
            period_length_value,period_length_unit,rounding_policy
            1,DAY,NEAREST
            """;

    private static final String NODES = """
            name,type,capacity_value,processing_time_value,processing_time_unit,fixed_cost,var_cost,failure_prob,region,lat,lng,notes
            SUP-1,SUPPLIER,500,0,,1000,2,0.02,EU-West,52.37,4.90,sole source,STRAY
            PLANT-1,PLANT,400,1,,5000,5,0.01,EU-West,51.22,6.78,two lines
            DC-1,DC,350,0,,1200,1,0.01,EU-North,59.33,18.07,"Nordic hub, 3PL operated"
            DC-2,DC,350,0,,1200,1,0.03,EU-South,41.90,12.50,
            DC-1,DC,350,0,,1200,1,0.01,EU-North,59.33,18.07,
            CUST-1,CUSTOMER,,0,,0,0,0,EU-North,60.17,24.94,
            CUST-2,CUSTOMER,,0,,0,0,0,EU-South,37.98,23.73,
            ,DC,350,0,,1200,1,0.01,EU-Central,50.11,8.68,
            WH-1,WAREHOUSE,350,0,,1200,1,0.01,EU-Central,48.14,11.58,
            DC-3,DC,n/a,0,fortnight,1200,1,1.4,EU-East,52.23,21.01,
            ORPHAN-1,DC,350,0,,1200,1,0.01,EU-East,50.08,14.44,
            """;

    private static final String LINKS = """
            source,target,lead_time_value,lead_time_unit,capacity_value,unit_cost,failure_prob
            SUP-1,PLANT-1,2,,500,3,0.01
            PLANT-1,DC-1,3,,300,4,0.02
            PLANT-1,DC-1,3,,300,4,0.02
            PLANT-1,DC-2,4,,300,4,0.02
            DC-1,CUST-1,6,HOUR,200,2,0.01
            DC-1,DC-2,30,HOUR,100,2,0.01
            DC-2,DC-2,1,,100,2,0.01
            DC-9,CUST-2,1,,200,2,0.01
            CUST-1,SUP-1,4,YEAR,50,5,0.05
            """;

    private static final String PRODUCTS = """
            name,unit_value
            WIDGET-A,25
            WIDGET-B,-5
            """;

    private static final String NODE_PRODUCTS = """
            node,product,demand_value,initial_inventory,safety_stock,holding_cost_value
            CUST-1,WIDGET-A,40,0,0,0
            CUST-1,WIDGET-A,40,0,0,0
            CUST-2,WIDGET-A,25,0,0,0
            DC-1,WIDGET-A,0,120,60,0.10
            DC-2,WIDGET-A,0,120,60,0.10
            PLANT-1,WIDGET-A,10,200,80,0.05
            SUP-1,WIDGET-A,0,300,100,0.02
            CUST-1,WIDGET-Z,5,0,0,0
            DC-9,WIDGET-A,0,120,60,0.10
            """;

    private static final String ASSUMPTIONS = """
            assumption,value
            lead times,in days
            """;

    private static ValidationResult validate() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("network_meta.csv", META);
        files.put("nodes.csv", NODES);
        files.put("links.csv", LINKS);
        files.put("products.csv", PRODUCTS);
        files.put("node_products.csv", NODE_PRODUCTS);
        files.put("assumptions.csv", ASSUMPTIONS);
        return SnrmRules.validate(LenientCsvReader.read(files));
    }

    @Test
    void everyReadmeRowIsReportedWhereTheReadmeSaysInCheckOrder() {
        ValidationResult r = validate();
        assertThat(r.findings())
                .extracting(Finding::code, Finding::file, Finding::line, Finding::column, Finding::element)
                .containsExactly(
                        tuple(UNRECOGNISED_SHEET, "assumptions.csv", null, null, null),
                        tuple(UNMAPPED_COLUMN, "nodes.csv", 1, "notes", null),
                        tuple(ROW_WIDTH_MISMATCH, "nodes.csv", 2, null, null),
                        tuple(MISSING_REQUIRED_COLUMN, "network_meta.csv", 1, "horizon_periods", null),
                        tuple(DUPLICATE_KEY, "nodes.csv", 6, "name", null),
                        tuple(REQUIRED_VALUE_MISSING, "nodes.csv", 9, "name", null),
                        tuple(UNKNOWN_ENUM_VALUE, "nodes.csv", 10, "type", null),
                        tuple(NOT_A_NUMBER, "nodes.csv", 11, "capacity_value", null),
                        tuple(UNKNOWN_TIME_UNIT, "nodes.csv", 11, "processing_time_unit", null),
                        tuple(PROBABILITY_OUT_OF_RANGE, "nodes.csv", 11, "failure_prob", null),
                        tuple(DUPLICATE_KEY, "links.csv", 4, "source", null),
                        tuple(SELF_LOOP, "links.csv", 8, "target", null),
                        tuple(UNKNOWN_NODE_REFERENCE, "links.csv", 9, "source", null),
                        tuple(OUT_OF_RANGE, "products.csv", 3, "unit_value", null),
                        tuple(DUPLICATE_KEY, "node_products.csv", 3, "node", null),
                        tuple(UNKNOWN_PRODUCT_REFERENCE, "node_products.csv", 9, "product", null),
                        tuple(UNKNOWN_NODE_REFERENCE, "node_products.csv", 10, "node", null),
                        tuple(CUSTOMER_UNREACHABLE, "nodes.csv", 8, null, "CUST-2"),
                        tuple(DURATION_ROUNDS_TO_ZERO, "links.csv", 6, null, "DC-1->CUST-1"),
                        tuple(DURATION_ROUNDING_ERROR, "links.csv", 7, null, "DC-1->DC-2"),
                        tuple(PERIOD_TOO_FINE, "links.csv", 10, null, "CUST-1->SUP-1"),
                        tuple(LATERAL_LINK, "links.csv", 7, null, "DC-1->DC-2"),
                        tuple(ECHELON_DIRECTION, "links.csv", 10, null, "CUST-1->SUP-1"),
                        tuple(ORPHAN_NODE, "nodes.csv", 8, null, "CUST-2"),
                        tuple(ORPHAN_NODE, "nodes.csv", 12, null, "ORPHAN-1"),
                        tuple(DEMAND_ON_NON_CUSTOMER, "node_products.csv", 7, null, "PLANT-1|WIDGET-A"),
                        tuple(NETWORK_CHECKS_ON_PARTIAL_GRAPH, null, null, null, null));
    }

    /** 16 errors (README headline: 17, itemised: 16) and 11 warnings (README: 11). */
    @Test
    void sixteenErrorsAndElevenWarnings() {
        ValidationResult r = validate();
        assertThat(r.valid()).isFalse();
        assertThat(r.errorCount()).isEqualTo(16);
        assertThat(r.warningCount()).isEqualTo(11);
        assertThat(r.countsByCode(Severity.ERROR)).isEqualTo(Map.ofEntries(
                Map.entry("MISSING_REQUIRED_COLUMN", 1), Map.entry("REQUIRED_VALUE_MISSING", 1),
                Map.entry("UNKNOWN_ENUM_VALUE", 1), Map.entry("NOT_A_NUMBER", 1), Map.entry("UNKNOWN_TIME_UNIT", 1),
                Map.entry("PROBABILITY_OUT_OF_RANGE", 1), Map.entry("DUPLICATE_KEY", 3), Map.entry("SELF_LOOP", 1),
                Map.entry("UNKNOWN_NODE_REFERENCE", 2), Map.entry("UNKNOWN_PRODUCT_REFERENCE", 1),
                Map.entry("OUT_OF_RANGE", 1), Map.entry("CUSTOMER_UNREACHABLE", 1),
                Map.entry("DURATION_ROUNDS_TO_ZERO", 1)));
        assertThat(r.countsByCode(Severity.WARNING)).isEqualTo(Map.ofEntries(
                Map.entry("UNRECOGNISED_SHEET", 1), Map.entry("UNMAPPED_COLUMN", 1), Map.entry("ROW_WIDTH_MISMATCH", 1),
                Map.entry("LATERAL_LINK", 1), Map.entry("ECHELON_DIRECTION", 1), Map.entry("DURATION_ROUNDING_ERROR", 1),
                Map.entry("PERIOD_TOO_FINE", 1), Map.entry("ORPHAN_NODE", 2), Map.entry("DEMAND_ON_NON_CUSTOMER", 1),
                Map.entry("NETWORK_CHECKS_ON_PARTIAL_GRAPH", 1)));
    }

    @Test
    void theMissingHorizonFallsBackTo52() {
        assertThat(validate().timeBase().horizonPeriods()).isEqualTo(52);
    }
}
