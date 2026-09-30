package org.snrmbench.netbench.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.util.Json;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** netbench batch: generates the full factorial of a design file into <out-dir>/<run-id> and writes <out-dir>/index.csv, one row per cell × seed. */
@Command(name = "batch", mixinStandardHelpOptions = true,
        description = "Generate the full factorial of a design file and write index.csv.")
public final class BatchCommand implements Callable<Integer> {

    static final String GENERATED = "generated";
    static final String SKIPPED = "skipped";

    /** The manifest {@code counts} fields index.csv lists, each as a {@code counts_<field>} column. */
    static final List<String> COUNTS = List.of("nodes", "links", "products", "node_products", "facts_total",
            "facts_stated", "sentences_header", "sentences_fact", "sentences_noise", "noise_ratio_actual", "words",
            "token_estimate");

    private static final String SEED = "seed";
    private static final String GEO = "geo";
    private static final Set<String> TOP_LEVEL = Set.of("name", "fixed", "factors", "seeds");

    @Spec
    CommandSpec spec;

    @Option(names = "--design", required = true, paramLabel = "<design.json>",
            description = "The design: {name, fixed: {param: value}, factors: {param: [levels]}, seeds: [..]}.")
    Path design;

    @Option(names = "--out-dir", required = true, paramLabel = "<dir>",
            description = "Output root: runs go to <out-dir>/<run-id>, the index to <out-dir>/index.csv.")
    Path outDir;

    @Option(names = "--overwrite", description = "Regenerate cells whose run directory already exists.")
    boolean overwrite;

    /** A design file; parameter values are kept as their command-line text. */
    record Design(String name, Map<String, String> fixed, Map<String, List<String>> factors, List<Long> seeds) {
    }

    /** One cell × seed. */
    record Cell(Map<String, String> levels, String runId, String status, String reason, Map<String, Object> params,
                Map<String, Object> counts, Map<String, String> raw) {
    }

