package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The deterministic twin of a network: every table rewritten from its RawTable with the same file name, the same headers and the same cells, except that every cell of a failure_prob column. */
public final class DeterministicTwin {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setRecordSeparator("\n").build();
    private static final Set<Column> FAILURE_PROB = Set.of(Schema.NODE_FAILURE_PROB, Schema.LINK_FAILURE_PROB);

    private DeterministicTwin() {
    }

    /** The twin's files (file name as found → CSV text), in sheet order. */
    public static Map<String, String> of(RawNetwork network) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            network.table(s).ifPresent(t -> files.put(t.fileName(), render(t)));
        }
        return files;
    }

    private static String render(RawTable t) {
        if (t.headers().isEmpty()) {
            return "";
        }
        List<Integer> zeroed = new ArrayList<>();
        for (int i = 0; i < t.headers().size(); i++) {
            if (Schema.column(t.sheet(), t.headers().get(i)).filter(FAILURE_PROB::contains).isPresent()) {
                zeroed.add(i);
            }
        }
        StringBuilder sb = new StringBuilder();
        try (CSVPrinter printer = new CSVPrinter(sb, FORMAT)) {
            printer.printRecord(t.headers());
            for (RawTable.Row row : t.rows()) {
                List<String> cells = new ArrayList<>(row.cells());
                zeroed.forEach(i -> cells.set(i, "0"));
                printer.printRecord(cells);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sb.toString();
    }
}
