package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.schema.Sheet;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Reads CSV files leniently in transport: strips a UTF-8 BOM, accepts \n or \r\n, sniffs the delimiter among,; \t | on the header line, trims every cell, skips blank lines and honours RFC 4180 quotes. */
public final class LenientCsvReader {

    private static final char[] DELIMITERS = {',', ';', '\t', '|'};

    private LenientCsvReader() {
    }

    /** Reads every {@code.csv} file of a directory; names are matched case-insensitively. */
    public static RawNetwork readDirectory(Path dir) {
        Map<String, String> files = new java.util.LinkedHashMap<>();
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> csvs = list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString())) // ordinal on every OS
                    .toList();
            for (Path p : csvs) {
                files.put(p.getFileName().toString(), Files.readString(p, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return read(files);
    }

    /** Reads named file contents (a directory, or a response split into files). */
    public static RawNetwork read(Map<String, String> files) {
        Map<Sheet, RawTable> tables = new EnumMap<>(Sheet.class);
        List<String> unrecognised = new ArrayList<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            Optional<Sheet> sheet = Sheet.byFileName(e.getKey());
            if (sheet.isEmpty()) {
                unrecognised.add(e.getKey());
            } else if (!tables.containsKey(sheet.get())) {
                tables.put(sheet.get(), readTable(sheet.get(), e.getKey(), e.getValue()));
            }
        }
        return new RawNetwork(tables, unrecognised);
    }

    /** Parses one file's content. A file with no header line yields a table with no columns. */
    public static RawTable readTable(Sheet sheet, String fileName, String content) {
        String text = !content.isEmpty() && content.charAt(0) == (char) 0xFEFF ? content.substring(1) : content;
        int[] lineStarts = lineStarts(text);
        char delimiter = sniff(firstNonBlankLine(text));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setTrim(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .build();
        List<String> headers = null;
        int headerLine = 1; // an empty file's (missing) header is still line 1
        List<RawTable.Row> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            for (CSVRecord record : parser) {
                List<String> cells = new ArrayList<>(record.size());
                for (String v : record) {
                    cells.add(v == null ? "" : v.trim());
                }
                if (cells.stream().allMatch(String::isEmpty) && cells.size() == 1) {
                    continue; // a whitespace-only line
                }
                int line = lineOf(text, lineStarts, record.getCharacterPosition());
                if (headers == null) {
                    headers = cells;
                    headerLine = line;
                    continue;
                }
                rows.add(new RawTable.Row(line, fit(cells, headers.size()), cells.size()));
            }
        } catch (IOException | UncheckedIOException e) {
            throw new UncheckedIOException(new IOException(fileName + ": " + e.getMessage(), e));
        }
        return new RawTable(sheet, fileName, headerLine, headers == null ? List.of() : headers, rows);
    }

    private static List<String> fit(List<String> cells, int width) {
        List<String> out = new ArrayList<>(cells.subList(0, Math.min(width, cells.size())));
        while (out.size() < width) {
            out.add("");
        }
        return out;
    }

    /** The delimiter occurring most often outside quotes; ties resolve in the order {@code,; \t |}. */
    static char sniff(String headerLine) {
        int[] counts = new int[DELIMITERS.length];
        boolean quoted = false;
        for (char c : headerLine.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                for (int i = 0; i < DELIMITERS.length; i++) {
                    if (c == DELIMITERS[i]) {
                        counts[i]++;
                    }
                }
            }
        }
        int best = 0;
        for (int i = 1; i < DELIMITERS.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return DELIMITERS[best];
    }

    private static String firstNonBlankLine(String text) {
        return Arrays.stream(text.split("\r?\n", -1)).filter(l -> !l.isBlank()).findFirst().orElse("");
    }

    private static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The 1-based line holding the first non-newline character at or after {@code position}. */
    private static int lineOf(String text, int[] lineStarts, long position) {
        int pos = (int) position;
        while (pos < text.length() && (text.charAt(pos) == '\n' || text.charAt(pos) == '\r')) {
            pos++;
        }
        int idx = Arrays.binarySearch(lineStarts, pos);
        return (idx >= 0 ? idx : -idx - 2) + 1;
    }
}
