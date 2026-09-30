package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The flat tables of batch (index.csv) and verify-batch (results.csv): UTF-8, \n, comma, minimal RFC 4180 quoting, one cell per column in every row, no embedded newlines, numbers without exponents, booleans as 1/0. */
final class FlatCsv {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setRecordSeparator("\n").build();

    private FlatCsv() {
    }

    /** One cell's text. */
    static String cell(Object value) {
        String s;
        if (value == null) {
            s = "";
        } else if (value instanceof Boolean b) {
            s = b ? "1" : "0";
        } else if (value instanceof BigDecimal b) {
            s = b.toPlainString();
        } else if (value instanceof Double d) {
            s = BigDecimal.valueOf(d).toPlainString();
        } else if (value instanceof JsonNode n) {
            s = n.isNull() || n.isMissingNode() ? "" : n.isBoolean() ? (n.asBoolean() ? "1" : "0")
                    : n.isNumber() ? n.decimalValue().toPlainString()
                    : n.isValueNode() ? n.asText() : n.toString();
        } else {
            s = value.toString();
        }
        return s.replaceAll("\\s*[\\r\\n]+\\s*", " ");
    }

    /** Writes the header and the rows (each as wide as the header), creating the parent directory. */
    static void write(Path file, List<String> header, List<List<String>> rows) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, FORMAT)) {
            printer.printRecord(header);
            for (List<String> row : rows) {
                if (row.size() != header.size()) {
                    throw new IllegalStateException("row of " + row.size() + " cells under " + header.size()
                            + " columns: " + row);
                }
                printer.printRecord(row);
            }
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, sw.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
