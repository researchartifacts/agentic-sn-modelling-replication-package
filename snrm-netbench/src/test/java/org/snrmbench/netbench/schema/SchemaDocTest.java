package org.snrmbench.netbench.schema;

import com.fasterxml.jackson.databind.JsonNode;
import org.snrmbench.netbench.cli.SchemaCommand;
import org.snrmbench.netbench.util.Json;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** netbench schema is stable (golden files, compared byte for byte), its JSON parses and mirrors Schema, and the output is ASCII with \n line ends. */
class SchemaDocTest {

    private static byte[] golden(String name) throws IOException {
        try (InputStream in = SchemaDocTest.class.getResourceAsStream("/golden/" + name)) {
            assertThat(in).as("golden file " + name).isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void markdownEqualsTheGoldenFile() throws IOException {
        byte[] golden = golden("schema.md");
        assertThat(SchemaDoc.markdown()).isEqualTo(new String(golden, StandardCharsets.UTF_8));
        assertThat(SchemaDoc.markdown().getBytes(StandardCharsets.UTF_8)).isEqualTo(golden);
    }

    @Test
    void jsonEqualsTheGoldenFile() throws IOException {
        byte[] golden = golden("schema.json");
        assertThat(SchemaDoc.json()).isEqualTo(new String(golden, StandardCharsets.UTF_8));
        assertThat(SchemaDoc.json().getBytes(StandardCharsets.UTF_8)).isEqualTo(golden);
    }

    @Test
    void jsonParsesAndListsEveryColumnInOrder() throws IOException {
        JsonNode root = Json.mapper().readTree(SchemaDoc.json());

        JsonNode files = root.get("files");
        assertThat(files).hasSize(Sheet.values().length);
        for (Sheet s : Sheet.values()) {
            JsonNode file = files.get(s.ordinal());
            assertThat(file.get("file").asText()).isEqualTo(s.fileName());
            List<String> names = new ArrayList<>();
            List<Boolean> required = new ArrayList<>();
            file.get("columns").forEach(c -> {
                names.add(c.get("name").asText());
                required.add(c.get("required").asBoolean());
            });
            assertThat(names).containsExactlyElementsOf(s.columns().stream().map(Column::name).toList());
            assertThat(required).containsExactlyElementsOf(s.columns().stream().map(Column::required).toList());
        }
        assertThat(root.get("node_types").findValuesAsText("name")).containsExactly("SUPPLIER", "PLANT", "DC",
                "CUSTOMER");
        assertThat(root.get("time_units").findValuesAsText("name")).containsExactly("SECOND", "MINUTE", "HOUR", "DAY",
                "WEEK", "MONTH", "YEAR");
        assertThat(root.get("time_units").get(5).get("seconds").asLong()).isEqualTo(30L * 86_400);
        assertThat(root.get("time_units").get(6).get("seconds").asLong()).isEqualTo(365L * 86_400);
        JsonNode nearest = root.get("rounding_policies").get(0);
        assertThat(nearest.get("name").asText()).isEqualTo("NEAREST");
        assertThat(nearest.get("examples").get("1.5").asInt()).isEqualTo(2);
        assertThat(root.get("conventions").get("cells")).hasSize(9);
    }

    @Test
    void outputIsAsciiWithLfLineEnds() {
        for (String text : List.of(SchemaDoc.markdown(), SchemaDoc.json())) {
            assertThat(text.chars()).allMatch(c -> c == '\n' || (c >= 0x20 && c < 0x7F));
            assertThat(text).endsWith("\n").doesNotContain("\n\n\n");
        }
    }

    @Test
    void theCommandPrintsEitherFormatAndRejectsOthers() {
        assertThat(run()).isEqualTo(SchemaDoc.markdown());
        assertThat(run("--format", "markdown")).isEqualTo(SchemaDoc.markdown());
        assertThat(run("--format", "JSON")).isEqualTo(SchemaDoc.json());

        CommandLine cmd = new CommandLine(new SchemaCommand());
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(new StringWriter(), true));
        cmd.setErr(new PrintWriter(err, true));
        assertThat(cmd.execute("--format", "xml")).isEqualTo(2);
        assertThat(err.toString()).contains("xml");
    }

    private static String run(String... args) {
        CommandLine cmd = new CommandLine(new SchemaCommand());
        StringWriter out = new StringWriter();
        cmd.setOut(new PrintWriter(out, true));
        assertThat(cmd.execute(args)).isZero();
        return out.toString();
    }
}
