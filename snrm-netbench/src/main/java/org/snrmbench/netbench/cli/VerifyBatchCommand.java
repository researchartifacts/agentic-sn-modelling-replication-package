package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.util.Jsonl;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.verify.Candidate;
import org.snrmbench.netbench.verify.CandidateLoader;
import org.snrmbench.netbench.verify.Report;
import org.snrmbench.netbench.verify.SnrmClient;
import org.snrmbench.netbench.verify.SnrmVerification;
import org.snrmbench.netbench.verify.Verifier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/** netbench verify-batch: verifies every response under <responses>/<model>/<run-id>/<rep>/ against <runs>/<run-id>/truth. */
@Command(name = "verify-batch", mixinStandardHelpOptions = true,
        description = "Verify every <responses>/<model>/<run-id>/<rep>/ response and write results.csv.")
public final class VerifyBatchCommand implements Callable<Integer> {

    static final String SCORED = "scored";
    static final String MISSING_TRUTH = "missing_truth";
    static final String TRUTH_INVALID = "truth_invalid";
    static final String NO_CANDIDATE = "no_candidate";

    static final String REPORT = "report.json";
    static final String CALL = "call.json";

    @Spec
    CommandSpec spec;

    @Option(names = "--runs", required = true, paramLabel = "<dir>",
            description = "The generated runs: <runs>/<run-id>/truth, facts.jsonl, decoys.jsonl, manifest.json.")
    Path runs;

    @Option(names = "--responses", required = true, paramLabel = "<dir>",
            description = "The responses: <responses>/<model>/<run-id>/<rep>/response.md (or candidate/).")
    Path responses;

    @Option(names = "--out", required = true, paramLabel = "<results.csv>", description = "Where to write the table.")
    Path out;

    @Option(names = "--tolerance", paramLabel = "<rel>", defaultValue = "1e-6",
            description = "Relative tolerance for non-integer numbers (default: ${DEFAULT-VALUE}).")
    BigDecimal tolerance;

    @Option(names = "--lenient-numbers",
            description = "Score numbers written with thousands separators or a decimal comma (1,200 / 1.200 / 0,05) "
                    + "by value at L1/L2; L0 stays SNRM's strict reading.")
    boolean lenientNumbers;

    @Mixin
    SnrmOptions snrm = new SnrmOptions();

    @Option(names = "--l3", description = "Behavioural comparison on SNRM for every response (requires --snrm-url).")
    boolean l3;

    @Option(names = "--keep", description = "Keep the projects, networks and runs --l3 creates on SNRM.")
    boolean keep;

    /** The batch's one SNRM client (null without --snrm-url). */
    private SnrmClient client;

    /** A run's truth, loaded once for all its responses. */
    private record Truth(String status, String problem, Path dir, RawNetwork network, List<Fact> facts,
                         List<Decoy> decoys, JsonNode manifest) {
    }

