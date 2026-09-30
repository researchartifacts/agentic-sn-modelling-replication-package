package org.snrmbench.netbench.model;

import java.math.BigDecimal;

public record NodeProduct(String node, String product, Quantity demand, BigDecimal initialInventory,
                          BigDecimal safetyStock, Quantity holdingCost) {

    /** The entity key used in the fact ledger and reports: {@code node|product}. */
    public String key() {
        return node + "|" + product;
    }
}
