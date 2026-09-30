package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.GenerateParams.DistanceMode;
import org.snrmbench.netbench.generate.GenerateParams.Naming;
import org.snrmbench.netbench.generate.GenerateParams.NoiseType;
import org.snrmbench.netbench.generate.GenerateParams.NumberLocale;
import org.snrmbench.netbench.generate.GenerateParams.Order;
import org.snrmbench.netbench.generate.GenerateParams.Style;
import org.snrmbench.netbench.generate.GenerateParams.Units;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.verify.SnrmClient;
import org.snrmbench.netbench.verify.SnrmVerification;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/** netbench generate. */
@Command(name = "generate", mixinStandardHelpOptions = true,
        description = "Generate a description (A), its SNRM network (B), the fact ledger and the manifest.")
public final class GenerateCommand implements Callable<Integer> {

    /** SNRM's validation report of B, written beside the manifest with --snrm-url (not in its sha256 table). */
    public static final String SNRM_VALIDATION = "snrm-validation.json";

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    SnrmOptions snrm = new SnrmOptions();

    @Option(names = "--seed", required = true, description = "RNG seed; determines everything else.")
    long seed;

    @Option(names = "--nodes", required = true, description = "Total node count N (>= echelons).")
    int nodes;

    @Option(names = "--echelons", required = true, description = "Tier count E, 3..7.")
    int echelons;

    @Option(names = "--final-product-name", required = true, description = "Name of the primary product.")
    String finalProductName;

    @Option(names = "--out-dir", required = true, description = "Output root; the run goes to <out-dir>/<run-id>.")
    Path outDir;

    @Option(names = "--products", defaultValue = "1", description = "Number of products (default ${DEFAULT-VALUE}).")
    int products;

    @Option(names = "--naming", defaultValue = "typed", description = "typed|neutral|realistic (default ${DEFAULT-VALUE}).")
    String naming;

    @Option(names = "--style", defaultValue = "template",
            description = "template|paraphrased|narrative (default ${DEFAULT-VALUE}).")
    String style;

    @Option(names = "--order", defaultValue = "by-tier", description = "by-tier|by-node|shuffled (default ${DEFAULT-VALUE}).")
    String order;

    @Option(names = "--units", defaultValue = "uniform", description = "uniform|mixed (default ${DEFAULT-VALUE}).")
    String units;

    @Option(names = "--period-unit", defaultValue = "DAY", description = "HOUR|DAY|WEEK (default ${DEFAULT-VALUE}).")
    String periodUnit;

    @Option(names = "--period-value", defaultValue = "1", description = "Period length (default ${DEFAULT-VALUE}).")
    int periodValue;

    @Option(names = "--horizon", defaultValue = "90", description = "Horizon in periods (default ${DEFAULT-VALUE}).")
    int horizon;

    @Option(names = "--inbound-links", defaultValue = "1,2",
            description = "min,max parents per non-supplier node (default ${DEFAULT-VALUE}).")
    String inboundLinks;

    @Option(names = "--lateral-links", defaultValue = "0",
            description = "Extra DC->DC links within a tier (default ${DEFAULT-VALUE}).")
    int lateralLinks;

    @Option(names = "--attribute-coverage", defaultValue = "1.0",
            description = "Share of optional attributes stated in A, 0..1 (default ${DEFAULT-VALUE}).")
    BigDecimal attributeCoverage;

    @Option(names = "--noise-type", defaultValue = "none",
            description = "none|boilerplate|topical|adversarial (default ${DEFAULT-VALUE}).")
    String noiseType;

    @Option(names = "--noise-ratio", defaultValue = "0", description = "Share of noise sentences, 0..0.8 (default ${DEFAULT-VALUE}).")
    BigDecimal noiseRatio;

    @Option(names = "--geo", description = "Write lat/lng and state them in A.")
    boolean geo;

    @Option(names = "--distance-mode", defaultValue = "none", description = "none|km (default ${DEFAULT-VALUE}).")
    String distanceMode;

