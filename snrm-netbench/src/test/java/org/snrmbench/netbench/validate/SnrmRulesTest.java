package org.snrmbench.netbench.validate;

import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.TimeBase;
import org.snrmbench.netbench.model.TimeUnit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.snrmbench.netbench.validate.Code.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** one test per row-level and network-level code, each built by editing minimal-6-node in memory the way SNRM's broken-6-node sample damages it. */
class SnrmRulesTest {

    private static final String META = "network_meta.csv";
    private static final String NODES = "nodes.csv";
    private static final String LINKS = "links.csv";
    private static final String PRODUCTS = "products.csv";
    private static final String NODE_PRODUCTS = "node_products.csv";

    /** The five minimal-6-node files as lines of text (index 0 = line 1 = header), edited in place. */
    private static final class Sample {
        private final Map<String, List<String>> files = new LinkedHashMap<>();

        static Sample minimal6Node() {
            Sample s = new Sample();
            Path dir = TestSupport.sample("minimal-6-node");
            for (String f : List.of(META, NODES, LINKS, PRODUCTS, NODE_PRODUCTS)) {
                try {
                    s.put(f, Files.readString(dir.resolve(f), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return s;
        }

        Sample put(String file, String content) {
            List<String> lines = new ArrayList<>(Arrays.asList(content.split("\n", -1)));
            if (lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            files.put(file, lines);
            return this;
        }

        Sample remove(String file) {
            files.remove(file);
            return this;
        }

        Sample set(String file, int line, String text) {
            files.get(file).set(line - 1, text);
            return this;
        }

        Sample edit(String file, int line, UnaryOperator<String> change) {
            return set(file, line, change.apply(files.get(file).get(line - 1)));
        }

        /** Inserts {@code text} so that it becomes line {@code line}. */
        Sample insert(String file, int line, String text) {
            files.get(file).add(line - 1, text);
            return this;
        }

        Sample append(String file, String text) {
            files.get(file).add(text);
            return this;
        }

        /** Deletes the given lines (numbers as they are before the deletion). */
        Sample delete(String file, int... lines) {
            int[] sorted = lines.clone();
            Arrays.sort(sorted);
            for (int i = sorted.length - 1; i >= 0; i--) {
                files.get(file).remove(sorted[i] - 1);
            }
            return this;
        }

        /** Appends a column to the header and a blank cell to every row. */
        Sample addColumn(String file, String header) {
            List<String> lines = files.get(file);
            lines.set(0, lines.get(0) + "," + header);
            for (int i = 1; i < lines.size(); i++) {
                lines.set(i, lines.get(i) + ",");
            }
            return this;
        }

        ValidationResult validate() {
            Map<String, String> text = new LinkedHashMap<>();
            files.forEach((f, lines) -> text.put(f, String.join("\n", lines) + "\n"));
            return SnrmRules.validate(LenientCsvReader.read(text));
        }
    }

    private static Sample minimal() {
        return Sample.minimal6Node();
    }

    private static List<Code> codes(ValidationResult r) {
        return r.findings().stream().map(Finding::code).toList();
    }

    /** The one finding with this code (fails when there is none or more than one). */
    private static Finding single(ValidationResult r, Code code) {
        List<Finding> found = r.findings().stream().filter(f -> f.code() == code).toList();
        assertThat(found).as("%s in %s", code, r.findings()).hasSize(1);
        return found.get(0);
    }

    /** A row-level finding: severity, file, line and column; no element. */
    private static void assertRow(Finding f, Severity severity, String file, Integer line, String column) {
        assertThat(f).extracting(Finding::severity, Finding::file, Finding::line, Finding::column, Finding::element)
                .containsExactly(severity, file, line, column, null);
    }

    /** A network-level finding: severity, file, line and element; no column. */
    private static void assertElement(Finding f, Severity severity, String file, Integer line, String element) {
        assertThat(f).extracting(Finding::severity, Finding::file, Finding::line, Finding::column, Finding::element)
                .containsExactly(severity, file, line, null, element);
    }

    // ---- guard ----------------------------------------------------------------------------------

    @Test
    void everySnrmCodeHasATestNamedAfterIt() {
        Set<Code> verifyOnly = EnumSet.of(ENVELOPE_PARSE_ERROR, FILE_MISSING, NETWORK_META_DEFAULTED);
        List<String> tests = Arrays.stream(SnrmRulesTest.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Test.class)).map(Method::getName).toList();
        for (Code code : Code.values()) {
            if (!verifyOnly.contains(code)) {
                String prefix = camel(code.name());
                assertThat(tests).as("a test for %s", code).anyMatch(t -> t.startsWith(prefix));
            }
        }
    }

    private static String camel(String constant) {
        StringBuilder sb = new StringBuilder();
        for (String part : constant.toLowerCase(Locale.ROOT).split("_")) {
            sb.append(sb.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return sb.toString();
    }

    // ---- row level, errors ----------------------------------------------------------------------

    @Test
    void missingRequiredColumnInNetworkMetaFallsBackToHorizon52() {
        ValidationResult r = minimal()
                .set(META, 1, "period_length_value,period_length_unit,rounding_policy")
                .set(META, 2, "1,DAY,NEAREST")
                .validate();
        assertThat(codes(r)).containsExactly(MISSING_REQUIRED_COLUMN);
        assertRow(single(r, MISSING_REQUIRED_COLUMN), Severity.ERROR, META, 1, "horizon_periods");
        assertThat(r.timeBase()).isEqualTo(new TimeBase(BigDecimal.ONE, TimeUnit.DAY, 52, RoundingPolicy.NEAREST,
                null));
    }

    @Test
    void missingRequiredColumnInLinksDropsEveryLinkRow() {
        ValidationResult r = minimal()
                .set(LINKS, 1, "source,to,lead_time_value,capacity_value,unit_cost,failure_prob")
                .validate();
        assertRow(single(r, MISSING_REQUIRED_COLUMN), Severity.ERROR, LINKS, 1, "target");
        // every link row dropped: the graph has six nodes and no links
        assertThat(r.countsByCode(Severity.ERROR))
                .isEqualTo(Map.of("MISSING_REQUIRED_COLUMN", 1, "CUSTOMER_UNREACHABLE", 2));
        assertThat(r.countsByCode(Severity.WARNING))
                .isEqualTo(Map.of("UNMAPPED_COLUMN", 1, "ORPHAN_NODE", 6, "NETWORK_CHECKS_ON_PARTIAL_GRAPH", 1));
    }

    @Test
    void requiredValueMissingForABlankNodeName() {
        ValidationResult r = minimal()
                .append(NODES, ",DC,350,0,1200,1,0.01,EU-Central,50.11,8.68,,")
                .validate();
        assertThat(codes(r)).containsExactly(REQUIRED_VALUE_MISSING, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, REQUIRED_VALUE_MISSING), Severity.ERROR, NODES, 8, "name");
    }

    @Test
    void unknownEnumValueForNodeTypeWarehouse() {
        ValidationResult r = minimal()
                .append(NODES, "WH-1,WAREHOUSE,350,0,1200,1,0.01,EU-Central,48.14,11.58,,")
                .validate();
        assertThat(codes(r)).containsExactly(UNKNOWN_ENUM_VALUE, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        Finding f = single(r, UNKNOWN_ENUM_VALUE);
        assertRow(f, Severity.ERROR, NODES, 8, "type");
        assertThat(f.message()).contains("nodes.csv line 8", "\"WAREHOUSE\"");
    }

    @Test
    void unknownEnumValueForRoundingPolicyKeepsNearest() {
        ValidationResult r = minimal().set(META, 2, "1,DAY,52,ROUND").validate();
        assertThat(codes(r)).containsExactly(UNKNOWN_ENUM_VALUE);
        assertRow(single(r, UNKNOWN_ENUM_VALUE), Severity.ERROR, META, 2, "rounding_policy");
        assertThat(r.timeBase().policy()).isEqualTo(RoundingPolicy.NEAREST);
    }

    @Test
    void notANumberForCapacityNa() {
        ValidationResult r = minimal()
                .append(NODES, "DC-3,DC,n/a,0,1200,1,0.01,EU-East,52.23,21.01,,")
                .validate();
        assertThat(codes(r)).containsExactly(NOT_A_NUMBER, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        Finding f = single(r, NOT_A_NUMBER);
        assertRow(f, Severity.ERROR, NODES, 8, "capacity_value");
        assertThat(f.message()).isEqualTo("nodes.csv line 8: capacity_value \"n/a\" is not a number");
    }

    @Test
    void unknownTimeUnitForFortnight() {
        ValidationResult r = minimal()
                .addColumn(NODES, "processing_time_unit")
                .append(NODES, "DC-3,DC,350,0,1200,1,0.01,EU-East,52.23,21.01,,,fortnight")
                .validate();
        assertThat(codes(r)).containsExactly(UNKNOWN_TIME_UNIT, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        Finding f = single(r, UNKNOWN_TIME_UNIT);
        assertRow(f, Severity.ERROR, NODES, 8, "processing_time_unit");
        assertThat(f.message()).contains("\"fortnight\"");
    }

    /** a present-but-blank unit cell is the period unit. */
    @Test
    void unknownTimeUnitIsNotRaisedForABlankUnitCell() {
        ValidationResult r = minimal()
                .set(META, 2, "1,WEEK,52,NEAREST")
                .addColumn(LINKS, "lead_time_unit")
                .addColumn(NODES, "processing_time_unit")
                .validate();
        assertThat(r.findings()).isEmpty();
    }

    @Test
    void probabilityOutOfRangeForFailureProb14() {
        ValidationResult r = minimal()
                .append(NODES, "DC-3,DC,350,0,1200,1,1.4,EU-East,52.23,21.01,,")
                .validate();
        assertThat(codes(r)).containsExactly(PROBABILITY_OUT_OF_RANGE, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, PROBABILITY_OUT_OF_RANGE), Severity.ERROR, NODES, 8, "failure_prob");
    }

    /** README: "three of the errors sit on one row … and are all reported". */
    @Test
    void threeErrorsOnOneRowAreAllReported() {
        ValidationResult r = minimal()
                .addColumn(NODES, "processing_time_unit")
                .append(NODES, "DC-3,DC,n/a,0,1200,1,1.4,EU-East,52.23,21.01,,,fortnight")
                .validate();
        assertThat(r.findings())
                .extracting(Finding::code, Finding::line, Finding::column)
                .containsExactly(
                        tuple(NOT_A_NUMBER, 8, "capacity_value"),
                        tuple(PROBABILITY_OUT_OF_RANGE, 8, "failure_prob"),
                        tuple(UNKNOWN_TIME_UNIT, 8, "processing_time_unit"),
                        tuple(NETWORK_CHECKS_ON_PARTIAL_GRAPH, null, null));
    }

    @Test
    void outOfRangeForNegativeUnitValue() {
        ValidationResult r = minimal().append(PRODUCTS, "WIDGET-B,-5").validate();
        assertThat(codes(r)).containsExactly(OUT_OF_RANGE, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, OUT_OF_RANGE), Severity.ERROR, PRODUCTS, 3, "unit_value");
    }

    @Test
    void outOfRangeForNegativeCapacity() {
        ValidationResult r = minimal().set(LINKS, 7, "DC-1,CUST-2,2,-150,3,0.02").validate();
        assertThat(codes(r)).containsExactly(OUT_OF_RANGE, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, OUT_OF_RANGE), Severity.ERROR, LINKS, 7, "capacity_value");
    }

    @Test
    void outOfRangeForHorizonZeroFallsBackTo52() {
        ValidationResult r = minimal().set(META, 2, "1,DAY,0,NEAREST").validate();
        assertThat(codes(r)).containsExactly(OUT_OF_RANGE);
        assertRow(single(r, OUT_OF_RANGE), Severity.ERROR, META, 2, "horizon_periods");
        assertThat(r.timeBase().horizonPeriods()).isEqualTo(52);
    }

    @Test
    void outOfRangeForPeriodLengthZeroFallsBackToOneDay() {
        ValidationResult r = minimal().set(META, 2, "0,WEEK,52,NEAREST").validate();
        assertThat(codes(r)).containsExactly(OUT_OF_RANGE);
        assertRow(single(r, OUT_OF_RANGE), Severity.ERROR, META, 2, "period_length_value");
        assertThat(r.timeBase().periodValue()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(r.timeBase().periodUnit()).isEqualTo(TimeUnit.DAY);
    }

    @Test
    void outOfRangeIsNotRaisedForAHorizonTooLargeForAnInt() {
        ValidationResult r = minimal().set(META, 2, "1,DAY,3000000000,NEAREST").validate();
        assertThat(r.findings()).isEmpty();
        assertThat(r.timeBase().horizonPeriods()).isEqualTo(52);
    }

    @Test
    void duplicateKeyForNodeDc1() {
        ValidationResult r = minimal()
                .insert(NODES, 6, "DC-1,DC,350,0,1200,1,0.01,EU-North,59.33,18.07,,")
                .validate();
        assertThat(codes(r)).containsExactly(DUPLICATE_KEY, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, DUPLICATE_KEY), Severity.ERROR, NODES, 6, "name");
    }

    @Test
    void duplicateKeyForLinkPlant1ToDc1() {
        ValidationResult r = minimal().insert(LINKS, 4, "PLANT-1,DC-1,3,300,4,0.02").validate();
        assertThat(codes(r)).containsExactly(DUPLICATE_KEY, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, DUPLICATE_KEY), Severity.ERROR, LINKS, 4, "source");
    }

    @Test
    void duplicateKeyForNodeProductCust1WidgetA() {
        ValidationResult r = minimal().insert(NODE_PRODUCTS, 3, "CUST-1,WIDGET-A,40,0,0,0").validate();
        assertThat(codes(r)).containsExactly(DUPLICATE_KEY, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, DUPLICATE_KEY), Severity.ERROR, NODE_PRODUCTS, 3, "node");
    }

    @Test
    void duplicateKeyForProductName() {
        ValidationResult r = minimal().append(PRODUCTS, "WIDGET-A,30").validate();
        assertThat(codes(r)).containsExactly(DUPLICATE_KEY, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, DUPLICATE_KEY), Severity.ERROR, PRODUCTS, 3, "name");
    }

    /** names compare exactly after trimming; case is not folded. */
    @Test
    void duplicateKeyComparesNamesExactlyAfterTrimming() {
        ValidationResult r = minimal()
                .append(NODES, "dc-1,DC,350,0,1200,1,0.01,EU-North,59.33,18.07,,")
                .append(NODES, "  DC-2  ,DC,350,0,1200,1,0.03,EU-South,41.90,12.50,,")
                .validate();
        assertThat(r.findings())
                .extracting(Finding::code, Finding::line, Finding::element)
                .containsExactly(
                        tuple(DUPLICATE_KEY, 9, null),
                        tuple(ORPHAN_NODE, 8, "dc-1"),
                        tuple(NETWORK_CHECKS_ON_PARTIAL_GRAPH, null, null));
    }

    @Test
    void selfLoopDc2ToDc2() {
        ValidationResult r = minimal().append(LINKS, "DC-2,DC-2,1,100,2,0.01").validate();
        assertThat(codes(r)).containsExactly(SELF_LOOP, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, SELF_LOOP), Severity.ERROR, LINKS, 8, "target");
    }

