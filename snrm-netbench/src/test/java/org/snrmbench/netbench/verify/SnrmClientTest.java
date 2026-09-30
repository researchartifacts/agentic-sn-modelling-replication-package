package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.verify.SnrmClient.SnrmException;
import org.snrmbench.netbench.verify.SnrmClient.SnrmHttpError;
import org.snrmbench.netbench.verify.SnrmClient.SnrmUnreachable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SNRM client against the mock: authentication, the multipart import, job polling and its failures. */
class SnrmClientTest {

    private final MockSnrm mock = new MockSnrm();

    @AfterEach
    void stop() {
        mock.close();
    }

    @Test
    void logsInOnceAndSendsTheTokenOnEveryCall() {
        try (SnrmClient client = new SnrmClient(mock.url(), null, MockSnrm.USER, MockSnrm.PASS)) {
            client.createProject("a");
            client.createProject("b");
        }

        assertThat(mock.calls()).containsExactly("POST " + SnrmApi.LOGIN_PATH, "POST " + SnrmApi.PROJECTS_PATH,
                "POST " + SnrmApi.PROJECTS_PATH);
        assertThat(mock.requests("POST", SnrmApi.PROJECTS_PATH))
                .allSatisfy(r -> assertThat(r.authorization()).isEqualTo("Bearer " + MockSnrm.TOKEN));
    }

    @Test
    void importIsMultipartWithOnePartPerFileThenNameProjectAndValidateOnly() {
        Map<String, String> files = CsvEdits.sample("minimal-6-node");
        JsonNode report;
        try (SnrmClient client = new SnrmClient(mock.url(), MockSnrm.TOKEN, null, null)) {
            report = client.importNetwork(files, "my-network", "42", true);
        }

        assertThat(report.get(SnrmApi.REPORT_VALID).asBoolean()).isTrue();
        MockSnrm.Request r = mock.requests("POST", SnrmApi.IMPORT_PATH).get(0);
        assertThat(r.contentType()).startsWith("multipart/form-data; boundary=");
        List<MockSnrm.Part> parts = r.parts();
        assertThat(parts).extracting(MockSnrm.Part::name).containsExactly("files", "files", "files", "files", "files",
                "name", "projectId", "validateOnly");
        assertThat(parts.subList(0, 5)).extracting(MockSnrm.Part::fileName)
                .containsExactlyElementsOf(files.keySet());
        assertThat(parts.subList(0, 5)).extracting(MockSnrm.Part::value).containsExactlyElementsOf(files.values());
        assertThat(r.part("name")).isEqualTo("my-network");
        assertThat(r.part("projectId")).isEqualTo("42");
        assertThat(r.part("validateOnly")).isEqualTo("true");
    }

    @Test
    void importWithoutAProjectSendsNoProjectPart() {
        try (SnrmClient client = new SnrmClient(mock.url(), MockSnrm.TOKEN, null, null)) {
            client.importNetwork(CsvEdits.sample("minimal-6-node"), "n", null, false);
        }

        assertThat(mock.requests("POST", SnrmApi.IMPORT_PATH).get(0).part("projectId")).isNull();
    }

    @Test
    void pollsAJobThroughQueuedAndRunningUntilDone() {
        JsonNode job;
        try (SnrmClient client = new SnrmClient(mock.url(), MockSnrm.TOKEN, null, null)) {
            String network = client.importNetwork(CsvEdits.sample("minimal-6-node"), "n", null, false)
                    .get(SnrmApi.REPORT_NETWORK_ID).asText();
            SnrmClient.SubmittedRun run = client.submitBaselineRun(network, L3Runner.baselineParams());
            job = client.pollJob(run.jobId());
        }

        assertThat(job.get(SnrmApi.JOB_STATUS).asText()).isEqualTo(SnrmApi.JOB_DONE);
        assertThat(mock.requests("GET", "/api/v1/jobs/")).hasSize(3);
    }

    @Test
    void aJobThatNeverFinishesIsABoundedWait() {
        mock.pollsBeforeDone = Integer.MAX_VALUE;
        try (SnrmClient client = new SnrmClient(mock.url(), MockSnrm.TOKEN, null, null, Duration.ofMillis(400))) {
            assertThatThrownBy(() -> client.pollJob("job-1"))
                    .isInstanceOf(SnrmException.class).isNotInstanceOf(SnrmUnreachable.class)
                    .hasMessageContaining("still RUNNING");
        }
    }

    @Test
    void anHttpErrorCarriesItsStatusAndBody() {
        try (SnrmClient client = new SnrmClient(mock.url(), "wrong-token", null, null)) {
            assertThatThrownBy(() -> client.createProject("p"))
                    .isInstanceOfSatisfying(SnrmHttpError.class, e -> {
                        assertThat(e.status()).isEqualTo(401);
                        assertThat(e.body()).contains("invalid token");
                        assertThat(e.getMessage()).contains("POST " + SnrmApi.PROJECTS_PATH, "401");
                    });
        }
        try (SnrmClient client = new SnrmClient(mock.url(), null, MockSnrm.USER, "wrong")) {
            assertThatThrownBy(client::login).isInstanceOf(SnrmHttpError.class).hasMessageContaining("401");
        }
    }

    @Test
    void aClosedPortIsUnreachable() {
        try (SnrmClient client = new SnrmClient(MockSnrm.closedUrl(), MockSnrm.TOKEN, null, null)) {
            assertThatThrownBy(() -> client.importNetwork(Map.of(), "n", null, true))
                    .isInstanceOf(SnrmUnreachable.class).hasMessageContaining("unreachable");
        }
    }

    @Test
    void aBaseUrlMustBeAnAbsoluteHttpUrl() {
        assertThat(SnrmClient.checkUrl("http://localhost:8080/")).isEqualTo("http://localhost:8080");
        assertThatThrownBy(() -> SnrmClient.checkUrl("localhost:8080")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnrmClient.checkUrl("ftp://host")).isInstanceOf(IllegalArgumentException.class);
    }
}