    /** Every generate parameter (the keys of {@link GenerateParams#asMap}), in flag order. */
    static List<String> parameterNames() {
        return List.copyOf(GenerateParams.defaults(0, 3, 3, "Widget").asMap().keySet());
    }

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        if (Files.exists(outDir) && !Files.isDirectory(outDir)) {
            err.println("batch: --out-dir " + outDir + " is not a directory");
            return 2;
        }
        Design d;
        try {
            d = parseDesign(Json.read(design));
        } catch (UncheckedIOException | IllegalArgumentException e) {
            err.println("batch: bad design " + design + ": " + firstLine(e.getMessage()));
            return 2;
        }
        List<Cell> cells = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> combination : combinations(d.factors())) {
            for (long seed : d.seeds()) {
                cells.add(run(d.fixed(), combination, seed, seen));
            }
        }
        Path index = outDir.resolve("index.csv");
        FlatCsv.write(index, header(), cells.stream().map(BatchCommand::indexRow).toList());

        PrintWriter out = spec.commandLine().getOut();
        long generated = cells.stream().filter(c -> c.status().equals(GENERATED)).count();
        out.println("batch " + (d.name() == null ? design.getFileName() : d.name()) + ": " + cells.size()
                + " cells, " + generated + " generated, " + (cells.size() - generated) + " skipped -> " + index);
        for (Cell c : cells) {
            if (c.status().equals(SKIPPED)) {
                out.println("  skipped " + label(c.levels()) + ": " + c.reason());
            }
        }
        out.flush();
        return 0;
    }

    // ---- the design -------------------------------------------------------------------------

    /** Parses and checks a design; any structural problem is an IllegalArgumentException. */
    static Design parseDesign(JsonNode root) {
        require(root != null && root.isObject(), "the design must be a JSON object");
        root.fieldNames().forEachRemaining(k -> require(TOP_LEVEL.contains(k),
                "unknown key \"" + k + "\" (allowed: name, fixed, factors, seeds)"));
        JsonNode name = root.get("name");
        require(name == null || name.isTextual(), "\"name\" must be a string");

        Set<String> allowed = new LinkedHashSet<>(parameterNames());
        allowed.remove(SEED);
        Map<String, String> fixed = new LinkedHashMap<>();
        JsonNode f = root.get("fixed");
        require(f == null || f.isObject(), "\"fixed\" must be an object");
        if (f != null) {
            f.fields().forEachRemaining(e -> fixed.put(parameter(e.getKey(), allowed), value(e.getKey(), e.getValue())));
        }
        Map<String, List<String>> factors = new LinkedHashMap<>();
        JsonNode fs = root.get("factors");
        require(fs == null || fs.isObject(), "\"factors\" must be an object");
        if (fs != null) {
            fs.fields().forEachRemaining(e -> {
                String p = parameter(e.getKey(), allowed);
                require(!fixed.containsKey(p), "\"" + p + "\" is both fixed and a factor");
                require(e.getValue().isArray() && !e.getValue().isEmpty(),
                        "factor \"" + p + "\" must be a non-empty array of levels");
                List<String> levels = new ArrayList<>();
                e.getValue().forEach(v -> levels.add(value(p, v)));
                factors.put(p, List.copyOf(levels));
            });
        }
        JsonNode s = root.get("seeds");
        require(s != null && s.isArray() && !s.isEmpty(), "\"seeds\" must be a non-empty array of integers");
        List<Long> seeds = new ArrayList<>();
        s.forEach(v -> {
            require(v.isIntegralNumber() && v.canConvertToLong(), "seed " + v + " is not an integer");
            seeds.add(v.asLong());
        });
        return new Design(name == null ? null : name.asText(), fixed, factors, List.copyOf(seeds));
    }

    private static String parameter(String key, Set<String> allowed) {
        require(allowed.contains(key), SEED.equals(key) ? "\"seed\" belongs in \"seeds\""
                : "unknown parameter \"" + key + "\" (parameters are the generate flags without --: "
                + String.join(", ", allowed) + ")");
        return key;
    }

    /** A parameter value as its command-line text: a number, a string or a boolean. */
    private static String value(String key, JsonNode v) {
        require(v.isNumber() || v.isTextual() || v.isBoolean(),
                "\"" + key + "\": " + v + " is not a number, string or boolean");
        return v.isNumber() ? FlatCsv.cell(v) : v.asText();
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    /** The full factorial, first factor outermost; one empty combination when there are no factors. */
    static List<Map<String, String>> combinations(Map<String, List<String>> factors) {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(new LinkedHashMap<>());
        for (Map.Entry<String, List<String>> factor : factors.entrySet()) {
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> partial : out) {
                for (String level : factor.getValue()) {
                    Map<String, String> m = new LinkedHashMap<>(partial);
                    m.put(factor.getKey(), level);
                    next.add(m);
                }
            }
            out = next;
        }
        return out;
    }

    // ---- one cell ---------------------------------------------------------------------------

    private Cell run(Map<String, String> fixed, Map<String, String> combination, long seed, Set<String> seen) {
        Map<String, String> levels = new LinkedHashMap<>(combination);
        levels.put(SEED, Long.toString(seed));
        Map<String, String> raw = new LinkedHashMap<>(fixed);
        raw.putAll(levels);
        GenerateParams p;
        try {
            p = params(raw, outDir);
        } catch (IllegalArgumentException | CommandLine.ParameterException e) {
            return new Cell(levels, null, SKIPPED, firstLine(e.getMessage()), null, null, raw);
        }
        String runId = p.defaultRunId();
        if (!seen.add(runId)) {
            return skipped(levels, runId, "same parameters as an earlier cell", p, raw);
        }
        if (Files.exists(outDir.resolve(runId)) && !overwrite) {
            return skipped(levels, runId, "run directory exists (use --overwrite)", p, raw);
        }
        try {
            Generator.Generated g = Generator.build(p, null);
            Generator.write(g, outDir, overwrite);
            @SuppressWarnings("unchecked")
            Map<String, Object> counts = (Map<String, Object>) g.manifest().get("counts");
            return new Cell(levels, runId, GENERATED, null, p.asMap(), counts, raw);
        } catch (Generator.SelfCheckFailure e) {
            return skipped(levels, runId, "self-check failed: " + firstLine(e.getMessage()), p, raw);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return skipped(levels, runId, firstLine(e.getMessage()), p, raw);
        } catch (RuntimeException e) {
            return skipped(levels, runId, firstLine(e.toString()), p, raw);
        }
    }

    private static Cell skipped(Map<String, String> levels, String runId, String reason, GenerateParams p,
                                Map<String, String> raw) {
        return new Cell(levels, runId, SKIPPED, reason, p.asMap(), null, raw);
    }

    /** The validated parameters of one cell, parsed by generate's own command line; unset parameters take generate's defaults. */
    static GenerateParams params(Map<String, String> values, Path outDir) {
        List<String> args = new ArrayList<>();
        values.forEach((k, v) -> {
            if (k.equals(GEO)) {
                require(v.equals("true") || v.equals("false"), "geo must be true or false (got \"" + v + "\")");
                if (v.equals("true")) {
                    args.add("--" + GEO);
                }
            } else {
                args.add("--" + k + "=" + v);
            }
        });
        args.add("--out-dir=" + outDir);
        GenerateCommand generate = new GenerateCommand();
        new CommandLine(generate).parseArgs(args.toArray(String[]::new));
        return generate.params().validate();
    }

    // ---- index.csv --------------------------------------------------------------------------

    /** run_id, status, reason, every generate parameter, every counts field. */
    static List<String> header() {
        List<String> h = new ArrayList<>(List.of("run_id", "status", "reason"));
        h.addAll(parameterNames());
        COUNTS.forEach(c -> h.add("counts_" + c));
        return h;
    }

    private static List<String> indexRow(Cell c) {
        List<String> row = new ArrayList<>(List.of(FlatCsv.cell(c.runId()), c.status(), FlatCsv.cell(c.reason())));
        for (String p : parameterNames()) {
            row.add(FlatCsv.cell(c.params() != null ? c.params().get(p) : c.raw().get(p)));
        }
        for (String k : COUNTS) {
            row.add(FlatCsv.cell(c.counts() == null ? null : c.counts().get(k)));
        }
        return row;
    }

    private static String label(Map<String, String> levels) {
        return levels.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(" "));
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        Iterator<String> lines = message.lines().iterator();
        return lines.hasNext() ? lines.next().trim() : "";
    }
}
