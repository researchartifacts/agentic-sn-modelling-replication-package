package org.snrmbench.netbench.model;

import java.math.BigDecimal;

public record Link(String source, String target, Quantity leadTime, Quantity capacity,
                   BigDecimal unitCost, BigDecimal failureProb) {

    /** The entity key used in the fact ledger and reports: {@code source->target}. */
    public String key() {
        return source + "->" + target;
    }
}
