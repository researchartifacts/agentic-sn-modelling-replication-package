package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.verify.CsvEdits;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** the response shapes the splitter recognises, and how it reports what it cannot use. */
class ResponseSplitterTest {

    /** A UTF-8 byte-order mark, spelled without a literal so no tool can mangle it. */
    private static final String BOM = "" + (char) 0xFEFF;

    /** minimal-6-node's five files with LF line endings (the splitter's output form). */
    private static final Map<String, String> FILES = lf(CsvEdits.sample("minimal-6-node"));

    private static Map<String, String> lf(Map<String, String> files) {
        Map<String, String> out = new LinkedHashMap<>();
        files.forEach((k, v) -> out.put(k, v.replace("\r\n", "\n")));
        return out;
    }

    private static String body(Sheet s) {
        return FILES.get(s.fileName());
    }

    private static List<String> canonicalNames() {
        return Arrays.stream(Sheet.values()).map(Sheet::fileName).toList();
    }

    private static void assertAllFive(ResponseSplitter.Split split) {
        assertThat(split.findings()).isEmpty();
        assertThat(split.files().keySet()).containsExactlyElementsOf(canonicalNames());
        for (Sheet s : Sheet.values()) {
            assertThat(split.files().get(s.fileName())).as(s.fileName()).isEqualTo(body(s));
        }
    }

    @Test
    void headingsWithFencesInAnyOrder() {
        StringBuilder sb = new StringBuilder();
        List<Sheet> order = List.of(Sheet.NODE_PRODUCTS, Sheet.NODES, Sheet.NETWORK_META, Sheet.LINKS, Sheet.PRODUCTS);
        for (Sheet s : order) {
            sb.append("### ").append(s.fileName()).append("\n```csv\n").append(body(s)).append("```\n\n");
        }
        ResponseSplitter.Split split = ResponseSplitter.split(sb.toString());

        assertAllFive(split); // in canonical order, whatever the response order
    }

    @Test
    void fileMarkersOfBothStyles() {
        String text = "=== FILE: network_meta.csv ===\n" + body(Sheet.NETWORK_META)
                + "=== FILE: nodes.csv ===\n\n" + body(Sheet.NODES) + "\n"
                + "--- links.csv ---\n```csv\n" + body(Sheet.LINKS) + "```\n"
                + "--- products.csv ---\n" + body(Sheet.PRODUCTS)
                + "=== FILE: NODE_PRODUCTS.CSV ===\n" + body(Sheet.NODE_PRODUCTS)
                + "=== END ===\nThat is all.\n";

        assertAllFive(ResponseSplitter.split(text));
    }

    @Test
    void fencesWithoutHeadingsAreIdentifiedByTheirHeaderRow() {
        StringBuilder sb = new StringBuilder();
        for (Sheet s : Sheet.values()) {
            String fence = s == Sheet.LINKS ? "~~~" : "```";
            sb.append(fence).append(s == Sheet.NODES ? "" : "csv").append('\n').append(body(s)).append(fence)
                    .append("\n\n");
        }
        ResponseSplitter.Split split = ResponseSplitter.split(sb.toString());

        assertAllFive(split); // nodes' header contains products' only required column, yet reads as nodes
    }

    @Test
    void proseAroundAndBetweenIsIgnored() {
        String text = "Here is the network you asked for.\n\n"
                + "## File: `network_meta.csv`\nThe time base:\n```\n" + body(Sheet.NETWORK_META) + "```\n"
                + "Next, the nodes.\n\n#### **NODES.CSV**\n```csv\n" + body(Sheet.NODES) + "```\n"
                + "### 3. links.csv:\n\n```csv\n" + body(Sheet.LINKS) + "```\n"
                + "## Products\nThe products are:\n```csv\n" + body(Sheet.PRODUCTS) + "```\n"
                + "### node_products.csv\n```csv\n" + body(Sheet.NODE_PRODUCTS) + "```\n"
                + "Let me know if anything should change.\n";

        assertAllFive(ResponseSplitter.split(text));
    }

    @Test
    void aMissingFileIsSimplyAbsent() {
        StringBuilder sb = new StringBuilder();
        for (Sheet s : List.of(Sheet.NETWORK_META, Sheet.NODES, Sheet.NODE_PRODUCTS)) {
            sb.append("### ").append(s.fileName()).append("\n```csv\n").append(body(s)).append("```\n");
        }
        ResponseSplitter.Split split = ResponseSplitter.split(sb.toString());

        assertThat(split.files().keySet()).containsExactly("network_meta.csv", "nodes.csv", "node_products.csv");
        assertThat(split.findings()).isEmpty(); // FILE_MISSING is the loader's finding
    }

    @Test
    void aDuplicatedFileKeepsTheFirstAndIsAParseError() {
        String second = body(Sheet.NODES).replace("SUP-1", "SUP-X");
        String text = "### nodes.csv\n```csv\n" + body(Sheet.NODES) + "```\n"
                + "### links.csv\n```csv\n" + body(Sheet.LINKS) + "```\n"
                + "### nodes.csv\n```csv\n" + second + "```\n";
        int secondLine = 1 + (body(Sheet.NODES).split("\n").length + 3) + (body(Sheet.LINKS).split("\n").length + 3);
        ResponseSplitter.Split split = ResponseSplitter.split(text);

        assertThat(split.files().get("nodes.csv")).isEqualTo(body(Sheet.NODES));
        assertThat(split.findings()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(Code.ENVELOPE_PARSE_ERROR);
            assertThat(f.isError()).isTrue();
            assertThat(f.file()).isEqualTo("nodes.csv");
            assertThat(f.message()).contains("response line " + secondLine + ":", "second time", "line 1 is used");
        });
    }

    @Test
    void aResponseWithNoFileIsAParseError() {
        ResponseSplitter.Split split = ResponseSplitter.split("I am sorry, I cannot produce that network.\n```\nx\n```\n");

        assertThat(split.files()).isEmpty();
        assertThat(split.findings()).extracting(Finding::code).containsExactly(Code.ENVELOPE_PARSE_ERROR);
    }

    @Test
    void nodeProductsIsNeverReadAsProducts() {
        String text = "### node_products.csv\n```csv\n" + body(Sheet.NODE_PRODUCTS) + "```\n";

        assertThat(ResponseSplitter.split(text).files().keySet()).containsExactly("node_products.csv");
    }

    @Test
    void anUnclosedFenceRunsToTheEnd() {
        String text = "### nodes.csv\n```csv\n" + body(Sheet.NODES);

        assertThat(ResponseSplitter.split(text).files().get("nodes.csv")).isEqualTo(body(Sheet.NODES));
    }

    @Test
    void crlfAndABomAreAccepted() {
        String text = BOM + "### products.csv\r\n```csv\r\n" + body(Sheet.PRODUCTS).replace("\n", "\r\n") + "```\r\n";

        assertThat(ResponseSplitter.split(text).files().get("products.csv")).isEqualTo(body(Sheet.PRODUCTS));
    }
}
