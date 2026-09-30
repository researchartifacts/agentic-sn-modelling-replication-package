package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.UnitTokens;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Normaliser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Normaliser() {
    }

    /** The normalised form of a name or enum cell; "" for null or blank. */
    public static String name(String cell) {
        if (cell == null) {
            return "";
        }
        String t = cell.trim();
        if (t.length() >= 2) {
            char first = t.charAt(0);
            if ((first == '"' || first == '\'') && t.charAt(t.length() - 1) == first) {
                t = t.substring(1, t.length() - 1);
            }
        }
        return WHITESPACE.matcher(t).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    /** The unit a *_unit cell resolves to: a blank or absent cell means the network's own period unit. */
    public static Optional<TimeUnit> unit(String cell, TimeUnit periodUnit) {
        if (cell == null || cell.isBlank()) {
            return Optional.ofNullable(periodUnit);
        }
        return UnitTokens.parse(cell);
    }

    /** A node type cell, case-insensitive and trimmed; empty when blank or not a type. */
    public static Optional<NodeType> nodeType(String cell) {
        String s = name(cell).toUpperCase(Locale.ROOT);
        for (NodeType t : NodeType.values()) {
            if (t.name().equals(s)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
