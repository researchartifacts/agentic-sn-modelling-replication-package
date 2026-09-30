package org.snrmbench.netbench.schema;

import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.util.Json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The canonical CSV contract as a document, for netbench schema. */
public final class SchemaDoc {

    /** The durations that illustrate each rounding policy, in periods. */
    private static final List<BigDecimal> ROUNDING_EXAMPLES = List.of(new BigDecimal("1.25"), new BigDecimal("1.5"));

    /** The columns whose blank cell means "uncapped" (both are named {@code capacity_value}). */
    private static final List<Column> UNCAPPED_WHEN_BLANK = List.of(Schema.NODE_CAPACITY_VALUE,
            Schema.LINK_CAPACITY_VALUE);

    private static final String INTRO = "Five CSV files, comma-separated. Each starts with a header row naming every"
            + " column of the file below, lower-case, in the order shown; every further row is one record. `*` marks"
            + " a column SNRM requires; any other cell may be left blank. A number is plain digits with an optional"
            + " `.` fraction (`1200`, `0.05`): no thousands separators, and no units or other text in the cell.";
    private static final String DURATIONS = "Durations (the value is a length of time, measured in its unit):";
    private static final String RATES = "Rates (the unit is the time denominator: so many per one unit of time):";
    private static final String CONVENTIONS_INTRO = "Cells fixed by rule for the type of the row's node; a description"
            + " never states them:";
    private static final String ZERO_OR_BLANK = "`0` may also be left blank.";

    private SchemaDoc() {
    }

    /** One convention: the cell a column holds for every node of the listed types. */
    private record Convention(List<NodeType> types, Column column, Conventions.Value value) {
    }

    // ---- markdown ---------------------------------------------------------------------------

    /** The schema as Markdown, ending in a newline. */
    public static String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("## Files and columns\n\n").append(INTRO).append('\n');
        for (Sheet sheet : Sheet.values()) {
            md.append("\n### ").append(sheet.fileName()).append("\n\n");
            md.append("| Column | Required | Kind | Meaning |\n|---|---|---|---|\n");
            for (Column c : sheet.columns()) {
                row(md, code(c.name()), c.required() ? "*" : "", kindWord(c.kind()), c.note());
            }
        }

        md.append("\n### Node types\n\n").append(code(Schema.NODE_TYPE.name())).append(" is one of:\n\n");
        md.append("| Value | Echelon rank |\n|---|---|\n");
        for (NodeType t : NodeType.values()) {
            row(md, code(t.name()), Integer.toString(t.rank()));
        }

        md.append("\n### Time units\n\nA `*_unit` cell holds one of these units, written as its name or as any of its"
                + " tokens (case-insensitive):\n\n");
        md.append("| Unit | Seconds | Tokens |\n|---|---|---|\n");
        for (Map.Entry<TimeUnit, List<String>> e : UnitTokens.table().entrySet()) {
            row(md, code(e.getKey().name()), secondsText(e.getKey()),
                    e.getValue().stream().map(SchemaDoc::code).collect(Collectors.joining(", ")));
        }

        md.append("\n### Rounding policies\n\n").append(code(Schema.META_ROUNDING.name())).append(" is one of:\n\n");
        md.append("| Value | Durations are rounded");
        ROUNDING_EXAMPLES.forEach(x -> md.append(" | ").append(x.toPlainString()).append(" periods become"));
        md.append(" |\n|---|---|").append("---|".repeat(ROUNDING_EXAMPLES.size())).append('\n');
        for (RoundingPolicy p : RoundingPolicy.values()) {
            List<String> cells = new ArrayList<>(List.of(code(p.name()), p.phrase()));
            ROUNDING_EXAMPLES.forEach(x -> cells.add(p.round(x).toPlainString()));
            row(md, cells.toArray(String[]::new));
        }

        md.append("\n### Units\n\n");
        md.append("- ").append(DURATIONS).append('\n');
        unitPairs(true).forEach(p -> md.append("  - ").append(p.value().sheet().fileName()).append(": ")
                .append(code(p.value().name())).append(" in ").append(code(p.unit().name())).append('\n'));
        md.append("- ").append(RATES).append('\n');
        unitPairs(false).forEach(p -> md.append("  - ").append(p.value().sheet().fileName()).append(": ")
                .append(code(p.value().name())).append(" per ").append(code(p.unit().name())).append('\n'));
        md.append("- ").append(blankUnitRule()).append('\n');
        md.append("- ").append(uncappedRule()).append('\n');

