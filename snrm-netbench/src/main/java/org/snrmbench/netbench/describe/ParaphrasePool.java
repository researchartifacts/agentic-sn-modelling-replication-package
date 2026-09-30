package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The wording pools of the paraphrased and narrative styles: one file per phrase (paraphrases/<slot>.txt, narrative/<id>.txt), each line a wording with the same placeholders. */
public final class ParaphrasePool {

    /** The narrative-only pools. */
    public enum Narrative {
        OVERVIEW_TIER_ONE("overview-tier-one"),
        OVERVIEW_TIER_MANY("overview-tier-many"),
        FACILITY_REGION("facility-region"),
        FACILITY_ATTRIBUTES("facility-attributes"),
        COREF_SUPPLIER("coref-supplier"),
        COREF_PLANT("coref-plant"),
        COREF_DC("coref-dc"),
        CUSTOMER_REGION_ONE("customer-region-one"),
        CUSTOMER_REGION_MANY("customer-region-many"),
        RELATIVE_NODE("relative-node"),
        DEMAND_GROUP("demand-group"),
        RELATIVE_INVENTORY("relative-inventory");

        private final String id;

        Narrative(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private static final Map<String, List<String>> CACHE = new ConcurrentHashMap<>();

    private final Rng rng;

    public ParaphrasePool(Rng rng) {
        this.rng = rng;
    }

    /** The lines of a pool file (cached, immutable). */
    public static List<String> lines(String resource) {
        return CACHE.computeIfAbsent(resource, ParaphrasePool::load);
    }

    private static List<String> load(String resource) {
        try (InputStream in = ParaphrasePool.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            List<String> out = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    String kept = line.stripTrailing();
                    if (!kept.isBlank() && !kept.strip().startsWith("#")) {
                        out.add(kept);
                    }
                }
            }
            if (out.isEmpty()) {
                throw new IllegalStateException("empty pool " + resource);
            }
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A wording of a fact phrase. */
    public String draw(Slot slot) {
        return rng.choice(Stage.STYLE, lines("/paraphrases/" + slot.id() + ".txt"));
    }

    /** A narrative phrase. */
    public String draw(Narrative phrase) {
        return rng.choice(Stage.STYLE, lines("/narrative/" + phrase.id() + ".txt"));
    }
}
