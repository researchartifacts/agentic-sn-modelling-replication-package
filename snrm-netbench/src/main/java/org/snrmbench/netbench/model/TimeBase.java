package org.snrmbench.netbench.model;

import java.math.BigDecimal;

/** The network time base of the SNRM design document, plus the network's own name. */
public record TimeBase(BigDecimal periodValue, TimeUnit periodUnit, int horizonPeriods,
                       RoundingPolicy policy, String name) {

    public BigDecimal periodSeconds() {
        return periodValue.multiply(BigDecimal.valueOf(periodUnit.seconds()));
    }
}
