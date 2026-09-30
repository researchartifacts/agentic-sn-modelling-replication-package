package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.verify.CsvEdits;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 's order of preference and the handling of marker lines that name no file. */
class ResponseSplitterPrecedenceTest {

    private static final Map<String, String> FILES = lf(CsvEdits.sample("minimal-6-node"));

    private static Map<String, String> lf(Map<String, String> files) {
        Map<String, String> out = new LinkedHashMap<>();
        files.forEach((k, v) -> out.put(k, v.replace("\r\n", "\n")));
        return out;
    }

    private static String headings() {
        StringBuilder sb = new StringBuilder();
        for (Sheet s : Sheet.values()) {
            sb.append("### ").append(s.fileName()).append("\n```csv\n").append(FILES.get(s.fileName()))
                    .append("```\n\n");
        }
        return sb.toString();
    }

    private static void assertAllFive(ResponseSplitter.Split split) {
        assertThat(split.findings()).isEmpty();
        for (Sheet s : Sheet.values()) {
            assertThat(split.files().get(s.fileName())).as(s.fileName()).isEqualTo(FILES.get(s.fileName()));
        }
    }

    @Test
    void aDecorativeSeparatorDoesNotSwallowTheResponse() {
        assertAllFive(ResponseSplitter.split("=== Output ===\n" + headings()));
        assertAllFive(ResponseSplitter.split("---------- Answer ----------\n\n" + headings() + "=== END ===\n"));
    }

    @Test
    void anEndMarkerOnlyClosesItsSection() {
        String text = "=== FILE: nodes.csv ===\n" + FILES.get("nodes.csv") + "=== END ===\n"
                + "And the links:\n```\n" + FILES.get("links.csv") + "```\n";
        ResponseSplitter.Split split = ResponseSplitter.split(text);
        assertThat(split.files().get("nodes.csv")).isEqualTo(FILES.get("nodes.csv"));
        assertThat(split.files().get("links.csv")).isEqualTo(FILES.get("links.csv"));
        assertThat(split.findings()).isEmpty();
    }

    @Test
    void anEarlierUnlabelledHeaderFenceDoesNotBeatTheLabelledBlock() {
        String text = "The nodes file uses this header:\n```\nname,type\n```\n\n" + headings();
        assertAllFive(ResponseSplitter.split(text));
    }

    @Test
    void anUnlabelledEchoAfterTheLabelledBlockIsIgnoredWithoutAnError() {
        String text = headings() + "For reference, the nodes again:\n```csv\n" + FILES.get("nodes.csv") + "```\n";
        assertAllFive(ResponseSplitter.split(text));
    }

    @Test
    void twoLabelledBlocksForOneFileAreStillAnError() {
        String text = headings() + "### nodes.csv\n```csv\nname,type\nX,DC\n```\n";
        ResponseSplitter.Split split = ResponseSplitter.split(text);
        assertThat(split.files().get("nodes.csv")).isEqualTo(FILES.get("nodes.csv"));
        assertThat(split.findings()).hasSize(1);
    }

    @Test
    void aVeryLongDashOrSpaceLineIsSplitQuickly() {
        String dashes = "-".repeat(20_000) + "x\n";
        String heading = "# a" + " ".repeat(20_000) + "b\n";
        long start = System.nanoTime();
        ResponseSplitter.split(dashes + heading + headings());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(ms).as("split took %d ms", ms).isLessThan(2_000);
    }
}
