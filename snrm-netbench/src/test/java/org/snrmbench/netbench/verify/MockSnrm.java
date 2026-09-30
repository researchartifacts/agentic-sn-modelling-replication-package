package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A mock SNRM on a loopback port: exactly the SnrmApi endpoints, answering canned JSON, and recording every request. */
public final class MockSnrm implements AutoCloseable {

    public static final String USER = "netbench";
    public static final String PASS = "secret";
    public static final String TOKEN = "mock-jwt";

    /** Network-scoped topological values the mock answers for every network. */
    public static final Map<String, Double> TOPOLOGY = orderedMap("DENSITY", 0.25, "SPOF_NODE_COUNT", 1.0,
            "SPOF_ARC_COUNT", 2.0, "SPOF_COUNT", 3.0, "AVG_PATH", 2.5, "CLUSTERING", 0.1, "ROBUSTNESS_RANDOM", 0.4,
            "ROBUSTNESS_TARGETED", 0.3);
    /** Baseline-run values the mock answers for every run (TOTAL_COST scaled for candidate networks). */
    public static final Map<String, Double> SIMULATION = orderedMap("FILL_RATE", 0.98, "SERVICE_LEVEL", 0.9,
            "MIN_FILL_RATE", 0.8, "TOTAL_COST", 12000.0, "CVAR_COST", 12500.0, "AVG_INVENTORY", 22.0,
            "AVG_PIPELINE", 0.0);

    /** One multipart part. */
    public record Part(String name, String fileName, String value) {
    }

    /** One request as received. */
    public record Request(String method, String path, String authorization, String contentType, String body) {

        /** The multipart parts of an import request, in order. */
        public List<Part> parts() {
            Matcher m = Pattern.compile("boundary=(\\S+)").matcher(contentType == null ? "" : contentType);
            if (!m.find()) {
                return List.of();
            }
            List<Part> out = new ArrayList<>();
            for (String chunk : body.split("--" + Pattern.quote(m.group(1)))) {
                int split = chunk.indexOf("\r\n\r\n");
                if (split < 0) {
                    continue;
                }
                String headers = chunk.substring(0, split);
                String value = chunk.substring(split + 4);
                value = value.endsWith("\r\n") ? value.substring(0, value.length() - 2) : value;
                Matcher name = Pattern.compile("name=\"([^\"]*)\"").matcher(headers);
                Matcher file = Pattern.compile("filename=\"([^\"]*)\"").matcher(headers);
                out.add(new Part(name.find() ? name.group(1) : null, file.find() ? file.group(1) : null, value));
            }
            return out;
        }

        public String part(String name) {
            return parts().stream().filter(p -> p.name().equals(name)).map(Part::value).findFirst().orElse(null);
        }

