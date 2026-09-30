package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.TestSupport;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** In-memory edits of a network held as file contents (file name to CSV text), so a test can mutate a bundled sample into a candidate. */
public final class CsvEdits {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setRecordSeparator("\n").build();

    private CsvEdits() {
    }

    /** The files of a bundled SNRM sample, in name order. */
    public static Map<String, String> sample(String name) {
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> list = Files.list(TestSupport.sample(name))) {
            for (Path p : list.sorted().toList()) {
                files.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    /** Verifies a candidate against a truth, both as file contents, with the default tolerance. */
    public static Report verify(Map<String, String> truth, Map<String, String> candidate) {
        return verify(truth, candidate, null, null);
    }

    public static Report verify(Map<String, String> truth, Map<String, String> candidate, List<Fact> facts,
                                List<Decoy> decoys) {
        return Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(truth),
                CandidateLoader.fromFiles(candidate, CandidateLoader.SOURCE_DIRECTORY), facts, decoys, null,
                new BigDecimal("1e-6"), "truth", "candidate"));
    }

    /** Sets one cell of the row with the given key, adding the column (blank elsewhere) when absent. */
    public static Map<String, String> set(Map<String, String> files, Sheet sheet, String key, Column column,
                                          String value) {
        return edit(files, sheet, rows -> {
            int col = columnIndex(rows, column);
            rows.get(rowIndex(rows, sheet, key)).set(col, value);
        });
    }

    /** Removes the row with the given key. */
    public static Map<String, String> removeRow(Map<String, String> files, Sheet sheet, String key) {
        return edit(files, sheet, rows -> rows.remove(rowIndex(rows, sheet, key)));
    }

    /** Appends a row; columns not given are blank, and given columns the sheet lacks are added. */
    public static Map<String, String> addRow(Map<String, String> files, Sheet sheet, Map<Column, String> cells) {
        return edit(files, sheet, rows -> {
            cells.keySet().forEach(c -> columnIndex(rows, c));
            List<String> row = new ArrayList<>();
            for (String h : rows.get(0)) {
                row.add(cells.entrySet().stream().filter(e -> e.getKey().name().equals(h))
                        .map(Map.Entry::getValue).findFirst().orElse(""));
            }
            rows.add(row);
        });
    }

    /** Replaces every key cell (node names, link ends, node-product nodes) equal to {@code from}. */
    public static Map<String, String> rename(Map<String, String> files, String from, String to) {
        Map<String, String> out = files;
        for (Sheet s : List.of(Sheet.NODES, Sheet.LINKS, Sheet.NODE_PRODUCTS)) {
            List<Column> nameColumns = s == Sheet.NODE_PRODUCTS ? List.of(Schema.NP_NODE) : Matcher.keyColumns(s);
            out = edit(out, s, rows -> {
                for (Column c : nameColumns) {
                    int col = columnIndex(rows, c);
                    for (List<String> r : rows.subList(1, rows.size())) {
                        if (r.get(col).equals(from)) {
                            r.set(col, to);
                        }
                    }
                }
            });
        }
        return out;
    }

    /** The files without one sheet. */
    public static Map<String, String> without(Map<String, String> files, Sheet sheet) {
        Map<String, String> out = new LinkedHashMap<>(files);
        out.remove(sheet.fileName());
        return out;
    }

    /** Applies an edit to the parsed rows (header first) of one sheet. */
    public static Map<String, String> edit(Map<String, String> files, Sheet sheet, Consumer<List<List<String>>> fn) {
        Map<String, String> out = new LinkedHashMap<>(files);
        List<List<String>> rows = parse(out.get(sheet.fileName()));
        fn.accept(rows);
        out.put(sheet.fileName(), print(rows));
        return out;
    }

    private static int rowIndex(List<List<String>> rows, Sheet sheet, String key) {
        if (sheet == Sheet.NETWORK_META) {
            return 1;
        }
        List<Integer> cols = Matcher.keyColumns(sheet).stream().map(c -> columnIndex(rows, c)).toList();
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (Matcher.key(sheet, cols.stream().map(r::get).toList()).equals(key)) {
                return i;
            }
        }
        throw new IllegalArgumentException(sheet.fileName() + " has no row " + key);
    }

    /** The index of a column, appended (blank in every row) when the header lacks it. */
    private static int columnIndex(List<List<String>> rows, Column column) {
        int i = rows.get(0).indexOf(column.name());
        if (i >= 0) {
            return i;
        }
        rows.forEach(r -> r.add(r == rows.get(0) ? column.name() : ""));
        return rows.get(0).size() - 1;
    }

    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser p = CSVParser.parse(new StringReader(text), CSVFormat.DEFAULT)) {
            for (CSVRecord r : p) {
                List<String> row = new ArrayList<>();
                r.forEach(row::add);
                rows.add(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }

    private static String print(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        try (CSVPrinter printer = new CSVPrinter(sb, FORMAT)) {
            for (List<String> r : rows) {
                printer.printRecord(r);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }
}
