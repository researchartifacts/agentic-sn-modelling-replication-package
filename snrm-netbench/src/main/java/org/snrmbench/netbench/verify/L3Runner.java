package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.verify.SnrmClient.SnrmException;
import org.snrmbench.netbench.verify.SnrmClient.SnrmUnreachable;
import org.snrmbench.netbench.verify.SnrmClient.SubmittedRun;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Runs L3 against a running SNRM: builds the deterministic twins of B and C in a temp directory, imports B's twin and then C's. */
public final class L3Runner {

    /** The fixed seed of every baseline run. */
    public static final int SEED = 1;

    /** What L3 created on the server, so it can be deleted again. */
    private static final class Created {
        String project;
        final List<String> networks = new ArrayList<>();
        final List<String> runs = new ArrayList<>();
    }

    private L3Runner() {
    }

    /** The baseline run's params: one replication, a fixed seed, no demand noise, no timing jitter, no element time series. */
    public static Map<String, Object> baselineParams() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put(SnrmApi.PARAM_SEED, SEED);
        p.put(SnrmApi.PARAM_REPLICATIONS, 1);
        p.put(SnrmApi.PARAM_DEMAND_NOISE_CV, 0);
        p.put(SnrmApi.PARAM_TIMING_JITTER_PERIODS, 0);
        p.put(SnrmApi.PARAM_RECORD_ELEMENT_TIMESERIES, false);
        return p;
    }

    /** Runs L3. An HTTP error or a malformed answer becomes an error result; only an unreachable SNRM propagates (nothing is cleaned up then — the server cannot be reached to do it). */
    public static L3 run(SnrmClient client, RawNetwork truth, RawNetwork candidate, String projectId, String label,
                         boolean keep) {
        Created created = new Created();
        L3 result;
        try {
            result = compare(client, truth, candidate, projectId, "netbench-l3-" + label, created);
        } catch (SnrmUnreachable e) {
            throw e;
        } catch (SnrmException e) {
            result = L3.error(e.getMessage());
        }
        return result.withCleanedUp(!keep && cleanUp(client, created));
    }

    private static L3 compare(SnrmClient client, RawNetwork truth, RawNetwork candidate, String projectId,
                              String name, Created created) {
        String project = projectId;
        if (project == null) {
            project = client.createProject(name);
            created.project = project;
        }
        Map<String, String> truthTwin = DeterministicTwin.of(truth);
        Map<String, String> candidateTwin = DeterministicTwin.of(candidate);
        Path dir = null;
        try {
            dir = Files.createTempDirectory("netbench-l3-");
            write(dir.resolve("truth"), truthTwin); // the files sent are exactly the ones written here
            write(dir.resolve("candidate"), candidateTwin);

            JsonNode truthReport = client.importNetwork(truthTwin, name + "-truth", project, false);
            if (!SnrmVerification.valid(truthReport)) {
                throw new SnrmException("SNRM rejects the deterministic twin of the truth: "
                        + SnrmVerification.errors(truthReport));
            }
            String truthId = networkId(truthReport);
            created.networks.add(truthId);
            JsonNode candidateReport = client.importNetwork(candidateTwin, name + "-candidate", project, false);
            if (!SnrmVerification.valid(candidateReport)) {
                return L3.candidateNotImportable(candidateReport);
            }
            String candidateId = networkId(candidateReport);
            created.networks.add(candidateId);

            JsonNode truthTopo = client.topologicalMetrics(truthId);
            JsonNode candidateTopo = client.topologicalMetrics(candidateId);
            Map<String, L3.Comparison> topological = compareCodes(L3.TOPOLOGICAL_CODES, truthTopo, candidateTopo);
            List<L3.NodeComparison> criticality = compareCriticality(truthTopo, client.networkNodes(truthId),
                    candidateTopo, client.networkNodes(candidateId));

            SubmittedRun truthRun = client.submitBaselineRun(truthId, baselineParams());
            created.runs.add(truthRun.runId());
            SubmittedRun candidateRun = client.submitBaselineRun(candidateId, baselineParams());
            created.runs.add(candidateRun.runId());
            awaitDone(client, truthRun, "truth");
            awaitDone(client, candidateRun, "candidate");
            Map<String, L3.Comparison> simulation = compareCodes(L3.SIMULATION_CODES,
                    client.runResults(truthRun.runId()), client.runResults(candidateRun.runId()));
            return L3.ok(project, truthId, candidateId, topological, criticality, simulation);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteTree(dir);
        }
    }

    private static void awaitDone(SnrmClient client, SubmittedRun run, String side) {
        JsonNode job = client.pollJob(run.jobId());
        String status = job.path(SnrmApi.JOB_STATUS).asText();
        if (!status.equals(SnrmApi.JOB_DONE)) {
            throw new SnrmException("the baseline run of the " + side + " twin (job " + run.jobId() + ") ended "
                    + status + ": " + job.path(SnrmApi.JOB_MESSAGE).asText(""));
        }
    }

    private static String networkId(JsonNode report) {
        JsonNode id = report.get(SnrmApi.REPORT_NETWORK_ID);
        if (id == null || !id.isValueNode() || id.isNull()) {
            throw new SnrmException("the import report carries no \"" + SnrmApi.REPORT_NETWORK_ID + "\": " + report);
        }
        return id.asText();
    }

    // ---- comparisons (pure) -------------------------------------------------------------------

    /** Truth vs candidate for each code: the first value SNRM lists under that code, on either side. */
    static Map<String, L3.Comparison> compareCodes(List<String> codes, JsonNode truth, JsonNode candidate) {
        Map<String, L3.Comparison> out = new LinkedHashMap<>();
        for (String code : codes) {
            out.put(code, new L3.Comparison(value(truth, code), value(candidate, code)));
        }
        return out;
    }

    /** NODE_CRITICALITY of every node both networks have (matched by normalised name), sorted by the truth's name; a node row names its node by id, resolved through the network's node list. */
    static List<L3.NodeComparison> compareCriticality(JsonNode truthTopo, JsonNode truthNodes,
                                                      JsonNode candidateTopo, JsonNode candidateNodes) {
        Map<String, String> truthNames = names(truthNodes);
        Map<String, String> candidateNames = names(candidateNodes);
        Map<String, Double> truthValues = criticality(truthTopo, truthNames);
        Map<String, Double> candidateValues = criticality(candidateTopo, candidateNames);
        Map<String, String> candidateByNorm = new LinkedHashMap<>();
        candidateNames.values().forEach(n -> candidateByNorm.putIfAbsent(Normaliser.name(n), n));
        List<L3.NodeComparison> out = new ArrayList<>();
        truthNames.values().stream().distinct().sorted().forEach(name -> {
            String norm = Normaliser.name(name);
            if (candidateByNorm.containsKey(norm)) {
                out.add(new L3.NodeComparison(name,
                        new L3.Comparison(truthValues.get(norm), candidateValues.get(norm))));
            }
        });
        return out;
    }

    /** Node id → node name. */
    private static Map<String, String> names(JsonNode nodes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (JsonNode n : list(nodes, SnrmApi.NODES)) {
            if (n.hasNonNull(SnrmApi.NODE_ID) && n.hasNonNull(SnrmApi.NODE_NAME)) {
                out.put(n.get(SnrmApi.NODE_ID).asText(), n.get(SnrmApi.NODE_NAME).asText());
            }
        }
        return out;
    }

    /** Normalised node name → its NODE_CRITICALITY (the first row per node). */
    private static Map<String, Double> criticality(JsonNode topo, Map<String, String> names) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (JsonNode m : list(topo, SnrmApi.METRICS)) {
            String name = names.get(m.path(SnrmApi.METRIC_SCOPE_ID).asText(""));
            if (m.path(SnrmApi.METRIC_CODE).asText("").equals(L3.NODE_CRITICALITY) && name != null) {
                out.putIfAbsent(Normaliser.name(name), number(m.get(SnrmApi.METRIC_VALUE)));
            }
        }
        return out;
    }

    /** The first numeric value listed under a code, or null. */
    private static Double value(JsonNode metrics, String code) {
        for (JsonNode m : list(metrics, SnrmApi.METRICS)) {
            if (m.path(SnrmApi.METRIC_CODE).asText("").equals(code)) {
                Double v = number(m.get(SnrmApi.METRIC_VALUE));
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static Double number(JsonNode v) {
        return v != null && v.isNumber() ? v.asDouble() : null;
    }

    /** A list answer: a top-level array, or an array under {@code field}. */
    private static List<JsonNode> list(JsonNode body, String field) {
        JsonNode array = body.isArray() ? body : body.path(field);
        if (!array.isArray()) {
            throw new SnrmException("expected a JSON array (or an object with an array \"" + field + "\"), got: "
                    + body);
        }
        List<JsonNode> out = new ArrayList<>();
        array.forEach(out::add);
        return out;
    }

    // ---- files and cleanup --------------------------------------------------------------------

    private static void write(Path dir, Map<String, String> files) throws IOException {
        Files.createDirectories(dir);
        for (Map.Entry<String, String> e : files.entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteTree(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Deletes runs, then networks, then a scratch project; true when every deletion succeeded. */
    private static boolean cleanUp(SnrmClient client, Created created) {
        boolean ok = true;
        for (String run : created.runs) {
            ok &= attempt(() -> client.deleteRun(run));
        }
        for (String network : created.networks) {
            ok &= attempt(() -> client.deleteNetwork(network));
        }
        if (created.project != null) {
            ok &= attempt(() -> client.deleteProject(created.project));
        }
        return ok;
    }

    private static boolean attempt(Runnable deletion) {
        try {
            deletion.run();
            return true;
        } catch (SnrmException e) {
            return false;
        }
    }
}
