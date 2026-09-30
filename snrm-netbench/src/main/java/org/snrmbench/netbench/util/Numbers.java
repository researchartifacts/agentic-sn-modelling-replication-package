package org.snrmbench.netbench.util;

import org.snrmbench.netbench.schema.Column;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;

/** The one number formatter of B and A and the strict numeric parser of C. */
public final class Numbers {

    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

    private Numbers() {
    }

    /** Formats a cell of the given column kind: blank for null, the literal 0 for zero (the samples' convention), two decimals for DECIMAL2 and a plain integer for INTEGER. */
    public static String format(BigDecimal value, Column.Kind kind) {
        if (value == null) {
            return "";
        }
        if (value.signum() == 0) {
            return "0";
        }
        return switch (kind) {
            case DECIMAL2 -> value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
            case INTEGER -> value.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
            default -> plain(value);
        };
    }

    /** Two decimals ({@code 1.80}), zero as {@code 0}. */
    public static String decimal2(BigDecimal value) {
        return format(value, Column.Kind.DECIMAL2);
    }

    /** An integer ({@code 1200}), zero as {@code 0}. */
    public static String integer(BigDecimal value) {
        return format(value, Column.Kind.INTEGER);
    }

    /** Shortest plain form: {@code 1.50 -> 1.5}, {@code 10 -> 10}, never an exponent. */
    public static String plain(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /** Parses a cell under the strict grammar: optional sign, digits, optional fraction. */
    public static Optional<BigDecimal> parse(String cell) {
        if (cell == null) {
            return Optional.empty();
        }
        String s = cell.trim();
        if (!NUMBER.matcher(s).matches()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(s));
    }

    // a leading group of 0 is never thousands grouping ("0,050" is a decimal comma)
    private static final Pattern GROUPED_COMMA = Pattern.compile("[+-]?[1-9]\\d{0,2}(,\\d{3})+");
    private static final Pattern GROUPED_POINT = Pattern.compile("[+-]?[1-9]\\d{0,2}(\\.\\d{3})+");
    private static final Pattern DIGIT_SPACE = Pattern.compile("(?<=\\d)[\\s\\u00A0\\u202F'](?=\\d)");

    /** The locale-lenient reading of a number (verify's --lenient-numbers; never used at L0): the strict grammar first, then thousands separators and a decimal comma. */
    public static Optional<BigDecimal> parseLenient(String cell) {
        Optional<BigDecimal> strict = parse(cell);
        if (strict.isPresent() || cell == null) {
            return strict;
        }
        String s = DIGIT_SPACE.matcher(cell.trim()).replaceAll("");
        int comma = s.lastIndexOf(',');
        int point = s.lastIndexOf('.');
        if (comma >= 0 && point >= 0) {
            s = comma > point ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (comma >= 0) {
            s = GROUPED_COMMA.matcher(s).matches() ? s.replace(",", "")
                    : s.indexOf(',') == comma ? s.replace(',', '.') : s;
        } else if (point >= 0 && GROUPED_POINT.matcher(s).matches()) {
            s = s.replace(".", "");
        }
        return parse(s);
    }

    public static boolean isInteger(BigDecimal v) {
        return v.signum() == 0 || v.stripTrailingZeros().scale() <= 0;
    }

    public static BigDecimal ceilToMultipleOf10(BigDecimal x) {
        return x.divide(BigDecimal.TEN, 0, RoundingMode.CEILING).multiply(BigDecimal.TEN);
    }
}
