package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One sheet as read, before any validation: trimmed header cells, the canonical column each maps to, and rows whose cells are already aligned to the header width. */
public final class RawTable {

    /** One data row. */
    public record Row(int line, List<String> cells, int width) {
        public Row {
            cells = List.copyOf(cells);
        }
    }

    private final Sheet sheet;
    private final String fileName;
    private final int headerLine;
    private final List<String> headers;
    private final List<Row> rows;
    private final Map<Column, Integer> columnIndex;
    private final List<String> unmappedHeaders;

    public RawTable(Sheet sheet, String fileName, int headerLine, List<String> headers, List<Row> rows) {
        this.sheet = sheet;
        this.fileName = fileName;
        this.headerLine = headerLine;
        this.headers = List.copyOf(headers);
        this.rows = List.copyOf(rows);
        Map<Column, Integer> index = new LinkedHashMap<>();
        List<String> unmapped = new ArrayList<>();
        for (int i = 0; i < this.headers.size(); i++) {
            Optional<Column> c = Schema.column(sheet, this.headers.get(i));
            if (c.isPresent() && !index.containsKey(c.get())) {
                index.put(c.get(), i);
            } else {
                unmapped.add(this.headers.get(i));
            }
        }
        this.columnIndex = Collections.unmodifiableMap(index);
        this.unmappedHeaders = List.copyOf(unmapped);
    }

    public Sheet sheet() {
        return sheet;
    }

    public String fileName() {
        return fileName;
    }

    public int headerLine() {
        return headerLine;
    }

    public List<String> headers() {
        return headers;
    }

    public List<Row> rows() {
        return rows;
    }

    /** Canonical column → header index; the first header naming a column claims it. */
    public Map<Column, Integer> columnIndex() {
        return columnIndex;
    }

    /** Header cells that map to no canonical column (or repeat one already claimed), in order. */
    public List<String> unmappedHeaders() {
        return unmappedHeaders;
    }

    public boolean has(Column column) {
        return columnIndex.containsKey(column);
    }

    /** The cell of a column: null when the column is absent, "" when the cell is blank. */
    public String cell(Row row, Column column) {
        Integer i = columnIndex.get(column);
        return i == null ? null : row.cells().get(i);
    }
}
