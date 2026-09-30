package org.snrmbench.netbench.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The only source of randomness. */
public final class Rng {

    /** The six random streams, by the names gives them. */
    public enum Stage {
        STRUCTURE("structure"), ATTRIBUTES("attributes"), NAMING("naming"), COVERAGE("coverage"),
        STYLE("style"), NOISE("noise");

        private final String id;

        Stage(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private final long seed;
    private final Map<Stage, Random> streams = new EnumMap<>(Stage.class);

    public Rng(long seed) {
        this.seed = seed;
    }

    public long seed() {
        return seed;
    }

    /** The stage's stream, created on first use. */
    public Random stream(Stage stage) {
        return streams.computeIfAbsent(stage, s -> new Random(seed * 1_000_003L + s.id().hashCode()));
    }

    /** Uniform integer in [min, max], both inclusive. */
    public int uniformInt(Stage stage, int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max < min: " + min + ".." + max);
        }
        return min + stream(stage).nextInt(max - min + 1);
    }

    /** Uniform multiple of {@code step} in [min, max], both inclusive and multiples of step. */
    public BigDecimal uniformStep(Stage stage, long min, long max, long step) {
        int k = uniformInt(stage, 0, Math.toIntExact((max - min) / step));
        return BigDecimal.valueOf(min + k * step);
    }

    /** Uniform value in [min, max] with {@code scale} decimals, both bounds inclusive. */
    public BigDecimal uniformDecimal(Stage stage, BigDecimal min, BigDecimal max, int scale) {
        long lo = min.movePointRight(scale).longValueExact();
        long hi = max.movePointRight(scale).longValueExact();
        int k = uniformInt(stage, 0, Math.toIntExact(hi - lo));
        return BigDecimal.valueOf(lo + k, scale);
    }

    /** True with probability p; a draw is consumed either way. */
    public boolean bernoulli(Stage stage, double p) {
        return stream(stage).nextDouble() < p;
    }

    public <T> T choice(Stage stage, List<T> items) {
        return items.get(stream(stage).nextInt(items.size()));
    }

    /** In-house Fisher–Yates, from the last index down. */
    public <T> void shuffle(List<T> list, Stage stage) {
        Random r = stream(stage);
        for (int i = list.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /** k items drawn uniformly without replacement, in draw order. */
    public <T> List<T> sample(Stage stage, List<T> from, int k) {
        List<T> pool = new ArrayList<>(from);
        List<T> out = new ArrayList<>(k);
        for (int i = 0; i < k && !pool.isEmpty(); i++) {
            out.add(pool.remove(stream(stage).nextInt(pool.size())));
        }
        return out;
    }
}
