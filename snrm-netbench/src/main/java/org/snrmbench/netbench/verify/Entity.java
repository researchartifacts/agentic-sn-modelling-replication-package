package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Sheet;

import java.util.List;

/** One scored entity: its key as written (name, source->target, node|product), its normalised key (what matching compares) and the row it was read from. */
public record Entity(Sheet sheet, String key, List<String> norm, RawTable table, RawTable.Row row) {

    public Entity {
        norm = List.copyOf(norm);
    }

    /** The trimmed cell of a column; "" when the cell is blank or the column is absent. */
    public String cell(Column column) {
        String v = table.cell(row, column);
        return v == null ? "" : v;
    }
}
