package org.snrmbench.netbench.model;

import java.math.BigDecimal;

/** A node; every optional attribute is nullable and null means "blank cell / not stated". */
public record Node(String name, NodeType type, int tierIndex, Quantity capacity, Quantity processingTime,
                   BigDecimal fixedCost, BigDecimal varCost, BigDecimal failureProb, String region,
                   BigDecimal lat, BigDecimal lng) {

    public Node withName(String newName) {
        return new Node(newName, type, tierIndex, capacity, processingTime, fixedCost, varCost, failureProb,
                region, lat, lng);
    }
}
