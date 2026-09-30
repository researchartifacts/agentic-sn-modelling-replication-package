package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.snrmbench.netbench.util.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The only class that opens network connections: a small client for the SNRM REST API. */
public final class SnrmClient implements AutoCloseable {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    /** The first job poll waits this long; the interval then doubles up to {@link #MAX_POLL_INTERVAL}. */
    public static final Duration FIRST_POLL_INTERVAL = Duration.ofMillis(100);
    public static final Duration MAX_POLL_INTERVAL = Duration.ofSeconds(2);
    /** How long {@link #pollJob} waits for a job to finish before giving up. */
    public static final Duration MAX_JOB_WAIT = Duration.ofMinutes(10);

    private static final Set<String> TERMINAL = Set.of(SnrmApi.JOB_DONE, SnrmApi.JOB_FAILED, SnrmApi.JOB_CANCELLED);

    /** Something went wrong talking to SNRM. */
    public static class SnrmException extends RuntimeException {
        public SnrmException(String message) {
            super(message);
        }

        public SnrmException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** SNRM could not be reached at all (exit code 5). */
    public static final class SnrmUnreachable extends SnrmException {
        public SnrmUnreachable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** SNRM answered with an HTTP error status. */
    public static final class SnrmHttpError extends SnrmException {
        private final int status;
        private final String body;

        public SnrmHttpError(String request, int status, String body) {
            super(request + " answered HTTP " + status + (body == null || body.isBlank() ? "" : ": " + body.strip()));
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }
    }

    /** A submitted simulation: the job to poll and the run whose results to read. */
    public record SubmittedRun(String jobId, String runId) {
    }

    private final String base;
    private final String user;
    private final String pass;
    private final Duration maxJobWait;
    private final HttpClient http;
    private String token;

    public SnrmClient(String baseUrl, String token, String user, String pass) {
        this(baseUrl, token, user, pass, MAX_JOB_WAIT);
    }

    public SnrmClient(String baseUrl, String token, String user, String pass, Duration maxJobWait) {
        this.base = checkUrl(baseUrl);
        this.token = token;
        this.user = user;
        this.pass = pass;
        this.maxJobWait = maxJobWait;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** The base URL without a trailing slash; throws IllegalArgumentException unless it is an absolute http(s) URL with a host. */
    public static String checkUrl(String url) {
        String trimmed = url == null ? "" : url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            URI u = new URI(trimmed);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && u.getHost() != null) {
                return trimmed;
            }
        } catch (java.net.URISyntaxException e) {
            // reported below
        }
        throw new IllegalArgumentException("--snrm-url must be an http(s) URL such as http://localhost:8080 (got \""
                + url + "\")");
    }

    // ---- endpoints ----------------------------------------------------------------------------

    /** {@code POST /auth/login}: obtains and keeps the JWT. */
    public String login() {
        if (user == null || pass == null) {
            throw new IllegalStateException("no SNRM token and no user/password to log in with");
        }
        ObjectNode body = Json.mapper().createObjectNode()
                .put(SnrmApi.LOGIN_USERNAME, user).put(SnrmApi.LOGIN_PASSWORD, pass);
        JsonNode r = send("POST", SnrmApi.LOGIN_PATH, json(body), "application/json", false);
        token = text(r, SnrmApi.LOGIN_TOKEN, "POST " + SnrmApi.LOGIN_PATH);
        return token;
    }

    /** {@code POST /projects}: creates a project and returns its id. */
    public String createProject(String name) {
        ObjectNode body = Json.mapper().createObjectNode().put(SnrmApi.PROJECT_NAME, name);
        JsonNode r = send("POST", SnrmApi.PROJECTS_PATH, json(body), "application/json", true);
        return text(r, SnrmApi.PROJECT_ID, "POST " + SnrmApi.PROJECTS_PATH);
    }

    /** POST /networks/import as multipart/form-data: one files part per CSV file (with its file name), then name, projectId (only when given) and validateOnly. */
    public JsonNode importNetwork(Map<String, String> files, String name, String projectId, boolean validateOnly) {
        StringBuilder all = new StringBuilder();
        files.forEach((k, v) -> all.append(k).append(v));
        String boundary = "netbench-" + Json.sha256(all.toString()).substring(0, 32);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        files.forEach((fileName, content) -> part(out, boundary, SnrmApi.IMPORT_PART_FILES, fileName, content));
        if (name != null) {
            part(out, boundary, SnrmApi.IMPORT_PART_NAME, null, name);
        }
        if (projectId != null) {
            part(out, boundary, SnrmApi.IMPORT_PART_PROJECT, null, projectId);
        }
        part(out, boundary, SnrmApi.IMPORT_PART_VALIDATE_ONLY, null, Boolean.toString(validateOnly));
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send("POST", SnrmApi.IMPORT_PATH, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()),
                "multipart/form-data; boundary=" + boundary, true);
    }

    /** {@code GET /networks/{id}/metrics/topological}. */
    public JsonNode topologicalMetrics(String networkId) {
        return get(SnrmApi.TOPOLOGICAL_PATH.formatted(networkId));
    }

    /** {@code GET /networks/{id}/nodes}: used to name NODE_CRITICALITY rows. */
    public JsonNode networkNodes(String networkId) {
        return get(SnrmApi.NETWORK_NODES_PATH.formatted(networkId));
    }

