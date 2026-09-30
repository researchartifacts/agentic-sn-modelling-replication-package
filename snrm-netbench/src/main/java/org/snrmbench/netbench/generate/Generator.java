package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.csv.CanonicalCsvWriter;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.describe.Description;
import org.snrmbench.netbench.describe.Describer;
import org.snrmbench.netbench.describe.NumberStyle;
import org.snrmbench.netbench.describe.StatementBuilder;
import org.snrmbench.netbench.extract.TemplateExtractor;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.RoundingPolicy;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.util.Jsonl;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.validate.Severity;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.validate.ValidationResult;
import org.snrmbench.netbench.verify.CandidateLoader;
import org.snrmbench.netbench.verify.Report;
import org.snrmbench.netbench.verify.Verifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** generate: builds the full network, the fact ledger, B and A in memory, runs the self-checks, and only then writes the run directory. */
public final class Generator {

    public static final String DESCRIPTION = "description/description.md";
    public static final String TRUTH_DIR = "truth";
    public static final String FACTS = "facts.jsonl";
    public static final String DECOYS = "decoys.jsonl";
    public static final String FULL_NETWORK = "network.full.json";
    public static final String MANIFEST = "manifest.json";

    private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /** A self-check failed: the run is not written (exit code 3). */
    public static final class SelfCheckFailure extends RuntimeException {
        public SelfCheckFailure(String message) {
            super(message);
        }
    }

    /** Everything one run produces, in memory. */
    public record Generated(GenerateParams params, String runId, Skeleton skeleton, Network full, Network truth,
                            List<Fact> facts, Description description, Map<String, String> files,
                            Map<String, Object> manifest) {
    }

    private Generator() {
    }

    /** Builds a run in memory and self-checks it; throws {@link SelfCheckFailure} when a check fails. */
    public static Generated build(GenerateParams p, String runIdOrNull) {
        p.validate();
        String runId = runIdOrNull == null ? p.defaultRunId() : runIdOrNull;
        if (!SAFE_RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("--run-id must be letters, digits, '.', '_' or '-' (got \"" + runId
                    + "\")");
        }
        Rng rng = new Rng(p.seed());
        Skeleton skeleton = StructureBuilder.build(p, rng);
        // attributes are drawn by canonical id, never by name; realistic names then follow the regions
        String[] ids = new String[skeleton.nodes().size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = "#" + i;
        }
        Network sampled = AttributeSampler.sample(skeleton, ids, p, rng);
        String[] regions = sampled.nodes().stream().map(Node::region).toArray(String[]::new);
        String[] names = Namer.assign(skeleton, regions, p, rng);
        Network full = Namer.rename(sampled, ids, names);
        List<Fact> ledger = FactLedger.build(full, p, rng);
        Network truth = FactLedger.blank(full, ledger);
        Description d = Describer.describe(truth, ledger, p, rng);

        Map<String, String> files = new LinkedHashMap<>();
        files.put(DESCRIPTION, d.markdown());
        CanonicalCsvWriter.render(truth, p.geo())
                .forEach((sheet, csv) -> files.put(TRUTH_DIR + "/" + sheet.fileName(), csv));
        files.put(FACTS, Jsonl.render(descriptionOrder(d.facts())));
        files.put(DECOYS, Jsonl.render(d.decoys()));
        files.put(FULL_NETWORK, Json.pretty(full));

        List<String> notes = new ArrayList<>();
        if (skeleton.lateralNote() != null) {
            notes.add(skeleton.lateralNote());
        }
        int expectedLateral = skeleton.sameTypeLinkCount();
        Map<String, Object> manifest = Manifest.build(p, runId, truth, d.facts(), d, expectedLateral, notes, files);
        Generated g = new Generated(p, runId, skeleton, full, truth, d.facts(), d, files, manifest);
        selfCheck(g, expectedLateral);
        Map<String, String> withManifest = new LinkedHashMap<>(files);
        withManifest.put(MANIFEST, Json.pretty(manifest));
        return new Generated(p, runId, skeleton, full, truth, d.facts(), d, withManifest, manifest);
    }