        md.append("\n### Conventions\n\n").append(CONVENTIONS_INTRO).append("\n\n");
        md.append("| Node type | File | Column | Value |\n|---|---|---|---|\n");
        for (Convention c : conventions()) {
            String value = conventionValue(c.value());
            row(md, c.types().stream().map(t -> code(t.name())).collect(Collectors.joining(", ")),
                    c.column().sheet().fileName(), code(c.column().name()),
                    c.value() == Conventions.Value.BLANK ? value : code(value));
        }
        md.append('\n').append(ZERO_OR_BLANK).append(' ').append(conventionUnitRule()).append('\n');
        return md.toString();
    }

    private static void row(StringBuilder md, String... cells) {
        md.append("| ").append(String.join(" | ", cells)).append(" |\n");
    }

    private static String code(String s) {
        return "`" + s + "`";
    }

    /** The kind of a column as the Markdown names it. */
    private static String kindWord(Column.Kind kind) {
        return switch (kind) {
            case TEXT -> "text";
            case NODE_TYPE -> "node type";
            case ROUNDING_POLICY -> "rounding policy";
            case TIME_UNIT -> "time unit";
            case INTEGER, DECIMAL2, NUMBER -> "number";
            case BOOLEAN -> "true/false";
        };
    }

    /** {@code 604800 (7 days)}: the length in seconds, and in days for units longer than a day. */
    private static String secondsText(TimeUnit u) {
        long day = TimeUnit.DAY.seconds();
        String s = Long.toString(u.seconds());
        return u.seconds() > day && u.seconds() % day == 0 ? s + " (" + u.seconds() / day + " days)" : s;
    }

    // ---- content shared by both formats -----------------------------------------------------

    private static List<Schema.UnitPair> unitPairs(boolean duration) {
        return Schema.unitPairs().stream().filter(p -> p.duration() == duration).toList();
    }

    private static String blankUnitRule() {
        return "A missing or blank `*_unit` column or cell means the period unit ("
                + code(Schema.META_PERIOD_UNIT.name()) + ").";
    }

    private static String uncappedRule() {
        String files = UNCAPPED_WHEN_BLANK.stream().map(c -> c.sheet().fileName()).collect(Collectors.joining(", "));
        return "A blank " + code(UNCAPPED_WHEN_BLANK.get(0).name()) + " (" + files + ") means uncapped.";
    }

    private static String conventionUnitRule() {
        return "The unit cell next to a convention value holds the period unit ("
                + code(Schema.META_PERIOD_UNIT.name()) + ").";
    }

    /** {@code blank} or {@code 0}. */
    private static String conventionValue(Conventions.Value v) {
        return v == Conventions.Value.BLANK ? "blank" : "0";
    }

    /** Markdown prose as a JSON string: the same text without code backticks. */
    private static String plain(String markdown) {
        return markdown.replace("`", "");
    }

    /** The conventions of every field, in field order; the node types that share a field's convention are listed together, in type order. */
    private static List<Convention> conventions() {
        List<Convention> out = new ArrayList<>();
        for (Field f : Field.values()) {
            for (Conventions.Value v : Conventions.Value.values()) {
                List<NodeType> types = new ArrayList<>();
                for (NodeType t : NodeType.values()) {
                    if (Conventions.of(f, t).orElse(null) == v) {
                        types.add(t);
                    }
                }
                if (!types.isEmpty()) {
                    out.add(new Convention(List.copyOf(types), f.column(), v));
                }
            }
        }
        return out;
    }

    // ---- JSON -------------------------------------------------------------------------------

    /** The same content as JSON (two-space indent, {@code \n}, trailing newline). */
    public static String json() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("description", plain(INTRO));
        List<Map<String, Object>> files = new ArrayList<>();
        for (Sheet sheet : Sheet.values()) {
            List<Map<String, Object>> columns = new ArrayList<>();
            for (Column c : sheet.columns()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", c.name());
                m.put("required", c.required());
                m.put("kind", c.kind().name());
                m.put("note", c.note());
                columns.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", sheet.fileName());
            m.put("columns", columns);
            files.add(m);
        }
        root.put("files", files);

        List<Map<String, Object>> types = new ArrayList<>();
        for (NodeType t : NodeType.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", t.name());
            m.put("rank", t.rank());
            types.add(m);
        }
        root.put("node_types", types);

        List<Map<String, Object>> units = new ArrayList<>();
        UnitTokens.table().forEach((u, tokens) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", u.name());
            m.put("seconds", u.seconds());
            m.put("tokens", tokens);
            units.add(m);
        });
        root.put("time_units", units);

        List<Map<String, Object>> policies = new ArrayList<>();
        for (RoundingPolicy p : RoundingPolicy.values()) {
            Map<String, Object> examples = new LinkedHashMap<>();
            ROUNDING_EXAMPLES.forEach(x -> examples.put(x.toPlainString(), p.round(x)));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            m.put("phrase", p.phrase());
            m.put("examples", examples);
            policies.add(m);
        }
        root.put("rounding_policies", policies);

        Map<String, Object> unitRules = new LinkedHashMap<>();
        unitRules.put("durations", pairsJson(true));
        unitRules.put("rates", pairsJson(false));
        unitRules.put("blank_unit", plain(blankUnitRule()));
        unitRules.put("uncapped", plain(uncappedRule()));
        root.put("units", unitRules);

        List<Map<String, Object>> cells = new ArrayList<>();
        for (Convention c : conventions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node_types", c.types().stream().map(Enum::name).toList());
            m.put("file", c.column().sheet().fileName());
            m.put("column", c.column().name());
            m.put("value", conventionValue(c.value()));
            cells.add(m);
        }
        Map<String, Object> conventions = new LinkedHashMap<>();
        conventions.put("description", plain(CONVENTIONS_INTRO));
        conventions.put("cells", cells);
        conventions.put("notes", List.of(plain(ZERO_OR_BLANK), plain(conventionUnitRule())));
        root.put("conventions", conventions);
        return Json.pretty(root);
    }

    private static List<Map<String, Object>> pairsJson(boolean duration) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Schema.UnitPair p : unitPairs(duration)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", p.value().sheet().fileName());
            m.put("value", p.value().name());
            m.put("unit", p.unit().name());
            out.add(m);
        }
        return out;
    }
}