    @Option(names = "--locale", defaultValue = "plain", description = "plain|en|de (default ${DEFAULT-VALUE}).")
    String locale;

    @Option(names = "--run-id", description = "Run id (default: derived from the parameters).")
    String runId;

    @Option(names = "--overwrite", description = "Replace an existing run directory.")
    boolean overwrite;

    @Override
    public Integer call() {
        GenerateParams p;
        try {
            p = params();
            p.validate();
        } catch (IllegalArgumentException e) {
            System.err.println("netbench generate: " + e.getMessage());
            return 2;
        }
        String snrmProblem = snrm.problem();
        if (snrmProblem != null) {
            System.err.println("netbench generate: " + snrmProblem);
            return 2;
        }
        try {
            Generator.Generated g = Generator.build(p, runId);
            if (snrm.requested()) {
                g = withSnrmValidation(g);
            }
            Path dir = Generator.write(g, outDir, overwrite);
            spec.commandLine().getOut().println(dir);
            return 0;
        } catch (Generator.SelfCheckFailure e) {
            System.err.println("netbench generate: self-check failed: " + e.getMessage());
            return 3;
        } catch (SnrmClient.SnrmUnreachable e) {
            System.err.println("netbench generate: " + e.getMessage());
            return 5;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            System.err.println("netbench generate: " + e.getMessage());
            return 2;
        }
    }

    /** Self-check 5: SNRM validates B (validateOnly=true) and must find it valid; its report joins the run's files as #SNRM_VALIDATION. */
    private Generator.Generated withSnrmValidation(Generator.Generated g) {
        Map<String, String> truth = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            truth.put(s.fileName(), g.files().get(Generator.TRUTH_DIR + "/" + s.fileName()));
        }
        JsonNode report;
        try (SnrmClient client = snrm.client()) {
            report = client.importNetwork(truth, g.runId(), snrm.projectId, true);
        } catch (SnrmClient.SnrmUnreachable e) {
            throw e;
        } catch (SnrmClient.SnrmException e) {
            throw new Generator.SelfCheckFailure("SNRM could not validate B: " + e.getMessage());
        }
        if (!SnrmVerification.valid(report)) {
            throw new Generator.SelfCheckFailure("SNRM rejects B: " + SnrmVerification.errors(report));
        }
        Map<String, String> files = new LinkedHashMap<>(g.files());
        String manifest = files.remove(Generator.MANIFEST); // Generated.files keeps the manifest last
        files.put(SNRM_VALIDATION, Json.pretty(report));
        files.put(Generator.MANIFEST, manifest);
        return new Generator.Generated(g.params(), g.runId(), g.skeleton(), g.full(), g.truth(), g.facts(),
                g.description(), files, g.manifest());
    }

    GenerateParams params() {
        String[] inbound = inboundLinks.split(",", -1);
        if (inbound.length != 2) {
            throw new IllegalArgumentException("--inbound-links must be min,max (got \"" + inboundLinks + "\")");
        }
        TimeUnit unit;
        try {
            unit = TimeUnit.valueOf(periodUnit.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--period-unit must be HOUR, DAY or WEEK (got \"" + periodUnit + "\")");
        }
        return new GenerateParams(seed, nodes, echelons, finalProductName, products,
                GenerateParams.parse(Naming.class, naming), GenerateParams.parse(Style.class, style),
                GenerateParams.parse(Order.class, order), GenerateParams.parse(Units.class, units), unit, periodValue,
                horizon, integer("--inbound-links", inbound[0]), integer("--inbound-links", inbound[1]), lateralLinks,
                attributeCoverage, GenerateParams.parse(NoiseType.class, noiseType), noiseRatio, geo,
                GenerateParams.parse(DistanceMode.class, distanceMode),
                GenerateParams.parse(NumberLocale.class, locale));
    }

    private static int integer(String flag, String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + ": \"" + s + "\" is not an integer");
        }
    }
}
