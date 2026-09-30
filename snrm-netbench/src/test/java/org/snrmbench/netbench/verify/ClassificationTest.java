package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.Severity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.snrmbench.netbench.verify.CsvEdits.addRow;
import static org.snrmbench.netbench.verify.CsvEdits.removeRow;
import static org.snrmbench.netbench.verify.CsvEdits.rename;
import static org.snrmbench.netbench.verify.CsvEdits.sample;
import static org.snrmbench.netbench.verify.CsvEdits.set;
import static org.snrmbench.netbench.verify.CsvEdits.verify;
import static org.snrmbench.netbench.verify.CsvEdits.without;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** a sample mutated into a candidate lands in the expected class. */
class ClassificationTest {

    private static final String UNITS = "multi-echelon-50-node";
    private static final String NO_UNITS = "minimal-6-node";
    private static final String NAMED = "simulation-verification-3-node";

    private static int count(Report r, Field f, FactClass c) {
        return r.l2().perField().get(f.key()).get(c.id());
    }

    private static List<Code> codes(Report r) {
        return r.l0().findings().stream().map(Finding::code).toList();
    }

    @Test
    void renamingANodesCaseEverywhereStillMatches() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, rename(b, "DCR-01", "dcr-01"));

        assertThat(r.importValid()).isTrue();
        assertThat(r.structureF1()).isEqualTo(1.0);
        assertThat(r.factRecall()).isEqualTo(1.0);
        assertThat(r.l2().mismatches()).isEmpty();
    }

    @Test
    void quotedAndSpacedNamesNormalise() {
        Map<String, String> b = sample(NO_UNITS);
        Report r = verify(b, rename(b, "DC-2", "'dc-2 '"));

        assertThat(r.l1().nodes().matched()).isEqualTo(6);
        assertThat(r.l2().mismatches()).isEmpty();
    }

    @Test
    void reversedLinkIsWrongDirectionOnceAndNotFabricated() {
        Map<String, String> b = sample(UNITS);
        Map<String, String> c = removeRow(b, Sheet.LINKS, "SUP-01->PLT-01");
        c = addRow(c, Sheet.LINKS, Map.of(Schema.LINK_SOURCE, "PLT-01", Schema.LINK_TARGET, "SUP-01",
                Schema.LINK_LEAD_VALUE, "2", Schema.LINK_LEAD_UNIT, "DAY", Schema.LINK_CAPACITY_VALUE, "3000",
                Schema.LINK_CAPACITY_UNIT, "WEEK", Schema.LINK_UNIT_COST, "0.40", Schema.LINK_FAILURE_PROB, "0.01"));
        Report r = verify(b, c);

        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::entity, Report.Mismatch::field, Report.Mismatch::cls,
                        Report.Mismatch::candidate)
                .containsExactly("SUP-01->PLT-01", "exists", "wrong_direction", "PLT-01->SUP-01");
        assertThat(r.fabricatedCount()).isZero();
        assertThat(count(r, Field.LINK_LEAD_TIME, FactClass.CORRECT)).isEqualTo(64);
        assertThat(r.l1().missingLinks()).containsExactly("SUP-01->PLT-01");
        assertThat(r.l1().extraLinks()).containsExactly("PLT-01->SUP-01");
        assertThat(r.l1().reversedLinks()).containsExactly("SUP-01->PLT-01");
        assertThat(r.l0().findings()).anySatisfy(f -> {
            assertThat(f.code()).isEqualTo(Code.ECHELON_DIRECTION);
            assertThat(f.element()).isEqualTo("PLT-01->SUP-01");
        });
    }

    @Test
    void changedTypeIsWrongType() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, set(b, Sheet.NODES, "PLT-01", Schema.NODE_TYPE, "DC"));

        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::field, Report.Mismatch::cls, Report.Mismatch::truth,
                        Report.Mismatch::candidate)
                .containsExactly("exists", "wrong_type", "PLANT", "DC");
        assertThat(r.l1().nodeTypeAccuracy()).isEqualTo(49.0 / 50);
        assertThat(r.l1().nodes().matched()).isEqualTo(50);
    }

    @Test
    void unknownTypeIsMatchedAsWrongTypeThoughL0DropsTheRow() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, set(b, Sheet.NODES, "PLT-02", Schema.NODE_TYPE, "WAREHOUSE"));

        assertThat(r.importValid()).isFalse();
        assertThat(codes(r)).contains(Code.UNKNOWN_ENUM_VALUE);
        assertThat(r.l1().nodes().matched()).isEqualTo(50);
        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::entity, Report.Mismatch::cls)
                .containsExactly("PLT-02", "wrong_type");
    }

    @Test
    void weeksAndDaysOfTheSameDurationAreCorrect() {
        Map<String, String> b = sample(UNITS);
        b = set(b, Sheet.LINKS, "SUP-04->PLT-03", Schema.LINK_LEAD_VALUE, "28");
        b = set(b, Sheet.LINKS, "SUP-04->PLT-03", Schema.LINK_LEAD_UNIT, "DAY");
        Map<String, String> c = set(b, Sheet.LINKS, "SUP-03->PLT-04", Schema.LINK_LEAD_VALUE, "21"); // was 3 WEEK
        c = set(c, Sheet.LINKS, "SUP-03->PLT-04", Schema.LINK_LEAD_UNIT, "days");
        c = set(c, Sheet.LINKS, "SUP-04->PLT-03", Schema.LINK_LEAD_VALUE, "4"); // truth 28 DAY
        c = set(c, Sheet.LINKS, "SUP-04->PLT-03", Schema.LINK_LEAD_UNIT, "wk");
        Report r = verify(b, c);

        assertThat(r.l2().mismatches()).isEmpty();
        assertThat(r.factRecall()).isEqualTo(1.0);
    }

    @Test
    void hoursForDaysIsWrongUnit() {
        Map<String, String> b = sample(UNITS);
        b = set(b, Sheet.LINKS, "SUP-03->PLT-04", Schema.LINK_LEAD_VALUE, "21");
        b = set(b, Sheet.LINKS, "SUP-03->PLT-04", Schema.LINK_LEAD_UNIT, "DAY");
        Report r = verify(b, set(b, Sheet.LINKS, "SUP-03->PLT-04", Schema.LINK_LEAD_UNIT, "HOUR"));

        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::cls, Report.Mismatch::truth, Report.Mismatch::truthUnit,
                        Report.Mismatch::candidate, Report.Mismatch::candidateUnit)
                .containsExactly("wrong_unit", "21", "DAY", "21", "HOUR");
    }

    @Test
    void aWeeklyCandidatePeriodIsOneWrongMetaFactAndNoRateCascade() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, set(b, Sheet.NETWORK_META, Fact.META_ENTITY, Schema.META_PERIOD_UNIT, "WEEK"));

        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::kind, Report.Mismatch::field, Report.Mismatch::cls)
                .containsExactly("meta", "period_length", "wrong_unit");
        for (Field rate : List.of(Field.NODE_CAPACITY, Field.LINK_CAPACITY, Field.NP_DEMAND, Field.NP_HOLDING_COST)) {
            int statedRates = r.l2().perField().get(rate.key()).get("stated");
            assertThat(statedRates).as(rate.key()).isPositive();
            assertThat(count(r, rate, FactClass.CORRECT)).as(rate.key()).isEqualTo(statedRates);
        }
    }

    @Test
    void blankUnitsResolveThroughThePeriod() {
        Map<String, String> b = sample(NO_UNITS);
        // truth: lead time 2 with no unit column (1 DAY period); candidate states 48 HOUR, the rest DAY
        Map<String, String> c = b;
        for (String link : List.of("SUP-1->PLANT-1", "PLANT-1->DC-1", "PLANT-1->DC-2", "DC-1->CUST-1",
                "DC-2->CUST-2", "DC-1->CUST-2")) {
            c = set(c, Sheet.LINKS, link, Schema.LINK_LEAD_UNIT, "DAY");
        }
        c = set(c, Sheet.LINKS, "SUP-1->PLANT-1", Schema.LINK_LEAD_VALUE, "48");
        c = set(c, Sheet.LINKS, "SUP-1->PLANT-1", Schema.LINK_LEAD_UNIT, "HOUR");
        assertThat(verify(b, c).l2().mismatches()).isEmpty();

        Report r = verify(b, set(b, Sheet.LINKS, "SUP-1->PLANT-1", Schema.LINK_LEAD_UNIT, "HOUR"));
        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::cls, Report.Mismatch::truthUnit, Report.Mismatch::candidateUnit)
                .containsExactly("wrong_unit", "DAY", "HOUR");
    }

    @Test
    void blankedStatedCapacityIsOmitted() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, set(b, Sheet.NODES, "PLT-01", Schema.NODE_CAPACITY_VALUE, ""));

        assertThat(count(r, Field.NODE_CAPACITY, FactClass.OMITTED)).isEqualTo(1);
        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::cls, Report.Mismatch::truth, Report.Mismatch::truthUnit,
                        Report.Mismatch::candidate)
                .containsExactly("omitted", "1200", "DAY", null);
        assertThat(r.factRecall()).isLessThan(1.0);
        assertThat(r.factPrecision()).isEqualTo(1.0);
    }

    @Test
    void filledUnstatedCostIsFilledUnstated() {
        Map<String, String> c = sample(UNITS);
        Map<String, String> b = set(c, Sheet.NODES, "PLT-01", Schema.NODE_FIXED_COST, "");
        Report r = verify(b, c);

        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::cls, Report.Mismatch::truth, Report.Mismatch::candidate)
                .containsExactly("filled_unstated", null, "12000");
        assertThat(count(r, Field.NODE_FIXED_COST, FactClass.FILLED_UNSTATED)).isEqualTo(1);
        assertThat(r.factRecall()).isEqualTo(1.0);
        assertThat(r.factPrecision()).isLessThan(1.0);
    }

    @Test
    void cappedCustomerIsAConventionViolation() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, set(b, Sheet.NODES, "CUS-01", Schema.NODE_CAPACITY_VALUE, "500"));

        assertThat(r.conventionViolationCount()).isEqualTo(1);
        assertThat(r.l2().conventionViolations()).singleElement()
                .extracting(Report.Mismatch::entity, Report.Mismatch::field)
                .containsExactly("CUS-01", "capacity");
        assertThat(count(r, Field.NODE_CAPACITY, FactClass.CONVENTION_OK)).isEqualTo(31);
        assertThat(r.factRecall()).isEqualTo(1.0);
    }

    @Test
    void addedNodeIsFabricated() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, addRow(b, Sheet.NODES, Map.of(Schema.NODE_NAME, "DCR-99", Schema.NODE_TYPE, "DC",
                Schema.NODE_CAPACITY_VALUE, "100", Schema.NODE_CAPACITY_UNIT, "DAY", Schema.NODE_REGION, "EU-West")));

        assertThat(r.fabricatedCount()).isEqualTo(1);
        assertThat(r.l2().fabricated().get(Sheet.NODES.id())).containsExactly("DCR-99");
        assertThat(r.l1().extraNodes()).containsExactly("DCR-99");
        assertThat(r.l2().mismatches()).singleElement()
                .extracting(Report.Mismatch::entity, Report.Mismatch::field, Report.Mismatch::cls,
                        Report.Mismatch::candidate)
                .containsExactly("DCR-99", "exists", "fabricated", "DC");
        assertThat(r.l2().fabricatedFacts()).isEqualTo(3); // exists + capacity + region
        int correct = r.l2().correctFacts();
        assertThat(r.factPrecision()).isEqualTo((double) correct / (correct + 3));
        assertThat(codes(r)).contains(Code.ORPHAN_NODE);
    }

    @Test
    void droppedProductsIsAWarningAndOmitsTheProductFacts() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, without(b, Sheet.PRODUCTS));

        assertThat(r.importValid()).isTrue();
        assertThat(codes(r)).contains(Code.PRODUCT_CREATED_IMPLICITLY, Code.FILE_MISSING);
        assertThat(r.l0().findings()).filteredOn(f -> f.code() == Code.FILE_MISSING).singleElement()
                .extracting(Finding::severity, Finding::file)
                .containsExactly(Severity.WARNING, Sheet.PRODUCTS.fileName());
        assertThat(count(r, Field.PRODUCT_EXISTS, FactClass.OMITTED)).isEqualTo(2);
        assertThat(count(r, Field.PRODUCT_UNIT_VALUE, FactClass.OMITTED)).isEqualTo(2);
        assertThat(r.envelope().filesMissing()).containsExactly(Sheet.PRODUCTS.fileName());
    }

    @Test
    void droppedNetworkMetaDefaultsAndOmitsTheFourMetaFacts() {
        Map<String, String> b = sample(NAMED);
        Report r = verify(b, without(b, Sheet.NETWORK_META));

        assertThat(codes(r)).contains(Code.NETWORK_META_DEFAULTED, Code.FILE_MISSING);
        assertThat(r.l2().mismatches())
                .extracting(Report.Mismatch::kind, Report.Mismatch::field, Report.Mismatch::cls)
                .containsExactly(tuple("meta", "name", "omitted"), tuple("meta", "period_length", "omitted"),
                        tuple("meta", "horizon_periods", "omitted"), tuple("meta", "rounding_policy", "omitted"));
        assertThat(r.importValid()).isTrue();
    }

    @Test
    void invalidCellsAreScoredByFieldKind() {
        Map<String, String> c = sample(NO_UNITS);
        Map<String, String> b = set(c, Sheet.NODES, "DC-2", Schema.NODE_FIXED_COST, "");
        c = set(c, Sheet.NODES, "PLANT-1", Schema.NODE_CAPACITY_VALUE, "n/a");
        c = set(c, Sheet.NODES, "CUST-1", Schema.NODE_PROCESSING_VALUE, "n/a");
        c = set(c, Sheet.NODES, "DC-2", Schema.NODE_FIXED_COST, "n/a");
        c = set(c, Sheet.LINKS, "DC-1->CUST-1", Schema.LINK_LEAD_UNIT, "fortnight");
        Report r = verify(b, c);

        assertThat(r.importValid()).isFalse();
        assertThat(codes(r)).contains(Code.NOT_A_NUMBER, Code.UNKNOWN_TIME_UNIT);
        assertThat(r.l2().mismatches())
                .extracting(Report.Mismatch::entity, Report.Mismatch::field, Report.Mismatch::cls)
                .containsExactly(tuple("PLANT-1", "capacity", "wrong"),
                        tuple("DC-2", "fixed_cost", "filled_unstated"),
                        tuple("CUST-1", "processing_time", "convention_violation"),
                        tuple("DC-1->CUST-1", "lead_time", "wrong"));
    }

    @Test
    void blankAndRepeatedCandidateKeysAreNotEntities() {
        Map<String, String> b = sample(NO_UNITS);
        Map<String, String> c = addRow(b, Sheet.NODES, Map.of(Schema.NODE_NAME, "", Schema.NODE_TYPE, "DC"));
        c = addRow(c, Sheet.NODES, Map.of(Schema.NODE_NAME, "dc-1", Schema.NODE_TYPE, "CUSTOMER"));
        Report r = verify(b, c);

        assertThat(r.l1().nodes().candidate()).isEqualTo(6);
        assertThat(r.fabricatedCount()).isZero();
        assertThat(r.l2().mismatches()).isEmpty();
        assertThat(codes(r)).contains(Code.REQUIRED_VALUE_MISSING);
    }

    @Test
    void ledgerDecidesStatedAndSentence() {
        Map<String, String> b = sample(NO_UNITS);
        List<Fact> facts = List.of(
                new Fact(1, "meta", Fact.META_ENTITY, "horizon_periods", "52", null, true, false, 1),
                new Fact(2, "node", "SUP-1", "capacity", "500", "DAY", true, false, 4),
                new Fact(3, "node", "PLANT-1", "region", "EU-West", null, false, false, null),
                new Fact(4, "link", "SUP-1->PLANT-1", "exists", "true", null, true, false, 9));
        Map<String, String> c = set(b, Sheet.NETWORK_META, Fact.META_ENTITY, Schema.META_HORIZON, "60");
        c = set(c, Sheet.NODES, "SUP-1", Schema.NODE_CAPACITY_VALUE, "");
        c = removeRow(c, Sheet.LINKS, "SUP-1->PLANT-1");
        Report r = CsvEdits.verify(b, c, facts, null);

        assertThat(r.l2().mismatches())
                .extracting(Report.Mismatch::entity, Report.Mismatch::field, Report.Mismatch::cls,
                        Report.Mismatch::sentence)
                .containsExactly(tuple(Fact.META_ENTITY, "horizon_periods", "wrong", 1),
                        tuple("SUP-1", "capacity", "omitted", 4),
                        tuple("PLANT-1", "region", "filled_unstated", null),
                        tuple("SUP-1->PLANT-1", "exists", "omitted", 9),
                        tuple("SUP-1->PLANT-1", "lead_time", "omitted", null),
                        tuple("SUP-1->PLANT-1", "capacity", "omitted", null),
                        tuple("SUP-1->PLANT-1", "unit_cost", "omitted", null),
                        tuple("SUP-1->PLANT-1", "failure_prob", "omitted", null));
        assertThat(r.l2().perField().get(Field.NODE_REGION.key()).get("stated")).isEqualTo(5);
    }

    @Test
    void decoysAreAbsorbedAsEntitiesAndAsValues() {
        Map<String, String> b = sample(NO_UNITS);
        List<Decoy> decoys = List.of(
                new Decoy(1, "Until 2019, ", "node", "DC-9", "exists", "DC", null, true, 12),
                new Decoy(2, "Under the proposed expansion, ", "node", "PLANT-1", "capacity", "600", "DAY", false,
                        13),
                new Decoy(3, "In the competitor's network, ", "link", "DC-2->CUST-1", "exists", "true", null, true, 14),
                new Decoy(4, "Before the 2021 restructuring, ", "node", "DC-1", "region", "EU-West", null, false, 15),
                new Decoy(5, "Until 2020, ", "node", "Lyon", "exists", "DC", null, true, 16));
        Map<String, String> c = addRow(b, Sheet.NODES, Map.of(Schema.NODE_NAME, "DC-9", Schema.NODE_TYPE, "DC"));
        c = addRow(c, Sheet.LINKS, Map.of(Schema.LINK_SOURCE, "DC-2", Schema.LINK_TARGET, "CUST-1"));
        c = set(c, Sheet.NODES, "PLANT-1", Schema.NODE_CAPACITY_VALUE, "600");
        c = set(c, Sheet.NODES, "DC-1", Schema.NODE_REGION, "eu-west");
        Report r = CsvEdits.verify(b, c, null, decoys);

        assertThat(r.decoys().total()).isEqualTo(5);
        assertThat(r.decoys().entitiesAbsorbed()).containsExactly("DC-9", "DC-2->CUST-1");
        assertThat(r.decoys().valuesAbsorbed())
                .extracting(Report.DecoyValue::entity, Report.DecoyValue::field, Report.DecoyValue::sentence)
                .containsExactly(tuple("PLANT-1", "capacity", 13), tuple("DC-1", "region", 15));
        assertThat(r.l2().mismatches())
                .extracting(Report.Mismatch::entity, Report.Mismatch::field, Report.Mismatch::cls,
                        Report.Mismatch::sentence)
                .containsExactly(tuple("PLANT-1", "capacity", "decoy_absorbed", null),
                        tuple("DC-1", "region", "decoy_absorbed", null),
                        tuple("DC-9", "exists", "fabricated", 12),
                        tuple("DC-2->CUST-1", "exists", "fabricated", 14));
        assertThat(r.fabricatedCount()).isEqualTo(2);
        assertThat(count(r, Field.NODE_CAPACITY, FactClass.DECOY_ABSORBED)).isEqualTo(1);
    }

    @Test
    void anEmptyCandidateScoresZeroWithoutThrowing() {
        Map<String, String> b = sample(UNITS);
        Report r = verify(b, new LinkedHashMap<>());

        assertThat(r.importValid()).isFalse();
        assertThat(r.structureF1()).isZero();
        assertThat(r.factRecall()).isZero();
        assertThat(r.factPrecision()).isZero();
        assertThat(r.ncs()).isZero();
        assertThat(r.fabricatedCount()).isZero();
        assertThat(r.l1().nodes().candidate()).isZero();
        assertThat(r.l1().nodes().precision()).isZero();
        assertThat(r.envelope().filesMissing()).hasSize(5);
        assertThat(r.l0().findings()).filteredOn(f -> f.code() == Code.FILE_MISSING && f.isError())
                .extracting(Finding::file)
                .containsExactly(Sheet.NODES.fileName(), Sheet.LINKS.fileName());
        assertThat(r.l2().mismatches()).allSatisfy(m -> assertThat(m.cls()).isEqualTo("omitted"));
        assertThat(r.toJson()).contains("\"import_valid\": false");
    }
}