    @Test
    void unknownNodeReferenceToDc9InLinksAndNodeProducts() {
        ValidationResult r = minimal()
                .append(LINKS, "DC-9,CUST-2,1,200,2,0.01")
                .append(NODE_PRODUCTS, "DC-9,WIDGET-A,0,120,60,0.10")
                .validate();
        assertThat(codes(r)).containsExactly(UNKNOWN_NODE_REFERENCE, UNKNOWN_NODE_REFERENCE,
                NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(r.findings().get(0), Severity.ERROR, LINKS, 8, "source");
        assertRow(r.findings().get(1), Severity.ERROR, NODE_PRODUCTS, 8, "node");
        assertThat(r.findings().get(0).message()).contains("\"DC-9\"");
    }

    @Test
    void unknownProductReferenceToWidgetZ() {
        ValidationResult r = minimal().append(NODE_PRODUCTS, "CUST-1,WIDGET-Z,5,0,0,0").validate();
        assertThat(codes(r)).containsExactly(UNKNOWN_PRODUCT_REFERENCE, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, UNKNOWN_PRODUCT_REFERENCE), Severity.ERROR, NODE_PRODUCTS, 8, "product");
    }

    // ---- row level, warnings --------------------------------------------------------------------

    @Test
    void unrecognisedSheetForAssumptionsCsv() {
        ValidationResult r = minimal().put("assumptions.csv", "assumption,value\nlead times,in days\n").validate();
        assertThat(codes(r)).containsExactly(UNRECOGNISED_SHEET);
        assertRow(single(r, UNRECOGNISED_SHEET), Severity.WARNING, "assumptions.csv", null, null);
    }

