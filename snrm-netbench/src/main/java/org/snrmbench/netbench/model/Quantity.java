package org.snrmbench.netbench.model;

import java.math.BigDecimal;

/** A value with a time unit: a duration (unit = the unit of the value) or a rate (unit = the denominator). */
public record Quantity(BigDecimal value, TimeUnit unit) {

    public static Quantity of(long value, TimeUnit unit) {
        return new Quantity(BigDecimal.valueOf(value), unit);
    }
}
