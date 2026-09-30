package org.snrmbench.netbench.util;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** JSON Lines: one compact record per line, {@code \n}-terminated. */
public final class Jsonl {

    private Jsonl() {
    }

    public static String render(List<?> records) {
        StringBuilder sb = new StringBuilder();
        for (Object r : records) {
            sb.append(Json.line(r)).append('\n');
        }
        return sb.toString();
    }

    public static void write(Path file, List<?> records) {
        try {
            Files.writeString(file, render(records), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> List<T> read(Path file, Class<T> type) {
        try {
            List<T> out = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    out.add(Json.mapper().readValue(line, type));
                }
            }
            return out;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(file + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