    /** Writes a built run to {@code <outDir>/<runId>}; refuses an existing run directory unless overwrite. */
    public static Path write(Generated g, Path outDir, boolean overwrite) {
        Path runDir = outDir.resolve(g.runId());
        try {
            if (Files.exists(runDir) && !overwrite) {
                throw new IllegalArgumentException("run directory " + runDir + " exists (use --overwrite)");
            }
            Files.createDirectories(outDir);
            Path staging = outDir.resolve("." + g.runId() + ".staging");
            deleteTree(staging);
            for (Map.Entry<String, String> e : g.files().entrySet()) {
                Path f = staging.resolve(e.getKey());
                Files.createDirectories(f.getParent());
                Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
            }
            deleteTree(runDir);
            Files.move(staging, runDir);
            return runDir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Stated facts by (sentence, id), then unstated facts by id: the order A states them in. */
    static List<Fact> descriptionOrder(List<Fact> facts) {
        return facts.stream()
                .sorted(Comparator.comparing((Fact f) -> f.sentence() == null ? Integer.MAX_VALUE : f.sentence())
                        .thenComparingInt(Fact::id))
                .toList();
    }

    // ---- self-checks -----------------------------------------------------------------

    static void selfCheck(Generated g, int expectedLateral) {
        checkImport(g, expectedLateral);
        checkFeasible(g);
        if (g.params().style() == GenerateParams.Style.TEMPLATE) {
            checkRoundTrip(g);
        }
        checkSentences(g);
    }

    /** 1. SnrmRules over B: zero errors, and exactly the predicted warnings. */
    private static void checkImport(Generated g, int expectedLateral) {
        ValidationResult r = SnrmRules.validate(LenientCsvReader.read(truthFiles(g)));
        Map<String, Integer> expected = new TreeMap<>();
        if (expectedLateral > 0) {
            expected.put("LATERAL_LINK", expectedLateral);
        }
        if (r.errorCount() > 0 || !r.countsByCode(Severity.WARNING).equals(expected)) {
            throw new SelfCheckFailure("B does not import as predicted: expected warnings " + expected + ", got "
                    + r.findings());
        }
    }

    /** 2. Max-flow reaches the total demand. */
    private static void checkFeasible(Generated g) {
        Feasibility.Result f = Feasibility.check(g.truth());
        if (!f.feasible()) {
            throw new SelfCheckFailure("B is not demand-feasible: max-flow " + f.maxFlow() + " < total demand "
                    + f.totalDemand());
        }
    }

    /** 3. Template style: the extractor's output from A verifies perfectly against B. */
    private static void checkRoundTrip(Generated g) {
        TemplateExtractor.Result x = TemplateExtractor.extract(g.description().markdown(),
                NumberStyle.of(g.params().locale()));
        if (!x.problems().isEmpty()) {
            throw new SelfCheckFailure("round trip: extractor problems " + x.problems());
        }
        Map<String, String> extracted = new LinkedHashMap<>();
        CanonicalCsvWriter.render(x.network(), g.params().geo()).forEach((s, csv) -> extracted.put(s.fileName(), csv));
        Report r = Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(truthFiles(g)),
                CandidateLoader.fromFiles(extracted, CandidateLoader.SOURCE_DIRECTORY), g.facts(),
                g.description().decoys(), null, new BigDecimal("1e-6"), TRUTH_DIR, "extracted"));
        if (r.factRecall() != 1.0 || r.structureF1() != 1.0 || r.fabricatedCount() != 0
                || r.conventionViolationCount() != 0) {
            throw new SelfCheckFailure("round trip: extracted network scores fact_recall " + r.factRecall()
                    + ", structure_f1 " + r.structureF1() + ", " + r.fabricatedCount() + " fabricated, "
                    + r.conventionViolationCount() + " convention violations: " + r.l2().mismatches());
        }
        if (g.params().distanceMode() == GenerateParams.DistanceMode.KM
                && g.params().units() == GenerateParams.Units.MIXED) {
            return;
        }
        for (Sheet s : Sheet.values()) {
            String truth = g.files().get(TRUTH_DIR + "/" + s.fileName());
            if (!truth.equals(extracted.get(s.fileName()))) {
                throw new SelfCheckFailure("round trip: " + s.fileName() + " extracted from A differs from B\n"
                        + "--- B\n" + truth + "--- extracted\n" + extracted.get(s.fileName()));
            }
        }
    }

    /** B's five files by file name. */
    private static Map<String, String> truthFiles(Generated g) {
        Map<String, String> files = new LinkedHashMap<>();
        for (Sheet s : Sheet.values()) {
            files.put(s.fileName(), g.files().get(TRUTH_DIR + "/" + s.fileName()));
        }
        return files;
    }

    /** 4. Every stated attribute fact (relative:false) points at a sentence containing its formatted value; every existence fact and every relative fact at one containing its entity name(s). */
    private static void checkSentences(Generated g) {
        NumberStyle numbers = NumberStyle.of(g.params().locale());
        Map<String, StatementBuilder.Distance> distances = g.params().distanceMode() == GenerateParams.DistanceMode.KM
                ? Describer.distances(g.truth(), g.facts()) : Map.of();
        for (Fact f : g.facts()) {
            if (!f.stated()) {
                continue;
            }
            String sentence = g.description().sentence(f.sentence());
            List<String> needles = f.field().equals("exists") || f.relative() ? entityNames(f)
                    : valueTexts(f, numbers, distances);
            for (String needle : needles) {
                if (!sentence.contains(needle)) {
                    throw new SelfCheckFailure("fact " + FactLedger.key(f) + " points at sentence " + f.sentence()
                            + " which does not contain \"" + needle + "\": " + sentence);
                }
            }
        }
    }

    /** The text a stated value appears as in A: in the run's number style, the policy phrase for the rounding policy, and a lane's kilometres for its lead time in distance mode. */
    static List<String> valueTexts(Fact f, NumberStyle numbers, Map<String, StatementBuilder.Distance> distances) {
        Field field = FactLedger.field(f);
        if (field == Field.META_ROUNDING) {
            return List.of(RoundingPolicy.valueOf(f.value()).phrase());
        }
        if (field == Field.META_NAME || field == Field.NODE_REGION) {
            return List.of(f.value());
        }
        if (field == Field.NODE_GEO) {
            return java.util.Arrays.stream(f.value().split(",")).map(numbers::format).toList();
        }
        if (field == Field.LINK_LEAD_TIME && distances.containsKey(f.entity())) {
            return List.of(numbers.format(distances.get(f.entity()).km()) + " km");
        }
        return List.of(numbers.format(f.value()));
    }

    /** The names an entity key is made of, split by the fact's kind (a product name may contain "->"). */
    static List<String> entityNames(Fact f) {
        if (f.kind().equals(Sheet.LINKS.factKind())) {
            return List.of(f.entity().split("->", 2));
        }
        if (f.kind().equals(Sheet.NODE_PRODUCTS.factKind())) {
            return List.of(f.entity().split("\\|", 2));
        }
        return List.of(f.entity());
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
