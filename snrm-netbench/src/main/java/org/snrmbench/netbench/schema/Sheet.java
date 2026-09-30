package org.snrmbench.netbench.schema;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The five canonical SNRM sheets, in the order files are listed and written. */
public enum Sheet {
    NETWORK_META("network_meta", "meta"),
    NODES("nodes", "node"),
    LINKS("links", "link"),
    PRODUCTS("products", "product"),
    NODE_PRODUCTS("node_products", "node_product");

    private final String id;
    private final String factKind;

    Sheet(String id, String factKind) {
        this.id = id;
        this.factKind = factKind;
    }

    /** The sheet name, e.g. {@code nodes}. */
    public String id() {
        return id;
    }

    /** The file name, e.g. {@code nodes.csv}. */
    public String fileName() {
        return id + ".csv";
    }

    /** The {@code kind} this sheet's entities carry in the fact ledger. */
    public String factKind() {
        return factKind;
    }

    /** Every column of the sheet, in the canonical order. */
    public List<Column> columns() {
        return Schema.columns(this);
    }

    /** Matches a file name case-insensitively ({@code Nodes.CSV} is {@code nodes}). */
    public static Optional<Sheet> byFileName(String fileName) {
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        for (Sheet s : values()) {
            if (s.fileName().equals(lower)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    public static Optional<Sheet> byFactKind(String kind) {
        for (Sheet s : values()) {
            if (s.factKind.equals(kind)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