    @Test
    void unmappedColumnForNodesNotes() {
        ValidationResult r = minimal().addColumn(NODES, "notes").validate();
        assertThat(codes(r)).containsExactly(UNMAPPED_COLUMN);
        assertRow(single(r, UNMAPPED_COLUMN), Severity.WARNING, NODES, 1, "notes");
    }

    /** The row still imports: SUP-1 keeps its links, so nothing else is raised. */
    @Test
    void rowWidthMismatchForAStray13thCell() {
        ValidationResult r = minimal().edit(NODES, 2, line -> line + ",STRAY").validate();
        assertThat(codes(r)).containsExactly(ROW_WIDTH_MISMATCH);
        Finding f = single(r, ROW_WIDTH_MISMATCH);
        assertRow(f, Severity.WARNING, NODES, 2, null);
        assertThat(f.message()).contains("13 cells", "12");
    }

    // ---- network level, errors ------------------------------------------------------------------

    /** README: delete SUP-1 and PLANT-1 while both DCs remain; DCs do not count as supply. */
    @Test
    void noSupplyNodeWhenSup1AndPlant1AreDeleted() {
        ValidationResult r = minimal().delete(NODES, 2, 3).validate();
        assertElement(single(r, NO_SUPPLY_NODE), Severity.ERROR, NODES, null, null);
        assertThat(r.countsByCode(Severity.ERROR)).isEqualTo(Map.of(
                "UNKNOWN_NODE_REFERENCE", 6, "NO_SUPPLY_NODE", 1, "CUSTOMER_UNREACHABLE", 2));
        assertThat(r.countsByCode(Severity.WARNING)).isEqualTo(Map.of("NETWORK_CHECKS_ON_PARTIAL_GRAPH", 1));
    }

