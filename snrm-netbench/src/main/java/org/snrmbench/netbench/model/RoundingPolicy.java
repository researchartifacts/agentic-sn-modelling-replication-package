package org.snrmbench.netbench.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** How a duration converts to whole periods. NEAREST is half-up. */
public enum RoundingPolicy {
    NEAREST(RoundingMode.HALF_UP, "to the nearest period"),
    UP(RoundingMode.CEILING, "up to the next whole period"),
    DOWN(RoundingMode.FLOOR, "down to the previous whole period");

    private final RoundingMode mode;
    private final String phrase;

    RoundingPolicy(RoundingMode mode, String phrase) {
        this.mode = mode;
        this.phrase = phrase;
    }

    public BigDecimal round(BigDecimal periods) {
        return periods.setScale(0, mode);
    }

    /** The phrase the description header uses ("durations are rounded {phrase}"). */
    public String phrase() {
        return phrase;
    }
}
