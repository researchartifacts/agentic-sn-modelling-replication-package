package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** on a generated B (mixed units, coverage 0.6, with its fact ledger): each mutation of B into C yields exactly the class the design names. */
class GeneratedClassificationTest {

    private static Generator.Generated g;
    private static Map<String, String> truth;
    private static List<Fact> facts;

    @BeforeAll
    static void generate() {
        GenerateParams d = GenerateParams.defaults(3, 25, 5, "Widget");
        GenerateParams p = new GenerateParams(3, 25, 5, "Widget", 2, d.naming(), d.style(), d.order(),
                GenerateParams.Units.MIXED, TimeUnit.DAY, 1, 90, 1, 2, 0, new BigDecimal("0.6"), d.noiseType(),
                d.noiseRatio(), false, d.distanceMode(), d.locale());
        g = Generator.build(p, null);
        truth = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            truth.put(s.fileName(), g.files().get("truth/" + s.fileName()));
        }
        facts = g.facts();
    }

    private static Report verify(Map<String, String> candidate) {
        return CsvEdits.verify(truth, candidate, facts, null);
    }

    /** The non-correct classifications, as "class entity.field". */
    private static List<String> mismatches(Report r) {
        return r.l2().mismatches().stream().map(m -> m.cls() + " " + m.entity() + "." + m.field()).toList();
    }

    private static List<String> codes(Report r) {
        return r.l0().findings().stream().map(Finding::code).map(Code::name).toList();
    }

    private static Fact fact(String kind, String entity, String field) {
        return facts.stream().filter(f -> f.kind().equals(kind) && f.entity().equals(entity)
                && f.field().equals(field)).findFirst().orElseThrow();
    }

    @Test
    void truthAgainstItselfIsPerfect() {
        Report r = verify(truth);
        assertThat(mismatches(r)).isEmpty();
        assertThat(r.factRecall()).isEqualTo(1.0);
        assertThat(r.factPrecision()).isEqualTo(1.0);
        assertThat(r.l2().statedFacts()).isEqualTo((int) facts.stream().filter(Fact::stated).count());
    }

    @Test
    void renamingANodesCaseStillMatches() {
        Report r = verify(CsvEdits.rename(truth, "PLT-01", "plt-01"));
        assertThat(mismatches(r)).isEmpty();
        assertThat(r.l1().nodes().recall()).isEqualTo(1.0);
    }

    @Test
    void reversingALinkIsWrongDirectionOnceAndNotFabricated() {
        Link l = g.truth().links().get(0);
        Map<String, String> c = CsvEdits.set(truth, Sheet.LINKS, l.key(), Schema.LINK_SOURCE, l.target());
        c = CsvEdits.set(c, Sheet.LINKS, l.target() + "->" + l.target(), Schema.LINK_TARGET, l.source());
        Report r = verify(c);
        assertThat(mismatches(r)).containsExactly("wrong_direction " + l.key() + ".exists");
        assertThat(r.fabricatedCount()).isZero();
        assertThat(codes(r)).contains("ECHELON_DIRECTION");
    }

    @Test
    void changingANodesTypeIsWrongType() {
        Node n = g.truth().nodes().stream().filter(x -> x.type() == NodeType.DC).findFirst().orElseThrow();
        Report r = verify(CsvEdits.set(truth, Sheet.NODES, n.name(), Schema.NODE_TYPE, "PLANT"));
        assertThat(mismatches(r)).contains("wrong_type " + n.name() + ".exists");
    }

    @Test
    void anEquivalentUnitIsCorrectAndTheSameNumberInAnotherUnitIsWrongUnit() {
        Link weekly = g.truth().links().stream().filter(l -> l.leadTime().unit() == TimeUnit.WEEK)
                .findFirst().orElseThrow(() -> new AssertionError("mixed units produced no weekly lead time"));
        String days = weekly.leadTime().value().multiply(BigDecimal.valueOf(7)).toPlainString();
        Map<String, String> c = CsvEdits.set(truth, Sheet.LINKS, weekly.key(), Schema.LINK_LEAD_VALUE, days);
        c = CsvEdits.set(c, Sheet.LINKS, weekly.key(), Schema.LINK_LEAD_UNIT, "days");
        assertThat(mismatches(verify(c))).isEmpty();

        Map<String, String> hours = CsvEdits.set(truth, Sheet.LINKS, weekly.key(), Schema.LINK_LEAD_UNIT, "h");
        assertThat(mismatches(verify(hours))).containsExactly("wrong_unit " + weekly.key() + ".lead_time");
    }

    @Test
    void aWeeklyPeriodIsOneWrongMetaFactAndEveryRateStaysCorrect() {
        Report r = verify(CsvEdits.set(truth, Sheet.NETWORK_META, null, Schema.META_PERIOD_UNIT, "WEEK"));
        assertThat(mismatches(r)).containsExactly("wrong_unit network_meta.period_length");
    }

    @Test
    void blankingAStatedCapacityIsOmitted() {
        Fact cap = facts.stream().filter(f -> f.kind().equals("node") && f.field().equals("capacity") && f.stated())
                .findFirst().orElseThrow();
        Report r = verify(CsvEdits.set(truth, Sheet.NODES, cap.entity(), Schema.NODE_CAPACITY_VALUE, ""));
        assertThat(mismatches(r)).containsExactly("omitted " + cap.entity() + ".capacity");
        assertThat(r.l2().mismatches().get(0).sentence()).isEqualTo(cap.sentence()).isNotNull();
        assertThat(r.factRecall()).isLessThan(1.0);
    }

    @Test
    void fillingAnUnstatedCostIsFilledUnstated() {
        Fact cost = facts.stream().filter(f -> f.field().equals("fixed_cost") && !f.stated()).findFirst()
                .orElseThrow(() -> new AssertionError("coverage 0.6 left every fixed cost stated"));
        Report r = verify(CsvEdits.set(truth, Sheet.NODES, cost.entity(), Schema.NODE_FIXED_COST, cost.value()));
        assertThat(mismatches(r)).containsExactly("filled_unstated " + cost.entity() + ".fixed_cost");
        assertThat(r.factRecall()).isEqualTo(1.0);
        assertThat(r.factPrecision()).isLessThan(1.0);
    }

    @Test
    void cappingACustomerIsAConventionViolation() {
        Node c = g.truth().nodes().stream().filter(x -> x.type() == NodeType.CUSTOMER).findFirst().orElseThrow();
        Report r = verify(CsvEdits.set(truth, Sheet.NODES, c.name(), Schema.NODE_CAPACITY_VALUE, "100"));
        assertThat(mismatches(r)).containsExactly("convention_violation " + c.name() + ".capacity");
        assertThat(r.conventionViolationCount()).isEqualTo(1);
    }

    @Test
    void addingANodeIsFabricated() {
        Report r = verify(CsvEdits.addRow(truth, Sheet.NODES,
                Map.of(Schema.NODE_NAME, "DCR-99", Schema.NODE_TYPE, "DC", Schema.NODE_REGION, "EU-West")));
        assertThat(mismatches(r)).containsExactly("fabricated DCR-99.exists");
        assertThat(r.l2().fabricatedFacts()).isEqualTo(2);
        assertThat(codes(r)).contains("ORPHAN_NODE");
    }

    @Test
    void droppingProductsIsImplicitCreationAndOmittedProductFacts() {
        Report r = verify(CsvEdits.without(truth, Sheet.PRODUCTS));
        assertThat(codes(r)).contains("PRODUCT_CREATED_IMPLICITLY", "FILE_MISSING");
        assertThat(r.importValid()).isTrue();
        assertThat(mismatches(r)).containsExactly(
                "omitted Widget-A.exists", "omitted Widget-A.unit_value",
                "omitted Widget-B.exists", "omitted Widget-B.unit_value");
    }

    @Test
    void droppingNetworkMetaDefaultsItAndOmitsTheFourMetaFacts() {
        Report r = verify(CsvEdits.without(truth, Sheet.NETWORK_META));
        assertThat(codes(r)).contains("NETWORK_META_DEFAULTED");
        assertThat(mismatches(r)).containsExactly("omitted network_meta.name", "omitted network_meta.period_length",
                "omitted network_meta.horizon_periods", "omitted network_meta.rounding_policy");
        assertThat(fact("meta", Fact.META_ENTITY, "name").stated()).isTrue();
    }
}