        public JsonNode json() {
            try {
                return Json.mapper().readTree(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** A network the mock created: its name and node ids in nodes.csv order. */
    private record Network(String name, Map<Integer, String> nodes) {
    }

    // ---- configuration ----
    /** {@code valid} of every validateOnly import. */
    public volatile boolean validateOnlyValid = true;
    /** Codes listed in {@code warnings[]} of every validateOnly import. */
    public volatile List<String> validateOnlyWarnings = List.of();
    /** Committed imports whose network name matches are rejected. */
    public volatile Predicate<String> rejectImport = name -> false;
    /** TOTAL_COST of a network whose name ends in {@code -candidate} is multiplied by this. */
    public volatile double candidateTotalCostFactor = 1.0;
    /** HTTP status of the topological metrics endpoint (200: answer normally). */
    public volatile int topologyStatus = 200;
    /** How many polls answer QUEUED/RUNNING before a job is DONE. */
    public volatile int pollsBeforeDone = 2;

    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, Network> networks = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<String, Integer> jobPolls = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<Integer, Integer> runNetwork = Collections.synchronizedMap(new LinkedHashMap<>());
    private int nextId = 1;

    public MockSnrm() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A loopback URL on which nothing listens. */
    public static String closedUrl() {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "http://127.0.0.1:" + s.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every request so far, in order. */
    public List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    /** "METHOD path" of every request so far. */
    public List<String> calls() {
        return requests().stream().map(r -> r.method() + " " + r.path()).toList();
    }

    public List<Request> requests(String method, String pathPrefix) {
        return requests().stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ---- dispatch -----------------------------------------------------------------------------

    private void handle(HttpExchange x) throws IOException {
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String method = x.getRequestMethod();
        String path = x.getRequestURI().getPath();
        Request r = new Request(method, path, x.getRequestHeaders().getFirst("Authorization"),
                x.getRequestHeaders().getFirst("Content-Type"), body);
        requests.add(r);
        try {
            if (method.equals("POST") && path.equals(SnrmApi.LOGIN_PATH)) {
                JsonNode j = r.json();
                if (USER.equals(j.path(SnrmApi.LOGIN_USERNAME).asText())
                        && PASS.equals(j.path(SnrmApi.LOGIN_PASSWORD).asText())) {
                    send(x, 200, object().put(SnrmApi.LOGIN_TOKEN, TOKEN));
                } else {
                    send(x, 401, object().put("title", "Bad credentials"));
                }
                return;
            }
            if (!("Bearer " + TOKEN).equals(r.authorization())) {
                send(x, 401, object().put("title", "Unauthorized").put("detail", "missing or invalid token"));
                return;
            }
            route(x, r);
        } catch (RuntimeException e) {
            send(x, 500, object().put("title", e.toString()));
        }
    }

    private void route(HttpExchange x, Request r) throws IOException {
        String m = r.method();
        String p = r.path();
        String[] seg = p.split("/"); // "", "api", "v1", resource, id,...
        if (m.equals("POST") && p.equals(SnrmApi.PROJECTS_PATH)) {
            send(x, 201, object().put(SnrmApi.PROJECT_ID, id()).put(SnrmApi.PROJECT_NAME,
                    r.json().path(SnrmApi.PROJECT_NAME).asText()));
        } else if (m.equals("POST") && p.equals(SnrmApi.IMPORT_PATH)) {
            send(x, 200, importReport(r));
        } else if (m.equals("GET") && p.endsWith("/metrics/topological")) {
            if (topologyStatus != 200) {
                send(x, topologyStatus, object().put("title", "metric engine failure"));
                return;
            }
            send(x, 200, topology(networks.get(Integer.parseInt(seg[4]))));
        } else if (m.equals("GET") && p.endsWith("/nodes")) {
            ArrayNode a = Json.mapper().createArrayNode();
            networks.get(Integer.parseInt(seg[4])).nodes().forEach((id, name) ->
                    a.add(object().put(SnrmApi.NODE_ID, id).put(SnrmApi.NODE_NAME, name)));
            send(x, 200, a);
        } else if (m.equals("POST") && p.equals(SnrmApi.SIMULATIONS_PATH)) {
            int network = r.json().path(SnrmApi.SIMULATION_NETWORK_ID).asInt();
            int run = id();
            runNetwork.put(run, network);
            jobPolls.put("job-" + run, 0);
            send(x, 202, object().put(SnrmApi.SIMULATION_JOB_ID, "job-" + run).put(SnrmApi.SIMULATION_RUN_ID, run));
        } else if (m.equals("GET") && p.startsWith("/api/v1/jobs/")) {
            int polls = jobPolls.merge(seg[4], 1, Integer::sum);
            String status = polls > pollsBeforeDone ? SnrmApi.JOB_DONE : polls == 1 ? "QUEUED" : "RUNNING";
            send(x, 200, object().put(SnrmApi.JOB_STATUS, status).put("progress", polls > pollsBeforeDone ? 1.0 : 0.5));
        } else if (m.equals("GET") && p.endsWith("/results")) {
            send(x, 200, results(networks.get(runNetwork.get(Integer.parseInt(seg[4])))));
        } else if (m.equals("DELETE")) {
            x.sendResponseHeaders(204, -1);
            x.close();
        } else {
            send(x, 404, object().put("title", "no mock for " + m + " " + p));
        }
    }

    private ObjectNode importReport(Request r) {
        boolean validateOnly = Boolean.parseBoolean(r.part(SnrmApi.IMPORT_PART_VALIDATE_ONLY));
        String name = r.part(SnrmApi.IMPORT_PART_NAME);
        ObjectNode report = object();
        ArrayNode errors = Json.mapper().createArrayNode();
        ArrayNode warnings = Json.mapper().createArrayNode();
        boolean valid = validateOnly ? validateOnlyValid : !rejectImport.test(name);
        if (!valid) {
            errors.add(object().put(SnrmApi.REPORT_ITEM_CODE, "NO_CUSTOMER_NODE").put("message", "no customer"));
        }
        if (validateOnly) {
            validateOnlyWarnings.forEach(c -> warnings.add(object().put(SnrmApi.REPORT_ITEM_CODE, c)
                    .put("message", "mock " + c)));
        }
        report.put(SnrmApi.REPORT_VALID, valid).put("committed", valid && !validateOnly);
        report.set(SnrmApi.REPORT_ERRORS, errors);
        report.set(SnrmApi.REPORT_WARNINGS, warnings);
        if (valid && !validateOnly) {
            Map<Integer, String> nodes = new LinkedHashMap<>();
            r.parts().stream().filter(pt -> "nodes.csv".equalsIgnoreCase(pt.fileName())).findFirst().ifPresent(pt -> {
                RawTable t = LenientCsvReader.readTable(Sheet.NODES, pt.fileName(), pt.value());
                t.rows().forEach(row -> nodes.put(id(), t.cell(row, Schema.NODE_NAME)));
            });
            int network = id();
            networks.put(network, new Network(name, nodes));
            report.put(SnrmApi.REPORT_NETWORK_ID, network);
        }
        return report;
    }

    private static ObjectNode topology(Network n) {
        ArrayNode a = Json.mapper().createArrayNode();
        TOPOLOGY.forEach((code, v) -> a.add(object().put(SnrmApi.METRIC_CODE, code).put("scope", "NETWORK")
                .put(SnrmApi.METRIC_VALUE, v)));
        n.nodes().forEach((id, name) -> a.add(object().put(SnrmApi.METRIC_CODE, "NODE_CRITICALITY")
                .put("scope", "NODE").put(SnrmApi.METRIC_SCOPE_ID, id).put(SnrmApi.METRIC_VALUE, criticality(name))));
        ObjectNode o = object();
        o.set(SnrmApi.METRICS, a);
        return o;
    }

    /** A deterministic criticality per node name. */
    public static double criticality(String name) {
        return (name.length() % 7) / 10.0;
    }

    private ArrayNode results(Network n) {
        ArrayNode a = Json.mapper().createArrayNode();
        SIMULATION.forEach((code, v) -> {
            double value = code.equals("TOTAL_COST") && n.name().endsWith("-candidate") ? v * candidateTotalCostFactor : v;
            a.add(object().put(SnrmApi.METRIC_CODE, code).put(SnrmApi.METRIC_VALUE, value).putNull("ciLow")
                    .putNull("ciHigh"));
        });
        return a;
    }

    private synchronized int id() {
        return nextId++;
    }

    private static ObjectNode object() {
        return Json.mapper().createObjectNode();
    }

    private static void send(HttpExchange x, int status, JsonNode body) throws IOException {
        byte[] bytes = Json.line(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", status >= 400 ? "application/problem+json" : "application/json");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    private static Map<String, Double> orderedMap(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }
}
