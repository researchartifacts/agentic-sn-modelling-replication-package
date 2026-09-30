package org.snrmbench.netbench.describe;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** How numbers are written in A (--locale): plain as in B (1200, 1.80), en with thousands commas (1,200, 1.80), de with thousands points and a decimal comma (1.200, 1,80). */
public enum NumberStyle {
    PLAIN(null, '.'),
    EN(',', '.'),
    DE('.', ',');

    private final Character grouping;
    private final char decimal;

    NumberStyle(Character grouping, char decimal) {
        this.grouping = grouping;
        this.decimal = decimal;
    }

    /** The style of a {@code --locale} value ({@code plain}, {@code en}, {@code de}). */
    public static NumberStyle of(Enum<?> locale) {
        return valueOf(locale.name());
    }

    /** The header sentence that declares this style, so that 1.500 cannot be misread (a thousand and a half in one style, one and a half in the other); null for plain. */
    public String headerSentence() {
        return switch (this) {
            case PLAIN -> null;
            case EN -> "Numbers use a comma as the thousands separator and a point as the decimal mark.";
            case DE -> "Numbers use a point as the thousands separator and a comma as the decimal mark.";
        };
    }

    /** A canonical number ({@code -1234.5}) in this style; a non-number is returned unchanged. */
    public String format(String canonical) {
        if (this == PLAIN || !canonical.matches("-?\\d+(\\.\\d+)?")) {
            return canonical;
        }
        boolean negative = canonical.startsWith("-");
        String digits = negative ? canonical.substring(1) : canonical;
        int dot = digits.indexOf('.');
        String integer = dot < 0 ? digits : digits.substring(0, dot);
        String fraction = dot < 0 ? "" : digits.substring(dot + 1);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) {
                grouped.append(grouping);
            }
            grouped.append(integer.charAt(i));
        }
        return (negative ? "-" : "") + grouped + (fraction.isEmpty() ? "" : decimal + fraction);
    }

    /** A regex matching one number written in this style (no capturing groups). */
    public String regex() {
        if (this == PLAIN) {
            return "-?\\d+(?:\\.\\d+)?";
        }
        String g = Pattern.quote(String.valueOf(grouping));
        String d = Pattern.quote(String.valueOf(decimal));
        return "-?(?:\\d{1,3}(?:" + g + "\\d{3})+|\\d+)(?:" + d + "\\d+)?";
    }

    /** A number written in this style, as its canonical value. */
    public BigDecimal parse(String text) {
        String s = text;
        if (grouping != null) {
            s = s.replace(String.valueOf(grouping), "");
        }
        return new BigDecimal(s.replace(decimal, '.'));
    }
}
