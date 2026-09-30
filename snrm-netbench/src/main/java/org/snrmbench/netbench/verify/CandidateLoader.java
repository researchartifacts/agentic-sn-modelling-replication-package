package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.csv.RawTable;
import org.snrmbench.netbench.csv.ResponseSplitter;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.Severity;
import org.snrmbench.netbench.validate.SnrmRules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Loads a candidate: a directory of CSV files, or a response file split into files by ResponseSplitter (the split files are written to the split directory for inspection). */
public final class CandidateLoader {

    public static final String SOURCE_FILE = "file";
    public static final String SOURCE_DIRECTORY = "directory";

    private CandidateLoader() {
    }

    /** Loads a candidate directory or response file. */
    public static Candidate load(Path candidate, Path splitDir) {
        if (Files.isDirectory(candidate)) {
            return build(readCsvFiles(candidate), SOURCE_DIRECTORY, List.of());
        }
        ResponseSplitter.Split split = ResponseSplitter.split(readText(candidate));
        writeSplit(split.files(), splitDir != null ? splitDir
                : candidate.resolveSibling(candidate.getFileName() + ".split"));
        return build(split.files(), SOURCE_FILE, split.findings());
    }

    /** The files #load scores (file name to content): a directory's.csv files in name order, or a response file's split files. */
    public static Map<String, String> files(Path candidate) {
        return Files.isDirectory(candidate) ? readCsvFiles(candidate)
                : ResponseSplitter.split(readText(candidate)).files();
    }

    /** A candidate from named file contents (file name to content). */
    public static Candidate fromFiles(Map<String, String> files, String source) {
        return build(files, source, List.of());
    }

    private static Candidate build(Map<String, String> files, String source, List<Finding> prior) {
        List<Finding> envelope = new ArrayList<>(prior);
        Map<Sheet, RawTable> tables = new EnumMap<>(Sheet.class);
        Map<Sheet, String> found = new EnumMap<>(Sheet.class);
        List<String> unrecognised = new ArrayList<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            Optional<Sheet> sheet = Sheet.byFileName(e.getKey());
            if (sheet.isEmpty()) {
                unrecognised.add(e.getKey());
            } else if (found.containsKey(sheet.get())) {
                envelope.add(Finding.row(Code.ENVELOPE_PARSE_ERROR, e.getKey(), null, null, e.getKey()
                        + " is a second " + sheet.get().fileName() + "; " + found.get(sheet.get()) + " is used"));
            } else {
                found.put(sheet.get(), e.getKey());
                readTable(sheet.get(), e.getKey(), e.getValue(), envelope).ifPresent(t -> tables.put(sheet.get(), t));
            }
        }
        List<String> filesFound = new ArrayList<>();
        List<String> filesMissing = new ArrayList<>();
        for (Sheet s : Sheet.values()) {
            if (found.containsKey(s)) {
                filesFound.add(s.fileName());
            } else {
                filesMissing.add(s.fileName());
                envelope.add(fileMissing(s));
            }
        }
        if (!found.containsKey(Sheet.NETWORK_META)) {
            envelope.add(Finding.row(Code.NETWORK_META_DEFAULTED, Sheet.NETWORK_META.fileName(), null, null,
                    Sheet.NETWORK_META.fileName() + " is absent; 1 DAY / NEAREST / " + SnrmRules.DEFAULT_HORIZON
                            + " periods assumed, and the network_meta facts are scored omitted"));
        }
        return new Candidate(new RawNetwork(tables, unrecognised), source, filesFound, filesMissing, envelope);
    }

    private static Optional<RawTable> readTable(Sheet sheet, String name, String content, List<Finding> envelope) {
        try {
            return Optional.of(LenientCsvReader.readTable(sheet, name, content));
        } catch (UncheckedIOException e) {
            // the reader's message already starts with the file name
            envelope.add(Finding.row(Code.ENVELOPE_PARSE_ERROR, name, null, null,
                    e.getCause().getMessage() + "; the file cannot be read as CSV and is ignored"));
            return Optional.empty();
        }
    }

    /** FILE_MISSING: an error for the two sheets a network cannot do without, a warning otherwise. */
    private static Finding fileMissing(Sheet sheet) {
        boolean essential = sheet == Sheet.NODES || sheet == Sheet.LINKS;
        return new Finding(Code.FILE_MISSING, essential ? Severity.ERROR : Severity.WARNING, sheet.fileName(), null,
                null, null, sheet.fileName() + " is missing from the candidate");
    }

    /** Every {@code.csv} file of a directory (name to content), in name order. */
    private static Map<String, String> readCsvFiles(Path dir) {
        Map<String, String> files = new LinkedHashMap<>();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path p : list.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString())) // ordinal on every OS
                    .toList()) {
                files.put(p.getFileName().toString(), readText(p));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    /** UTF-8, with malformed bytes replaced rather than fatal: a candidate is read leniently. */
    private static String readText(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the split files; a canonical file this split did not find is removed from an earlier run. */
    private static void writeSplit(Map<String, String> files, Path dir) {
        try {
            Files.createDirectories(dir);
            for (Sheet s : Sheet.values()) {
                Path p = dir.resolve(s.fileName());
                String content = files.get(s.fileName());
                if (content == null) {
                    Files.deleteIfExists(p);
                } else {
                    Files.writeString(p, content, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
