package org.snrmbench.netbench.schema;

/** One canonical column. */
public record Column(Sheet sheet, String name, boolean required, Kind kind, Check check, String note) {

    public enum Kind {
        /** Free text (names, region, caption). */
        TEXT,
        /** A {@code NodeType}. */
        NODE_TYPE,
        /** A {@code RoundingPolicy}. */
        ROUNDING_POLICY,
        /** A time-unit token. */
        TIME_UNIT,
        /** Numeric, written as an integer without a decimal point. */
        INTEGER,
        /** Numeric, written with exactly two decimals (convention zeros as {@code 0}). */
        DECIMAL2,
        /** Numeric, read but never written (canvas coordinates). */
        NUMBER,
        /** A boolean flag, read but never scored. */
        BOOLEAN;

        public boolean numeric() {
            return this == INTEGER || this == DECIMAL2 || this == NUMBER;
        }
    }

    /** The row-level range rule a cell of this column is subject to. */
    public enum Check {
        NONE,
        /** OUT_OF_RANGE when negative. */
        NON_NEGATIVE,
        /** PROBABILITY_OUT_OF_RANGE outside [0, 1]. */
        PROBABILITY,
        /** OUT_OF_RANGE when not strictly positive ({@code period_length_value}). */
        POSITIVE,
        /** OUT_OF_RANGE when below 1 ({@code horizon_periods}). */
        AT_LEAST_ONE
    }

    /** "{@code nodes.capacity_value}", for messages. */
    public String qualified() {
        return sheet.id() + "." + name;
    }

    @Override
    public String toString() {
        return qualified();
    }
}
