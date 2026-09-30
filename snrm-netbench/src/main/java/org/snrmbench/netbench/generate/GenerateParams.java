package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.util.Numbers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Every effective parameter of generate except the output location. */
public record GenerateParams(long seed, int nodes, int echelons, String finalProductName, int products,
                             Naming naming, Style style, Order order, Units units, TimeUnit periodUnit,
                             int periodValue, int horizon, int inboundMin, int inboundMax, int lateralLinks,
                             BigDecimal attributeCoverage, NoiseType noiseType, BigDecimal noiseRatio, boolean geo,
                             DistanceMode distanceMode, NumberLocale locale) {

    /** A CLI enum: its command-line spelling is lower-case with dashes. */
    public interface CliEnum {
        String cli();
    }

    public enum Naming implements CliEnum {
        TYPED, NEUTRAL, REALISTIC;

        public String cli() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Style implements CliEnum {
        TEMPLATE, PARAPHRASED, NARRATIVE;

        public String cli() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Order implements CliEnum {
        BY_TIER, BY_NODE, SHUFFLED;

        public String cli() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public enum Units implements CliEnum {
        UNIFORM, MIXED;

        public String cli() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum NoiseType implements CliEnum {
        NONE, BOILERPLATE, TOPICAL, ADVERSARIAL;

        public String cli() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum DistanceMode implements CliEnum {
        NONE, KM;

        public String cli() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum NumberLocale implements CliEnum {
        PLAIN, EN, DE;

        public String cli() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Parses a CLI spelling ({@code by-tier}) into its enum constant. */
    public static <E extends Enum<E> & CliEnum> E parse(Class<E> type, String value) {
        for (E e : type.getEnumConstants()) {
            if (e.cli().equals(value.trim().toLowerCase(Locale.ROOT))) {
                return e;
            }
        }
        StringBuilder allowed = new StringBuilder();
        for (E e : type.getEnumConstants()) {
            allowed.append(allowed.isEmpty() ? "" : "|").append(e.cli());
        }
        throw new IllegalArgumentException("\"" + value + "\" is not one of " + allowed);
    }

    /** The defaults for everything but the five required parameters. */
    public static GenerateParams defaults(long seed, int nodes, int echelons, String finalProductName) {
        return new GenerateParams(seed, nodes, echelons, finalProductName, 1, Naming.TYPED, Style.TEMPLATE,
                Order.BY_TIER, Units.UNIFORM, TimeUnit.DAY, 1, 90, 1, 2, 0, BigDecimal.ONE, NoiseType.NONE,
                BigDecimal.ZERO, false, DistanceMode.NONE, NumberLocale.PLAIN);
    }

    /** Throws IllegalArgumentException with a one-line message when the combination is invalid. */
    public GenerateParams validate() {
        require(echelons >= 3 && echelons <= 7, "--echelons must be between 3 and 7 (got " + echelons + ")");
        require(nodes >= echelons, "--nodes (" + nodes + ") must be at least --echelons (" + echelons + ")");
        require(products >= 1, "--products must be at least 1 (got " + products + ")");
        require(inboundMin >= 1, "--inbound-links min must be at least 1 (got " + inboundMin + ")");
        require(inboundMax >= inboundMin, "--inbound-links max (" + inboundMax + ") must be at least min ("
                + inboundMin + ")");
        require(lateralLinks >= 0, "--lateral-links must not be negative");
        require(periodValue >= 1, "--period-value must be at least 1");
        require(horizon >= 1, "--horizon must be at least 1");
        require(periodUnit == TimeUnit.HOUR || periodUnit == TimeUnit.DAY || periodUnit == TimeUnit.WEEK,
                "--period-unit must be HOUR, DAY or WEEK");
        require(attributeCoverage.signum() >= 0 && attributeCoverage.compareTo(BigDecimal.ONE) <= 0,
                "--attribute-coverage must be in [0, 1]");
        require(noiseRatio.signum() >= 0 && noiseRatio.compareTo(new BigDecimal("0.8")) <= 0,
                "--noise-ratio must be in [0, 0.8]");
        require(noiseRatio.signum() == 0 || noiseType != NoiseType.NONE,
                "--noise-ratio > 0 requires --noise-type other than none");
        require(finalProductName != null && !finalProductName.isBlank(), "--final-product-name must not be empty");
        require(finalProductName.equals(finalProductName.trim()),
                "--final-product-name must not start or end with whitespace");
        require(finalProductName.chars().allMatch(c -> c >= 0x20 && c < 0x7F),
                "--final-product-name must be printable ASCII");
        require(finalProductName.indexOf(',') < 0, "--final-product-name must not contain a comma");
        return this;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Every effective parameter keyed by its flag name, in flag order: the manifest's params and the input of the run-id hash. */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seed", seed);
        m.put("nodes", nodes);
        m.put("echelons", echelons);
        m.put("final-product-name", finalProductName);
        m.put("products", products);
        m.put("naming", naming.cli());
        m.put("style", style.cli());
        m.put("order", order.cli());
        m.put("units", units.cli());
        m.put("period-unit", periodUnit.name());
        m.put("period-value", periodValue);
        m.put("horizon", horizon);
        m.put("inbound-links", inboundMin + "," + inboundMax);
        m.put("lateral-links", lateralLinks);
        m.put("attribute-coverage", new BigDecimal(Numbers.plain(attributeCoverage)));
        m.put("noise-type", noiseType.cli());
        m.put("noise-ratio", new BigDecimal(Numbers.plain(noiseRatio)));
        m.put("geo", geo);
        m.put("distance-mode", distanceMode.cli());
        m.put("locale", locale.cli());
        return m;
    }

    /** s{seed-n{nodes}-e{echelons}-{style}-{naming}-{units}-{noise}{ratio}-{hash8}}; the hash is the first 8 hex digits of SHA-256 over the canonical (sorted-key) JSON of #asMap. */
    public String defaultRunId() {
        String hash8 = Json.sha256(Json.canonical(asMap())).substring(0, 8);
        return "s" + seed + "-n" + nodes + "-e" + echelons + "-" + style.cli() + "-" + naming.cli() + "-"
                + units.cli() + "-" + noiseType.cli() + Numbers.plain(noiseRatio) + "-" + hash8;
    }
}
