package org.snrmbench.netbench.model;

/** SNRM node types; the rank is the echelon order used for direction checks. */
public enum NodeType {
    SUPPLIER(0), PLANT(1), DC(2), CUSTOMER(3);

    private final int rank;

    NodeType(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** SUPPLIER and PLANT count as supply for NO_SUPPLY_NODE and CUSTOMER_UNREACHABLE. */
    public boolean isSupply() {
        return this == SUPPLIER || this == PLANT;
    }
}
