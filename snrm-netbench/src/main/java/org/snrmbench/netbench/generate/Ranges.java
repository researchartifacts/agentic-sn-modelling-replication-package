package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.model.NodeType;

import java.math.BigDecimal;
import java.util.List;

/** Every numeric range, in one place. Money and probabilities have two decimals. */
public final class Ranges {

    private Ranges() {
    }

    /** An inclusive integer range. */
    public record IntRange(int min, int max) {
    }

    /** An inclusive range of two-decimal values. */
    public record DecRange(BigDecimal min, BigDecimal max) {
        static DecRange of(String min, String max) {
            return new DecRange(new BigDecimal(min), new BigDecimal(max));
        }
    }

    /** An inclusive range of multiples of {@code step}. */
    public record StepRange(long min, long max, long step) {
    }

    public static final List<String> DOMESTIC_REGIONS = List.of("EU-West", "EU-North", "EU-South", "EU-East",
            "EU-Central");
    public static final List<String> OVERSEAS_REGIONS = List.of("APAC", "AMER");
    public static final double SUPPLIER_OVERSEAS_PROBABILITY = 0.3;

    public static final IntRange UNIT_VALUE = new IntRange(10, 100);
    public static final IntRange DEMAND_PRIMARY = new IntRange(10, 60);
    public static final IntRange DEMAND_SECONDARY = new IntRange(2, 15);

    /** Capacity slack, drawn in thousandths from 1.100 to 1.600. */
    public static final IntRange SLACK_THOUSANDTHS = new IntRange(1100, 1600);

    /** Lead times in periods and transport costs per unit, per kind of lane. */
    public enum Lane {
        SUPPLIER_PLANT_DOMESTIC(new IntRange(2, 5), DecRange.of("0.30", "0.60")),
        SUPPLIER_PLANT_OVERSEAS(null, DecRange.of("1.50", "3.00")),
        SUPPLIER_SUPPLIER(new IntRange(2, 5), DecRange.of("0.20", "0.50")),
        PLANT_PLANT(new IntRange(1, 3), DecRange.of("0.20", "0.40")),
        PLANT_DOWNSTREAM(new IntRange(1, 3), DecRange.of("0.20", "0.50")),
        DC_DC(new IntRange(1, 4), DecRange.of("0.20", "0.45")),
        DC_CUSTOMER(new IntRange(1, 3), DecRange.of("0.10", "0.40"));

        private final IntRange leadTime;
        private final DecRange unitCost;

        Lane(IntRange leadTime, DecRange unitCost) {
            this.leadTime = leadTime;
            this.unitCost = unitCost;
        }

        /** Null for the overseas lane, whose lead time is one of {@link #OVERSEAS_LEAD_TIMES}. */
        public IntRange leadTime() {
            return leadTime;
        }

        public DecRange unitCost() {
            return unitCost;
        }
    }

    /** Overseas supplier lead times in periods: 2–4 weeks on a DAY period… */
    public static final List<Integer> OVERSEAS_LEAD_TIMES = List.of(14, 21, 28);
    /** …and 2–4 on a WEEK period. */
    public static final List<Integer> OVERSEAS_LEAD_TIMES_WEEK = List.of(2, 3, 4);

    /** Node attribute ranges per type; customers carry the zero conventions and draw nothing. */
    public record NodeRanges(IntRange processingTime, StepRange fixedCost, DecRange varCost, DecRange failureProb) {
    }

    public static NodeRanges node(NodeType type) {
        return switch (type) {
            case SUPPLIER -> new NodeRanges(null, new StepRange(500, 2500, 50), DecRange.of("1.50", "3.00"),
                    DecRange.of("0.01", "0.07"));
            case PLANT -> new NodeRanges(new IntRange(1, 3), new StepRange(5000, 15000, 100),
                    DecRange.of("5.00", "8.00"), DecRange.of("0.01", "0.03"));
            case DC -> new NodeRanges(new IntRange(0, 1), new StepRange(1000, 5000, 50), DecRange.of("0.80", "1.50"),
                    DecRange.of("0.01", "0.03"));
            case CUSTOMER -> null;
        };
    }

    public static final DecRange LINK_FAILURE_PROB = DecRange.of("0.01", "0.06");
    public static final IntRange INVENTORY_MULTIPLE = new IntRange(2, 6);
    public static final DecRange HOLDING_COST = DecRange.of("0.02", "0.50");

    /** --units mixed re-expression probabilities. */
    public static final double MIXED_SUPPLIER_CAPACITY = 0.5;
    public static final double MIXED_CUSTOMER_DEMAND = 0.4;
    public static final double MIXED_HOLDING_COST = 0.3;
    public static final double MIXED_LEAD_TIME_WEEK = 0.7;
    public static final double MIXED_LEAD_TIME_FINER = 0.2;

    /** Distance mode km: lane speeds in km per day. */
    public static final int ROAD_KM_PER_DAY = 480;
    public static final int OCEAN_KM_PER_DAY = 600;

    /** Geo jitter in degrees around the region centroid. */
    public static final DecRange GEO_JITTER = DecRange.of("-2.00", "2.00");
}
