package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.util.Jsonl;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.validate.ValidationResult;
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
import java.util.List;
import java.util.concurrent.Callable;

/** netbench verify: scores a candidate against the truth and writes report.json. */
@Command(name = "verify", mixinStandardHelpOptions = true,
        description = "Score a candidate network (C) against the truth (B) and write report.json.")
public final class VerifyCommand implements Callable<Integer> {

    static final int EXIT_OK = 0;
    static final int EXIT_BAD_ARGUMENTS = 2;
    static final int EXIT_TRUTH_INVALID = 3;
    static final int EXIT_SNRM_UNREACHABLE = 5;

    @Spec
    CommandSpec spec;

    @Mixin
    SnrmOptions snrm = new SnrmOptions();

    @Option(names = "--l3", description = "Behavioural comparison on SNRM (requires --snrm-url).")
    boolean l3;

    @Option(names = "--keep", description = "Keep the projects, networks and runs --l3 creates on SNRM.")
    boolean keep;

    @Option(names = "--truth", required = true, paramLabel = "<dir>", description = "The truth directory (B).")
    Path truth;

    @Option(names = "--candidate", required = true, paramLabel = "<dir|file>",
            description = "The candidate: a directory of CSV files, or a response file to split.")
    Path candidate;

    @Option(names = "--out", required = true, paramLabel = "<report.json>", description = "Where to write the report.")
    Path out;

    @Option(names = "--facts", paramLabel = "<facts.jsonl>",
            description = "The fact ledger (default: ../facts.jsonl beside the truth directory, when present).")
    Path facts;

    @Option(names = "--decoys", paramLabel = "<decoys.jsonl>",
            description = "The decoy ledger (default: ../decoys.jsonl beside the truth directory, when present).")
    Path decoys;

    @Option(names = "--manifest", paramLabel = "<manifest.json>",
            description = "The run manifest (default: ../manifest.json beside the truth directory, when present).")
    Path manifest;

    @Option(names = "--tolerance", paramLabel = "<rel>", defaultValue = "1e-6",
            description = "Relative tolerance for non-integer numbers (default: ${DEFAULT-VALUE}).")
    BigDecimal tolerance;

    @Option(names = "--lenient-numbers",
            description = "Score numbers written with thousands separators or a decimal comma (1,200 / 1.200 / 0,05) "
                    + "by value at L1/L2; L0 stays SNRM's strict reading.")
    boolean lenientNumbers;

    @Option(names = "--split-dir", paramLabel = "<dir>",
            description = "Where a response file's split CSV files go (default: <candidate>.split/).")
    Path splitDir;

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        String problem = checkArguments();
        if (problem != null) {
            err.println("verify: " + problem);
            return EXIT_BAD_ARGUMENTS;
        }
        RawNetwork truthNetwork;
        try {
            truthNetwork = LenientCsvReader.readDirectory(truth);
        } catch (UncheckedIOException e) {
            err.println("verify: truth " + truth + " cannot be read: " + e.getCause().getMessage());
            return EXIT_TRUTH_INVALID;
        }
        ValidationResult truthResult = SnrmRules.validate(truthNetwork);
        if (!truthResult.valid()) {
            err.println("verify: truth " + truth + " fails the SNRM import rules (" + truthResult.errorCount()
                    + " errors):");
            truthResult.findings().forEach(f -> err.println("  " + describe(f)));
            return EXIT_TRUTH_INVALID;
        }
        List<Fact> factList;
        List<Decoy> decoyList;
        JsonNode manifestNode;
        try {
            factList = read(orBeside(facts, "facts.jsonl"), Fact.class);
            decoyList = read(orBeside(decoys, "decoys.jsonl"), Decoy.class);
            Path m = orBeside(manifest, "manifest.json");
            manifestNode = m == null ? null : Json.read(m);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            err.println("verify: " + e.getMessage());
            return EXIT_BAD_ARGUMENTS;
        }
        Candidate c = CandidateLoader.load(candidate, splitDir);
        Report report = Verifier.verify(new Verifier.Inputs(truthNetwork, c, factList, decoyList, manifestNode,
                tolerance, truth.toString(), candidate.toString(), lenientNumbers));
        if (snrm.requested()) {
            try (SnrmClient client = snrm.client()) {
                report = SnrmVerification.apply(client, report, CandidateLoader.files(candidate), truthNetwork,
                        c.network(), new SnrmVerification.Settings(snrm.projectId, l3, keep));
            } catch (SnrmClient.SnrmUnreachable e) {
                err.println("verify: " + e.getMessage());
                return EXIT_SNRM_UNREACHABLE;
            }
            SnrmOptions.warn(report, keep, err, "verify");
        }
        write(report.toJson());
        spec.commandLine().getOut().println(out + ": import_valid=" + report.importValid()
                + " structure_f1=" + report.structureF1() + " fact_recall=" + report.factRecall()
                + " fact_precision=" + report.factPrecision()
                + (report.snrm() == null ? "" : " import_valid_snrm=" + report.snrm().importValid())
                + (report.l3() == null ? "" : " l3=" + report.l3().status()
                        + " behavioural_equivalence=" + report.l3().behaviouralEquivalence()));
        return EXIT_OK;
    }

    /** A one-line reason the arguments are unusable, or null. */
    private String checkArguments() {
        String snrmProblem = snrm.problem(l3, keep);
        if (snrmProblem != null) {
            return snrmProblem;
        }
        if (!Files.isDirectory(truth)) {
            return "--truth " + truth + " is not a directory";
        }
        if (!Files.exists(candidate)) {
            return "--candidate " + candidate + " does not exist";
        }
        if (tolerance.signum() < 0) {
            return "--tolerance " + tolerance.toPlainString() + " is negative";
        }
        for (Path p : new Path[] {facts, decoys, manifest}) {
            if (p != null && !Files.isRegularFile(p)) {
                return p + " is not a file";
            }
        }
        return null;
    }

    /** The given file, else the named file beside the truth directory when it exists, else null. */
    private Path orBeside(Path given, String name) {
        if (given != null) {
            return given;
        }
        Path parent = truth.toAbsolutePath().normalize().getParent();
        Path beside = parent == null ? null : parent.resolve(name);
        return beside != null && Files.isRegularFile(beside) ? beside : null;
    }

    private static <T> List<T> read(Path file, Class<T> type) {
        return file == null ? null : Jsonl.read(file, type);
    }

    private void write(String json) {
        try {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String describe(Finding f) {
        return f.severity() + " " + f.code() + (f.file() == null ? "" : " " + f.file())
                + (f.line() == null ? "" : ":" + f.line()) + ": " + f.message();
    }
}
