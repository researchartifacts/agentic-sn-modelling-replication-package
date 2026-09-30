package org.snrmbench.netbench.schema;

import org.snrmbench.netbench.model.TimeUnit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The unit tokens accepted in a *_unit cell: case-insensitive, trimmed, singular, plural and common abbreviations. */
public final class UnitTokens {

    private static final Map<String, TimeUnit> TOKENS = build();

    private UnitTokens() {
    }

    private static Map<String, TimeUnit> build() {
        Map<String, TimeUnit> m = new LinkedHashMap<>();
        put(m, TimeUnit.SECOND, "s", "sec", "secs", "second", "seconds");
        put(m, TimeUnit.MINUTE, "min", "mins", "minute", "minutes");
        put(m, TimeUnit.HOUR, "h", "hr", "hrs", "hour", "hours");
        put(m, TimeUnit.DAY, "d", "day", "days");
        put(m, TimeUnit.WEEK, "wk", "wks", "week", "weeks");
        put(m, TimeUnit.MONTH, "mo", "mon", "month", "months");
        put(m, TimeUnit.YEAR, "y", "yr", "yrs", "year", "years");
        return Collections.unmodifiableMap(m);
    }

    private static void put(Map<String, TimeUnit> m, TimeUnit unit, String... tokens) {
        for (String t : tokens) {
            m.put(t, unit);
        }
    }

    /** The unit a token names, or empty when the token is not in the table (blank included). */
    public static Optional<TimeUnit> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TOKENS.get(token.trim().toLowerCase(Locale.ROOT)));
    }

    /** Every accepted token per unit, in table order. */
    public static Map<TimeUnit, List<String>> table() {
        Map<TimeUnit, List<String>> out = new LinkedHashMap<>();
        for (TimeUnit u : TimeUnit.values()) {
            out.put(u, TOKENS.entrySet().stream().filter(e -> e.getValue() == u).map(Map.Entry::getKey).toList());
        }
        return out;
    }
}