    @Test
    void noCustomerNodeWhenBothCustomersAreDeleted() {
        ValidationResult r = minimal().delete(NODES, 6, 7).validate();
        assertElement(single(r, NO_CUSTOMER_NODE), Severity.ERROR, NODES, null, null);
        // the customers' links and demand rows now name no node, so no demand survives either
        assertThat(r.countsByCode(Severity.ERROR)).isEqualTo(Map.of(
                "UNKNOWN_NODE_REFERENCE", 5, "NO_CUSTOMER_NODE", 1, "NO_DEMAND_DECLARED", 1));
    }

    @Test
    void noDemandDeclaredWhenEveryDemandIsZero() {
        ValidationResult r = minimal()
                .set(NODE_PRODUCTS, 2, "CUST-1,WIDGET-A,0,0,0,0")
                .set(NODE_PRODUCTS, 3, "CUST-2,WIDGET-A,0,0,0,0")
                .validate();
        assertThat(codes(r)).containsExactly(NO_DEMAND_DECLARED);
        assertElement(single(r, NO_DEMAND_DECLARED), Severity.ERROR, NODE_PRODUCTS, null, null);
    }

    /** README: the only link into CUST-2 comes from the non-existent DC-9, so CUST-2 is stranded. */
    @Test
    void customerUnreachableWhenTheOnlyLinkIntoCust2ComesFromDc9() {
        ValidationResult r = minimal()
                .delete(LINKS, 6, 7)
                .append(LINKS, "DC-9,CUST-2,1,200,2,0.01")
                .validate();
        assertThat(codes(r)).containsExactly(UNKNOWN_NODE_REFERENCE, CUSTOMER_UNREACHABLE, ORPHAN_NODE,
                NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertRow(single(r, UNKNOWN_NODE_REFERENCE), Severity.ERROR, LINKS, 6, "source");
        assertElement(single(r, CUSTOMER_UNREACHABLE), Severity.ERROR, NODES, 7, "CUST-2");
        assertElement(single(r, ORPHAN_NODE), Severity.WARNING, NODES, 7, "CUST-2");
    }

    /** README: 6 HOUR on a 1-day period becomes instantaneous under NEAREST. */
    @Test
    void durationRoundsToZeroFor6HoursOnADayPeriod() {
        ValidationResult r = minimal()
                .addColumn(LINKS, "lead_time_unit")
                .set(LINKS, 5, "DC-1,CUST-1,6,200,2,0.01,HOUR")
                .validate();
        assertThat(codes(r)).containsExactly(DURATION_ROUNDS_TO_ZERO);
        assertElement(single(r, DURATION_ROUNDS_TO_ZERO), Severity.ERROR, LINKS, 5, "DC-1->CUST-1");
    }

    /** a stated 0 is legal (simulation-verification-3-node imports with zero lead times). */
    @Test
    void durationRoundsToZeroIsNotRaisedForAStatedZero() {
        ValidationResult r = minimal()
                .addColumn(LINKS, "lead_time_unit")
                .set(LINKS, 5, "DC-1,CUST-1,0,200,2,0.01,HOUR")
                .set(LINKS, 6, "DC-2,CUST-2,0,200,2,0.01,")
                .validate();
        assertThat(r.findings()).isEmpty();
    }

    // ---- network level, warnings ----------------------------------------------------------------

    @Test
    void lateralLinkDc1ToDc2() {
        ValidationResult r = minimal().append(LINKS, "DC-1,DC-2,1,100,2,0.01").validate();
        assertThat(codes(r)).containsExactly(LATERAL_LINK);
        assertElement(single(r, LATERAL_LINK), Severity.WARNING, LINKS, 8, "DC-1->DC-2");
    }

    @Test
    void echelonDirectionCust1ToSup1() {
        ValidationResult r = minimal().append(LINKS, "CUST-1,SUP-1,4,50,5,0.05").validate();
        assertThat(codes(r)).containsExactly(ECHELON_DIRECTION);
        assertElement(single(r, ECHELON_DIRECTION), Severity.WARNING, LINKS, 8, "CUST-1->SUP-1");
    }

    @Test
    void orphanNodeNeverLinked() {
        ValidationResult r = minimal()
                .append(NODES, "ORPHAN-1,DC,350,0,1200,1,0.01,EU-East,50.08,14.44,,")
                .validate();
        assertThat(codes(r)).containsExactly(ORPHAN_NODE);
        assertElement(single(r, ORPHAN_NODE), Severity.WARNING, NODES, 8, "ORPHAN-1");
    }

    @Test
    void demandOnNonCustomerForPlant1Demand10() {
        ValidationResult r = minimal().set(NODE_PRODUCTS, 6, "PLANT-1,WIDGET-A,10,200,80,0.05").validate();
        assertThat(codes(r)).containsExactly(DEMAND_ON_NON_CUSTOMER);
        assertElement(single(r, DEMAND_ON_NON_CUSTOMER), Severity.WARNING, NODE_PRODUCTS, 6, "PLANT-1|WIDGET-A");
    }

    /** README: 30 HOUR on a 1-day period rounds to 1 day, −20%. */
    @Test
    void durationRoundingErrorFor30HoursIsMinus20Percent() {
        ValidationResult r = minimal()
                .addColumn(LINKS, "lead_time_unit")
                .set(LINKS, 3, "PLANT-1,DC-1,30,300,4,0.02,HOUR")
                .validate();
        assertThat(codes(r)).containsExactly(DURATION_ROUNDING_ERROR);
        Finding f = single(r, DURATION_ROUNDING_ERROR);
        assertElement(f, Severity.WARNING, LINKS, 3, "PLANT-1->DC-1");
        assertThat(f.message()).contains("rounds to 1 periods (-20%)");
    }

    /** README: 4 YEAR on a 1-day period spans 1,460 periods, past the 1,000× limit. */
    @Test
    void periodTooFineFor4Years() {
        ValidationResult r = minimal()
                .addColumn(LINKS, "lead_time_unit")
                .set(LINKS, 3, "PLANT-1,DC-1,4,300,4,0.02,YEAR")
                .validate();
        assertThat(codes(r)).containsExactly(PERIOD_TOO_FINE);
        Finding f = single(r, PERIOD_TOO_FINE);
        assertElement(f, Severity.WARNING, LINKS, 3, "PLANT-1->DC-1");
        assertThat(f.message()).contains("1460 periods");
    }

    @Test
    void networkChecksOnPartialGraphIsRaisedOnceWhenRowsAreDropped() {
        ValidationResult r = minimal()
                .append(LINKS, "DC-2,DC-2,1,100,2,0.01")
                .append(PRODUCTS, "WIDGET-B,-5")
                .validate();
        assertThat(codes(r)).containsExactly(SELF_LOOP, OUT_OF_RANGE, NETWORK_CHECKS_ON_PARTIAL_GRAPH);
        assertElement(single(r, NETWORK_CHECKS_ON_PARTIAL_GRAPH), Severity.WARNING, null, null, null);
    }

    /** Without products.csv no product reference can be unknown. */
    @Test
    void productCreatedImplicitlyWithoutProductsCsv() {
        ValidationResult r = minimal()
                .remove(PRODUCTS)
                .append(NODE_PRODUCTS, "CUST-1,WIDGET-Z,5,0,0,0")
                .validate();
        assertThat(codes(r)).containsExactly(PRODUCT_CREATED_IMPLICITLY);
        assertRow(single(r, PRODUCT_CREATED_IMPLICITLY), Severity.WARNING, PRODUCTS, null, null);
    }
}