    /** {@code POST /simulations} without a scenario: a baseline run. */
    public SubmittedRun submitBaselineRun(String networkId, Map<String, Object> params) {
        ObjectNode body = Json.mapper().createObjectNode();
        body.set(SnrmApi.SIMULATION_NETWORK_ID, id(networkId));
        body.set(SnrmApi.SIMULATION_PARAMS, Json.mapper().valueToTree(params));
        JsonNode r = send("POST", SnrmApi.SIMULATIONS_PATH, json(body), "application/json", true);
        String request = "POST " + SnrmApi.SIMULATIONS_PATH;
        return new SubmittedRun(text(r, SnrmApi.SIMULATION_JOB_ID, request), text(r, SnrmApi.SIMULATION_RUN_ID, request));
    }

    /** Polls GET /jobs/{jobId} until the job is DONE, FAILED or CANCELLED and returns that last status; waits at most the client's maximum job wait. */
    public JsonNode pollJob(String jobId) {
        long deadline = System.nanoTime() + maxJobWait.toNanos();
        Duration interval = FIRST_POLL_INTERVAL;
        while (true) {
            JsonNode job = get(SnrmApi.JOB_PATH.formatted(jobId));
            String status = job.path(SnrmApi.JOB_STATUS).asText("");
            if (TERMINAL.contains(status)) {
                return job;
            }
            if (System.nanoTime() + interval.toNanos() > deadline) {
                throw new SnrmException("job " + jobId + " is still " + status + " after " + maxJobWait.toSeconds()
                        + " s");
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SnrmException("interrupted while waiting for job " + jobId, e);
            }
            interval = interval.multipliedBy(2).compareTo(MAX_POLL_INTERVAL) > 0 ? MAX_POLL_INTERVAL
                    : interval.multipliedBy(2);
        }
    }

    /** {@code GET /simulations/{runId}/results}: the run's metric suite. */
    public JsonNode runResults(String runId) {
        return get(SnrmApi.SIMULATION_RESULTS_PATH.formatted(runId));
    }

    public void deleteRun(String runId) {
        send("DELETE", SnrmApi.SIMULATION_PATH.formatted(runId), HttpRequest.BodyPublishers.noBody(), null, true);
    }

    public void deleteNetwork(String networkId) {
        send("DELETE", SnrmApi.NETWORK_PATH.formatted(networkId), HttpRequest.BodyPublishers.noBody(), null, true);
    }

    public void deleteProject(String projectId) {
        send("DELETE", SnrmApi.PROJECT_PATH.formatted(projectId), HttpRequest.BodyPublishers.noBody(), null, true);
    }

    @Override
    public void close() {
        http.close();
    }

    // ---- transport ----------------------------------------------------------------------------

    private JsonNode get(String path) {
        return send("GET", path, HttpRequest.BodyPublishers.noBody(), null, true);
    }

    private JsonNode send(String method, String path, HttpRequest.BodyPublisher body, String contentType,
                          boolean authenticated) {
        String request = method + " " + path;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(REQUEST_TIMEOUT)
                .method(method, body).header("Accept", "application/json");
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        if (authenticated) {
            b.header("Authorization", "Bearer " + (token != null ? token : login()));
        }
        HttpResponse<String> r;
        try {
            r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException | HttpTimeoutException | UnknownHostException e) {
            throw unreachable(request, e);
        } catch (IOException e) {
            if (e.getCause() instanceof UnresolvedAddressException) {
                throw unreachable(request, e);
            }
            throw new SnrmException(request + " failed: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SnrmException(request + " was interrupted", e);
        }
        if (r.statusCode() >= 400) {
            throw new SnrmHttpError(request, r.statusCode(), r.body());
        }
        if (r.body() == null || r.body().isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return Json.mapper().readTree(r.body());
        } catch (IOException e) {
            throw new SnrmException(request + " answered something that is not JSON: " + r.body().strip(), e);
        }
    }

    private SnrmUnreachable unreachable(String request, Exception e) {
        return new SnrmUnreachable("SNRM at " + base + " is unreachable (" + request + ": "
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")", e);
    }

    private static HttpRequest.BodyPublisher json(JsonNode body) {
        return HttpRequest.BodyPublishers.ofString(Json.line(body), StandardCharsets.UTF_8);
    }

    private static void part(ByteArrayOutputStream out, String boundary, String name, String fileName, String value) {
        StringBuilder h = new StringBuilder("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append('"');
        if (fileName != null) {
            h.append("; filename=\"").append(fileName.replace("\"", "%22")).append('"');
        }
        h.append("\r\n");
        if (fileName != null) {
            h.append("Content-Type: text/csv; charset=UTF-8\r\n");
        }
        h.append("\r\n");
        out.writeBytes(h.toString().getBytes(StandardCharsets.UTF_8));
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /** A numeric id as a JSON number (SNRM's ids are database keys), anything else as a string. */
    private static JsonNode id(String id) {
        return id.matches("\\d{1,18}") ? Json.mapper().getNodeFactory().numberNode(Long.parseLong(id))
                : Json.mapper().getNodeFactory().textNode(id);
    }

    /** A required scalar field of a response, as text. */
    private static String text(JsonNode response, String field, String request) {
        JsonNode v = response.get(field);
        if (v == null || !v.isValueNode() || v.isNull() || v.asText().isEmpty()) {
            throw new SnrmException(request + " answered without \"" + field + "\": " + response);
        }
        return v.asText();
    }
}
