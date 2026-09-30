package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.validate.ValidationResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** the reader is lenient in transport and keeps physical line numbers. */
class LenientCsvReaderTest {

    /** A UTF-8 byte-order mark, spelled without a literal so no tool can mangle it. */
    private static final String BOM = "" + (char) 0xFEFF;

    private static RawTable products(String content) {
        return LenientCsvReader.readTable(Sheet.PRODUCTS, "products.csv", content);
    }

    private static RawTable nodes(String content) {
        return LenientCsvReader.readTable(Sheet.NODES, "nodes.csv", content);
    }

    private static List<List<String>> cells(RawTable t) {
        return t.rows().stream().map(RawTable.Row::cells).toList();
    }

    private static List<Integer> lines(RawTable t) {
        return t.rows().stream().map(RawTable.Row::line).toList();
    }

    /** Only the given sheet: the network-level findings about everything else are filtered out. */
    private static List<Finding> findings(String fileName, String content, Code code) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(fileName, content);
        ValidationResult r = SnrmRules.validate(LenientCsvReader.read(files));
        return r.findings().stream().filter(f -> f.code() == code).toList();
    }

    @Test
    void stripsAUtf8Bom() {
        RawTable t = products(BOM + "name,unit_value\nWIDGET-A,25\n");
        assertThat(t.headers()).containsExactly("name", "unit_value");
        assertThat(t.has(Schema.PRODUCT_NAME)).isTrue();
        assertThat(t.unmappedHeaders()).isEmpty();
        assertThat(cells(t)).containsExactly(List.of("WIDGET-A", "25"));
    }

    @Test
    void acceptsCrLf() {
        RawTable t = products("name,unit_value\r\nWIDGET-A,25\r\nWIDGET-B,30\r\n");
        assertThat(t.headers()).containsExactly("name", "unit_value");
        assertThat(cells(t)).containsExactly(List.of("WIDGET-A", "25"), List.of("WIDGET-B", "30"));
        assertThat(lines(t)).containsExactly(2, 3);
    }

    @ParameterizedTest
    @ValueSource(strings = {";", "\t", "|"})
    void sniffsTheDelimiterOnTheHeaderLine(String d) {
        RawTable t = products("name" + d + "unit_value\nWIDGET, A" + d + "25\n");
        assertThat(t.headers()).containsExactly("name", "unit_value");
        assertThat(cells(t)).containsExactly(List.of("WIDGET, A", "25"));
    }

    @Test
    void matchesUpperCaseAndPaddedHeaders() {
        RawTable t = products(" NAME , Unit_Value \nWIDGET-A , 25 \n");
        assertThat(t.columnIndex()).containsExactly(Map.entry(Schema.PRODUCT_NAME, 0),
                Map.entry(Schema.PRODUCT_UNIT_VALUE, 1));
        assertThat(t.unmappedHeaders()).isEmpty();
        assertThat(cells(t)).containsExactly(List.of("WIDGET-A", "25"));
    }

    @Test
    void readsQuotedCellsWithCommasAndEmbeddedQuotes() {
        RawTable t = nodes("name,type,region,caption\nDC-1,DC,\"EU, North\",\"Nordic hub \"\"3PL\"\", 2 shifts\"\n");
        assertThat(cells(t)).containsExactly(List.of("DC-1", "DC", "EU, North", "Nordic hub \"3PL\", 2 shifts"));
        assertThat(t.rows().get(0).width()).isEqualTo(4);
    }

    @Test
    void anAbsentUnitColumnReadsAsNull() {
        RawTable t = nodes("name,type,capacity_value\nSUP-1,SUPPLIER,500\n");
        RawTable.Row row = t.rows().get(0);
        assertThat(t.has(Schema.NODE_CAPACITY_UNIT)).isFalse();
        assertThat(t.cell(row, Schema.NODE_CAPACITY_UNIT)).isNull();
        assertThat(t.cell(row, Schema.NODE_CAPACITY_VALUE)).isEqualTo("500");
    }

    @Test
    void skipsTrailingBlankAndWhitespaceOnlyLines() {
        RawTable t = products("name,unit_value\nWIDGET-A,25\n\n\n   \n\r\n");
        assertThat(cells(t)).containsExactly(List.of("WIDGET-A", "25"));
    }

    @Test
    void anExtraColumnIsUnmapped() {
        String content = "name,unit_value,notes\nWIDGET-A,25,best seller\n";
        RawTable t = products(content);
        assertThat(t.unmappedHeaders()).containsExactly("notes");
        assertThat(t.rows().get(0).width()).isEqualTo(3);
        assertThat(findings("products.csv", content, Code.UNMAPPED_COLUMN))
                .extracting(Finding::file, Finding::line, Finding::column)
                .containsExactly(tuple("products.csv", 1, "notes"));
    }

    @Test
    void aShortRowIsPaddedAndALongRowTruncated() {
        String content = "name,unit_value\nWIDGET-A\nWIDGET-B,30,STRAY\n";
        RawTable t = products(content);
        assertThat(cells(t)).containsExactly(List.of("WIDGET-A", ""), List.of("WIDGET-B", "30"));
        assertThat(t.rows()).extracting(RawTable.Row::width).containsExactly(1, 3);
        assertThat(findings("products.csv", content, Code.ROW_WIDTH_MISMATCH))
                .extracting(Finding::file, Finding::line)
                .containsExactly(tuple("products.csv", 2), tuple("products.csv", 3));
    }

    @Test
    void lineNumbersArePhysicalAfterBlankLines() {
        RawTable lf = products("\n\nname,unit_value\n\nWIDGET-A,25\n\n\nWIDGET-B,30\n");
        assertThat(lf.headerLine()).isEqualTo(3);
        assertThat(lines(lf)).containsExactly(5, 8);
        RawTable crlf = products("name,unit_value\r\n\r\nWIDGET-A,25\r\n  \r\nWIDGET-B,30\r\n");
        assertThat(crlf.headerLine()).isEqualTo(1);
        assertThat(lines(crlf)).containsExactly(3, 5);
    }

    @Test
    void lineNumbersArePhysicalAfterAQuotedCellSpanningTwoLines() {
        RawTable t = nodes("name,type,caption\nDC-1,DC,\"first line\nsecond line\"\nDC-2,DC,x\n");
        assertThat(lines(t)).containsExactly(2, 4);
        assertThat(t.rows().get(0).cells().get(2)).isEqualTo("first line\nsecond line");
    }

    /** An empty file (an empty block in a response) has no columns; its missing header is line 1. */
    @ParameterizedTest
    @ValueSource(strings = {"", BOM, "\n\n", "   \r\n"})
    void anEmptyFileHasNoColumnsAndItsHeaderOnLine1(String content) {
        RawTable t = nodes(content);
        assertThat(t.headers()).isEmpty();
        assertThat(t.rows()).isEmpty();
        assertThat(t.headerLine()).isEqualTo(1);
        assertThat(findings("nodes.csv", content, Code.MISSING_REQUIRED_COLUMN))
                .extracting(Finding::file, Finding::line, Finding::column)
                .containsExactly(tuple("nodes.csv", 1, "name"), tuple("nodes.csv", 1, "type"));
    }

    @Test
    void fileNamesMatchCaseInsensitively() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Nodes.CSV", "name,type\nSUP-1,SUPPLIER\n");
        files.put("LINKS.csv", "source,target\n");
        files.put("assumptions.csv", "a,b\n");
        RawNetwork net = LenientCsvReader.read(files);
        assertThat(net.tables()).containsOnlyKeys(Sheet.NODES, Sheet.LINKS);
        assertThat(net.table(Sheet.NODES).orElseThrow().fileName()).isEqualTo("Nodes.CSV");
        assertThat(net.unrecognisedFiles()).containsExactly("assumptions.csv");
    }

    @Test
    void readDirectoryFindsUpperCaseExtensions(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Nodes.CSV"), "name,type\nSUP-1,SUPPLIER\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("notes.txt"), "not a csv", StandardCharsets.UTF_8);
        RawNetwork net = LenientCsvReader.readDirectory(dir);
        assertThat(net.tables()).containsOnlyKeys(Sheet.NODES);
        assertThat(net.unrecognisedFiles()).isEmpty();
    }

    /** An Excel-style export of minimal-6-node (BOM, CRLF, ';', upper-case headers, quotes where a cell holds a ';' — PLANT-1's caption does) validates exactly like the original. */
    @Test
    void anExcelStyleCopyOfMinimal6NodeImportsClean() throws IOException {
        CSVFormat excel = CSVFormat.DEFAULT.builder().setDelimiter(';').setRecordSeparator("\r\n").build();
        Map<String, String> files = new LinkedHashMap<>();
        try (var list = Files.list(TestSupport.sample("minimal-6-node"))) {
            for (Path p : list.sorted().toList()) {
                StringBuilder sb = new StringBuilder(BOM);
                try (CSVParser in = CSVParser.parse(Files.readString(p, StandardCharsets.UTF_8), CSVFormat.DEFAULT);
                     CSVPrinter out = new CSVPrinter(sb, excel)) {
                    for (CSVRecord rec : in) {
                        out.printRecord(rec.getRecordNumber() == 1
                                ? rec.stream().map(h -> h.toUpperCase(Locale.ROOT)).toList()
                                : rec.toList());
                    }
                }
                files.put(p.getFileName().toString(), sb.toString());
            }
        }
        assertThat(files.get("nodes.csv")).contains("\r\n", ";\"Two lines; second one idle since Q1\";");
        ValidationResult r = SnrmRules.validate(LenientCsvReader.read(files));
        assertThat(r.findings()).isEmpty();
    }
}
