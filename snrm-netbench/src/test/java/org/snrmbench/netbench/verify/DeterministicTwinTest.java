package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** step 3: a twin has every failure_prob at 0 and is otherwise the same network, cell for cell. */
class DeterministicTwinTest {

    /** Asserts the twin, read back, equals the original except that failure_prob cells are all "0". */
    private static void assertTwin(RawNetwork original) {
        Map<String, String> twin = DeterministicTwin.of(original);
        RawNetwork back = LenientCsvReader.read(twin);

        assertThat(twin.keySet()).containsExactlyElementsOf(
                original.tables().values().stream().map(RawTable::fileName).toList());
        for (Sheet s : Sheet.values()) {
            RawTable a = original.table(s).orElse(null);
            RawTable b = back.table(s).orElse(null);
            if (a == null) {
                assertThat(b).isNull();
                continue;
            }
            assertThat(b.headers()).isEqualTo(a.headers());
            assertThat(b.rows()).hasSameSizeAs(a.rows());
            Integer fp = s == Sheet.NODES ? a.columnIndex().get(Schema.NODE_FAILURE_PROB)
                    : s == Sheet.LINKS ? a.columnIndex().get(Schema.LINK_FAILURE_PROB) : null;
            for (int r = 0; r < a.rows().size(); r++) {
                List<String> was = a.rows().get(r).cells();
                List<String> now = b.rows().get(r).cells();
                for (int c = 0; c < was.size(); c++) {
                    assertThat(now.get(c)).as("%s row %d column %s", s, r, a.headers().get(c))
                            .isEqualTo(fp != null && fp == c ? "0" : was.get(c));
                }
            }
        }
    }

    @Test
    void aGeneratedTruthKeepsEveryCellButFailureProbability() {
        Generator.Generated g = Generator.build(GenerateParams.defaults(5, 12, 4, "Widget"), null);
        Map<String, String> truth = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            truth.put(s.fileName(), g.files().get(Generator.TRUTH_DIR + "/" + s.fileName()));
        }
        RawNetwork b = LenientCsvReader.read(truth);
        assertThat(truth.get("links.csv")).containsPattern(",0\\.0[1-9]\n"); // B does have non-zero probabilities

        assertTwin(b);
        // B is canonical CSV, so apart from failure_prob its twin is byte-identical
        Map<String, String> twin = DeterministicTwin.of(b);
        for (String f : List.of("network_meta.csv", "products.csv", "node_products.csv")) {
            assertThat(twin.get(f)).isEqualTo(truth.get(f));
        }
    }

    @Test
    void everySampleTwinsCleanly() {
        for (String sample : org.snrmbench.netbench.TestSupport.SAMPLES) {
            assertTwin(LenientCsvReader.readDirectory(org.snrmbench.netbench.TestSupport.sample(sample)));
        }
    }

    @Test
    void blankProbabilitiesBecomeZeroAndFileNamesAndHeadersAreKept() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Nodes.CSV", "Name ; TYPE ; Failure_Prob\nS ; SUPPLIER ; \nC ; CUSTOMER ; 0.5\n");
        files.put("links.csv", "source,target\nS,C\n");
        files.put("notes.csv", "anything\n");
        RawNetwork n = LenientCsvReader.read(files);

        Map<String, String> twin = DeterministicTwin.of(n);

        assertThat(twin.keySet()).isEqualTo(Set.of("Nodes.CSV", "links.csv"));
        assertThat(twin.get("Nodes.CSV")).isEqualTo("Name,TYPE,Failure_Prob\nS,SUPPLIER,0\nC,CUSTOMER,0\n");
        assertThat(twin.get("links.csv")).isEqualTo("source,target\nS,C\n");
    }
}