    /** One results.csv row before the columns are laid out. */
    private record Row(String model, String runId, String rep, String status, JsonNode manifest, Report report,
                       Map<String, String> call) {
    }

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        String problem = !Files.isDirectory(runs) ? "--runs " + runs + " is not a directory"
                : !Files.isDirectory(responses) ? "--responses " + responses + " is not a directory"
                : tolerance.signum() < 0 ? "--tolerance " + tolerance.toPlainString() + " is negative"
                : snrm.problem(l3, keep);
        if (problem != null) {
            err.println("verify-batch: " + problem);
            return 2;
        }
        Map<String, Truth> truths = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();
        client = snrm.requested() ? snrm.client() : null;
        try {
            for (Path model : subdirectories(responses)) {
                for (Path run : subdirectories(model)) {
                    Truth truth = truths.computeIfAbsent(name(run), this::loadTruth);
                    for (Path rep : subdirectories(run)) {
                        rows.add(verify(name(model), name(run), rep, truth, err));
                    }
                }
            }
        } catch (SnrmClient.SnrmUnreachable e) {
            err.println("verify-batch: " + e.getMessage());
            return 5;
        } finally {
            if (client != null) {
                client.close();
            }
        }
        List<String> scalars = scalarColumns();
        List<String> calls = new ArrayList<>(new TreeSet<>(rows.stream()
                .flatMap(r -> r.call().keySet().stream()).toList()));
        List<String> header = new ArrayList<>(List.of("model", "run_id", "rep", "status"));
        header.addAll(BatchCommand.parameterNames());
        header.addAll(scalars);
        for (Code c : Code.values()) {
            header.add("l0_" + c.name());
        }
        header.addAll(calls);
        FlatCsv.write(out, header, rows.stream().map(r -> row(r, scalars, calls)).toList());

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : List.of(SCORED, MISSING_TRUTH, TRUTH_INVALID, NO_CANDIDATE)) {
            byStatus.put(s, (int) rows.stream().filter(r -> r.status().equals(s)).count());
        }
        spec.commandLine().getOut().println(out + ": " + rows.size() + " responses, " + byStatus);
        return 0;
    }

    // ---- one response -----------------------------------------------------------------------

    private Row verify(String model, String runId, Path rep, Truth truth, PrintWriter err) {
        Map<String, String> call = callFields(rep, err);
        String where = model + "/" + runId + "/" + name(rep);
        if (!truth.status().equals(SCORED)) {
            err.println("verify-batch: " + where + ": " + truth.status() + ": " + truth.problem());
            return new Row(model, runId, name(rep), truth.status(), truth.manifest(), null, call);
        }
        Path candidate = candidateIn(rep);
        if (candidate == null) {
            err.println("verify-batch: " + where + ": " + NO_CANDIDATE);
            return new Row(model, runId, name(rep), NO_CANDIDATE, truth.manifest(), null, call);
        }
        // as VerifyCommand: load (a response file is split into <candidate>.split/), verify, write the report
        Candidate c = CandidateLoader.load(candidate, null);
        Report report = Verifier.verify(new Verifier.Inputs(truth.network(), c, truth.facts(), truth.decoys(),
                truth.manifest(), tolerance, truth.dir().toString(), candidate.toString(), lenientNumbers));
        if (client != null) {
            report = SnrmVerification.apply(client, report, CandidateLoader.files(candidate), truth.network(),
                    c.network(), new SnrmVerification.Settings(snrm.projectId, l3, keep));
            SnrmOptions.warn(report, keep, err, "verify-batch: " + where);
        }
        try {
            Files.writeString(rep.resolve(REPORT), report.toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Row(model, runId, name(rep), SCORED, truth.manifest(), report, call);
    }

    /** The truth of a run, with its ledgers and manifest from the run directory, as verify defaults them. */
    private Truth loadTruth(String runId) {
        Path runDir = runs.resolve(runId);
        Path dir = runDir.resolve(Generator.TRUTH_DIR);
        if (!Files.isDirectory(dir)) {
            return new Truth(MISSING_TRUTH, "no directory " + dir, dir, null, null, null, null);
        }
        JsonNode manifest = null;
        try {
            manifest = optional(runDir.resolve(Generator.MANIFEST)) == null ? null
                    : Json.read(runDir.resolve(Generator.MANIFEST));
            RawNetwork network = LenientCsvReader.readDirectory(dir);
            long errors = SnrmRules.validate(network).errorCount();
            if (errors > 0) {
                return new Truth(TRUTH_INVALID, dir + " fails the SNRM import rules (" + errors + " errors)", dir,
                        null, null, null, manifest);
            }
            return new Truth(SCORED, null, dir, network, read(optional(runDir.resolve(Generator.FACTS)), Fact.class),
                    read(optional(runDir.resolve(Generator.DECOYS)), Decoy.class), manifest);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return new Truth(TRUTH_INVALID, e.getMessage(), dir, null, null, null, manifest);
        }
    }

    private static Path optional(Path file) {
        return Files.isRegularFile(file) ? file : null;
    }

    private static <T> List<T> read(Path file, Class<T> type) {
        return file == null ? null : Jsonl.read(file, type);
    }

    /** response.md, else response.txt, else the only *.md file, else candidate/; null when none. */
    static Path candidateIn(Path rep) {
        for (String name : List.of("response.md", "response.txt")) {
            if (Files.isRegularFile(rep.resolve(name))) {
                return rep.resolve(name);
            }
        }
        List<Path> md = list(rep).stream().filter(Files::isRegularFile)
                .filter(p -> name(p).toLowerCase(Locale.ROOT).endsWith(".md")).toList();
        if (md.size() == 1) {
            return md.get(0);
        }
        Path dir = rep.resolve("candidate");
        return Files.isDirectory(dir) ? dir : null;
    }

    /** The top-level scalar fields of call.json as {@code call_<key>} columns; empty without the file. */
    private static Map<String, String> callFields(Path rep, PrintWriter err) {
        Map<String, String> out = new LinkedHashMap<>();
        Path file = rep.resolve(CALL);
        if (!Files.isRegularFile(file)) {
            return out;
        }
        JsonNode root;
        try {
            root = Json.read(file);
        } catch (UncheckedIOException e) {
            err.println("verify-batch: " + file + " is not JSON; ignored");
            return out;
        }
        if (root != null && root.isObject()) {
            root.fields().forEachRemaining(e -> {
                if (e.getValue().isValueNode()) {
                    out.put("call_" + e.getKey(), FlatCsv.cell(e.getValue()));
                }
            });
        }
        return out;
    }

    // ---- results.csv ------------------------------------------------------------------------

    /** The keys of Report#scalars, in order. */
    static List<String> scalarColumns() {
        RawNetwork empty = new RawNetwork(Map.of(), List.of());
        Report r = Verifier.verify(new Verifier.Inputs(empty,
                CandidateLoader.fromFiles(Map.of(), CandidateLoader.SOURCE_DIRECTORY), null, null, null,
                BigDecimal.ZERO, "", ""));
        return List.copyOf(r.scalars().keySet());
    }

    private static List<String> row(Row r, List<String> scalars, List<String> calls) {
        List<String> cells = new ArrayList<>(List.of(r.model(), r.runId(), r.rep(), r.status()));
        JsonNode params = r.manifest() == null ? null : r.manifest().get("params");
        for (String p : BatchCommand.parameterNames()) {
            cells.add(FlatCsv.cell(params == null ? null : params.get(p)));
        }
        Map<String, Object> values = r.report() == null ? Map.of() : r.report().scalars();
        for (String s : scalars) {
            cells.add(FlatCsv.cell(values.get(s)));
        }
        Map<Code, Integer> l0 = new EnumMap<>(Code.class);
        if (r.report() != null) {
            for (Finding f : r.report().l0().findings()) {
                l0.merge(f.code(), 1, Integer::sum);
            }
        }
        for (Code c : Code.values()) {
            cells.add(r.report() == null ? "" : Integer.toString(l0.getOrDefault(c, 0)));
        }
        for (String c : calls) {
            cells.add(r.call().getOrDefault(c, ""));
        }
        return cells;
    }

    // ---- directories ------------------------------------------------------------------------

    private static List<Path> subdirectories(Path dir) {
        return list(dir).stream().filter(Files::isDirectory).toList();
    }

    /** The entries of a directory, sorted by name (plain string order, the same on every platform). */
    private static List<Path> list(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted((a, b) -> name(a).compareTo(name(b))).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String name(Path p) {
        return p.getFileName().toString();
    }
}
