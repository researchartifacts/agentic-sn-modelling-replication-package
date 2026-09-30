package org.snrmbench.netbench.validate;

/** Validation codes. */
public enum Code {
    // row level, errors
    MISSING_REQUIRED_COLUMN(Severity.ERROR),
    REQUIRED_VALUE_MISSING(Severity.ERROR),
    UNKNOWN_ENUM_VALUE(Severity.ERROR),
    NOT_A_NUMBER(Severity.ERROR),
    UNKNOWN_TIME_UNIT(Severity.ERROR),
    PROBABILITY_OUT_OF_RANGE(Severity.ERROR),
    OUT_OF_RANGE(Severity.ERROR),
    DUPLICATE_KEY(Severity.ERROR),
    SELF_LOOP(Severity.ERROR),
    UNKNOWN_NODE_REFERENCE(Severity.ERROR),
    UNKNOWN_PRODUCT_REFERENCE(Severity.ERROR),
    // row level, warnings
    UNRECOGNISED_SHEET(Severity.WARNING),
    UNMAPPED_COLUMN(Severity.WARNING),
    ROW_WIDTH_MISMATCH(Severity.WARNING),
    // network level, errors
    NO_SUPPLY_NODE(Severity.ERROR),
    NO_CUSTOMER_NODE(Severity.ERROR),
    NO_DEMAND_DECLARED(Severity.ERROR),
    CUSTOMER_UNREACHABLE(Severity.ERROR),
    DURATION_ROUNDS_TO_ZERO(Severity.ERROR),
    // network level, warnings
    LATERAL_LINK(Severity.WARNING),
    ECHELON_DIRECTION(Severity.WARNING),
    ORPHAN_NODE(Severity.WARNING),
    DEMAND_ON_NON_CUSTOMER(Severity.WARNING),
    DURATION_ROUNDING_ERROR(Severity.WARNING),
    PERIOD_TOO_FINE(Severity.WARNING),
    NETWORK_CHECKS_ON_PARTIAL_GRAPH(Severity.WARNING),
    PRODUCT_CREATED_IMPLICITLY(Severity.WARNING),
    // this tool's own (verify only)
    ENVELOPE_PARSE_ERROR(Severity.ERROR),
    FILE_MISSING(Severity.ERROR),
    NETWORK_META_DEFAULTED(Severity.WARNING);

    private final Severity severity;

    Code(Severity severity) {
        this.severity = severity;
    }

    /** The usual severity; FILE_MISSING is a warning for the three optional sheets. */
    public Severity severity() {
        return severity;
    }
}
