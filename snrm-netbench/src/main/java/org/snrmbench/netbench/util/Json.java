package org.snrmbench.netbench.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** JSON with machine-independent bytes: \n line endings (Jackson's default indenter uses the platform separator), two-space indent, plain BigDecimals, and a trailing newline. */
public final class Json {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private static final DefaultPrettyPrinter PRETTY;

    static {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter p = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                        .withArrayEmptySeparator("")
                        .withObjectEmptySeparator(""));
        p.indentObjectsWith(indenter);
        p.indentArraysWith(indenter);
        PRETTY = p;
    }

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** Pretty JSON, insertion-ordered, ending in a newline. */
    public static String pretty(Object value) {
        try {
            return MAPPER.writer(PRETTY).writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One compact line (a JSONL record), without the newline. */
    public static String line(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Compact JSON with every object's keys sorted: the canonical form that is hashed. */
    public static String canonical(Object value) {
        try {
            JsonNode tree = CANONICAL.valueToTree(value);
            return CANONICAL.writeValueAsString(CANONICAL.treeToValue(tree, Object.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void write(Path file, Object value) {
        try {
            Files.writeString(file, pretty(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode read(Path file) {
        try {
            return MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
