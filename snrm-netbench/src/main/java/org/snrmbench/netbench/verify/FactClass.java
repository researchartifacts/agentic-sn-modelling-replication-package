package org.snrmbench.netbench.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The error taxonomy, in the order the per-field tallies list it. */
public enum FactClass {
    CORRECT, CORRECT_BLANK, CONVENTION_OK, OMITTED, WRONG, WRONG_UNIT, WRONG_TYPE, WRONG_DIRECTION,
    FILLED_UNSTATED, CONVENTION_VIOLATION, DECOY_ABSORBED, FABRICATED;

    /** The name used in reports ({@code wrong_unit}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether a classification of this class is listed in {@code mismatches[]}. */
    public boolean isMismatch() {
        return this != CORRECT && this != CORRECT_BLANK && this != CONVENTION_OK;
    }

    /** Whether the class counts in the denominator of {@code fact_precision} (fabricated facts aside). */
    public boolean inPrecision() {
        return switch (this) {
            case CORRECT, WRONG, WRONG_UNIT, WRONG_TYPE, WRONG_DIRECTION, DECOY_ABSORBED, FILLED_UNSTATED -> true;
            default -> false;
        };
    }

    /** Every class a truth field can take, in report order. */
    public static List<FactClass> perField() {
        return Arrays.stream(values()).filter(c -> c != FABRICATED).toList();
    }
}
