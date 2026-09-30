package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.schema.Sheet;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A directory (or split response) as read: the canonical sheets that were present, and the names of CSV files that are not canonical sheets. */
public record RawNetwork(Map<Sheet, RawTable> tables, List<String> unrecognisedFiles) {

    public RawNetwork {
        EnumMap<Sheet, RawTable> copy = new EnumMap<>(Sheet.class);
        copy.putAll(tables);
        tables = Collections.unmodifiableMap(copy);
        unrecognisedFiles = List.copyOf(unrecognisedFiles);
    }

    public Optional<RawTable> table(Sheet sheet) {
        return Optional.ofNullable(tables.get(sheet));
    }

    public boolean has(Sheet sheet) {
        return tables.containsKey(sheet);
    }
}
