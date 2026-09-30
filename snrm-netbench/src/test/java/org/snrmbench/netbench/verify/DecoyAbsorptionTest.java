package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** end to end: a candidate that believes an adversarial sentence is caught absorbing its decoy. */
class DecoyAbsorptionTest {

    private static Generator.Generated adversarial(long seed) {
        GenerateParams d = GenerateParams.defaults(seed, 30, 4, "Widget");
        return Generator.build(new GenerateParams(seed, 30, 4, "Widget", 1, d.naming(), d.style(), d.order(),
                d.units(), d.periodUnit(), 1, 90, 1, 2, 0, d.attributeCoverage(), GenerateParams.NoiseType.ADVERSARIAL,
                new BigDecimal("0.6"), false, d.distanceMode(), d.locale()), null);
    }

    private static Map<String, String> truth(Generator.Generated g) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            files.put(s.fileName(), g.files().get("truth/" + s.fileName()));
        }
        return files;
    }

    @Test
    void aTrueCandidateAbsorbsNothing() {
        Generator.Generated g = adversarial(1);
        Report r = CsvEdits.verify(truth(g), truth(g), g.facts(), g.description().decoys());
        assertThat(r.decoys().total()).isEqualTo(g.description().decoys().size()).isPositive();
        assertThat(r.decoys().entitiesAbsorbed()).isEmpty();
        assertThat(r.decoys().valuesAbsorbed()).isEmpty();
        assertThat(r.factRecall()).isEqualTo(1.0);
    }

    @Test
    void takingADecoyValueForARealEntityIsDecoyAbsorbed() {
        for (long seed = 1; seed <= 20; seed++) {
            Generator.Generated g = adversarial(seed);
            Decoy d = g.description().decoys().stream()
                    .filter(x -> !x.decoyEntity() && x.kind().equals("link") && x.field().equals("capacity"))
                    .findFirst().orElse(null);
            if (d == null) {
                continue;
            }
            Map<String, String> c = CsvEdits.set(truth(g), Sheet.LINKS, d.entity(), Schema.LINK_CAPACITY_VALUE,
                    d.value());
            Report r = CsvEdits.verify(truth(g), c, g.facts(), g.description().decoys());
            assertThat(r.decoys().valuesAbsorbed()).extracting(Report.DecoyValue::entity).containsExactly(d.entity());
            assertThat(r.l2().mismatches()).extracting(Report.Mismatch::cls).containsExactly("decoy_absorbed");
            assertThat(r.l2().perField().get(Field.LINK_CAPACITY.key()).get("decoy_absorbed")).isEqualTo(1);
            return;
        }
        throw new AssertionError("no seed produced a link-capacity decoy");
    }

    @Test
    void addingADecoyNodeIsAnAbsorbedDecoyEntity() {
        for (long seed = 1; seed <= 20; seed++) {
            Generator.Generated g = adversarial(seed);
            Decoy d = g.description().decoys().stream()
                    .filter(x -> x.decoyEntity() && x.kind().equals("node")).findFirst().orElse(null);
            if (d == null) {
                continue;
            }
            Map<Column, String> row = Map.of(Schema.NODE_NAME, d.entity(), Schema.NODE_TYPE, d.value());
            Report r = CsvEdits.verify(truth(g), CsvEdits.addRow(truth(g), Sheet.NODES, row), g.facts(),
                    g.description().decoys());
            assertThat(r.decoys().entitiesAbsorbed()).containsExactly(d.entity());
            assertThat(r.l2().fabricated().get("nodes")).containsExactly(d.entity());
            assertThat(r.l2().mismatches()).extracting(Report.Mismatch::cls).containsExactly("fabricated");
            assertThat(r.l2().mismatches().get(0).sentence()).isEqualTo(d.sentence());
            assertThat(g.description().decoyEntities()).contains(d.entity());
            assertThat(List.of(d.value())).containsAnyOf("SUPPLIER", "PLANT", "DC", "CUSTOMER");
            return;
        }
        throw new AssertionError("no seed produced a decoy node");
    }
}
