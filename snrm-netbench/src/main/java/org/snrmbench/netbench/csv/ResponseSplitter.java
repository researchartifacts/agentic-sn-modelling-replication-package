package org.snrmbench.netbench.csv;

import org.snrmbench.netbench.schema.Column;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits an LLM response into the five canonical files. */
public final class ResponseSplitter {

    /** The split response. */
    public record Split(Map<String, String> files, List<Finding> findings) {
        public Split {
            files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
            findings = List.copyOf(findings);
        }
    }

    // possessive quantifiers: a long run of '-' or spaces must not backtrack (a line of 20 000 dashes
    // followed by text would otherwise take hours)
    private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}\\s++(.*)$");
    private static final Pattern FENCE = Pattern.compile("^\\s*+(`{3,}+|~{3,}+).*$");
    private static final Pattern MARKER =
            Pattern.compile("^\\s*+(?:={3,}+|-{3,}+)\\s*+(.*?)\\s*+(?:={3,}+|-{3,}+)\\s*+$");
    private static final Pattern FILE_PREFIX = Pattern.compile("^file\\s*:\\s*");
    private static final Pattern DECORATION = Pattern.compile("[`*\"']");

    /** How a block was recognised, most preferred first. */
    private enum Form {
        HEADING, MARKER, HEADER_ROW
    }

    /** A recognised block, the 1-based response line it starts on, and how it was recognised. */
    private record Block(String content, int line, Form form) {
    }

    private final List<String> lines;
    private final Map<Sheet, Block> found = new EnumMap<>(Sheet.class);
    private final List<Finding> findings = new ArrayList<>();

    private ResponseSplitter(String text) {
        this.lines = (!text.isEmpty() && text.charAt(0) == (char) 0xFEFF ? text.substring(1) : text).lines().toList();
    }

    public static Split split(String responseText) {
        return new ResponseSplitter(responseText).run();
    }

    private Split run() {
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            Optional<Sheet> heading = headingFile(line);
            Optional<String> marker = markerText(line);
            if (heading.isPresent()) {
                i = afterHeading(heading.get(), i);
            } else if (marker.isPresent()) {
                Optional<Sheet> sheet = Sheet.byFileName(FILE_PREFIX.matcher(marker.get()).replaceFirst(""));
                if (sheet.isPresent()) {
                    int end = nextMarker(i + 1);
                    add(sheet.get(), section(i + 1, end), i + 1, Form.MARKER);
                    i = end;
                } else {
                    i++; // END, Output, Answer, …: ends the previous section and names nothing
                }
            } else if (isFence(line)) {
                int close = closingFence(i);
                List<String> body = lines.subList(i + 1, Math.min(close, lines.size()));
                int start = i;
                headerSheet(body).ifPresent(s -> add(s, join(body), start + 1, Form.HEADER_ROW));
                i = close + 1;
            } else {
                i++;
            }
        }
        if (found.isEmpty()) {
            findings.add(Finding.row(Code.ENVELOPE_PARSE_ERROR, null, null, null,
                    "the response contains no recognisable CSV file (no file heading, no FILE marker and no "
                            + "fenced block starting with a canonical header row)"));
        }
        Map<String, String> files = new LinkedHashMap<>();
        found.forEach((sheet, block) -> files.put(sheet.fileName(), block.content()));
        return new Split(files, findings);
    }

    /** A heading claims the next fenced block, unless another heading or marker comes first. */
    private int afterHeading(Sheet sheet, int headingIndex) {
        int j = headingIndex + 1;
        while (j < lines.size() && !isFence(lines.get(j)) && !isBoundary(lines.get(j))) {
            j++;
        }
        if (j < lines.size() && isFence(lines.get(j))) {
            int close = closingFence(j);
            add(sheet, join(lines.subList(j + 1, Math.min(close, lines.size()))), headingIndex + 1, Form.HEADING);
            return close + 1;
        }
        return j;
    }

    /** Records a block: a more preferred form replaces a less preferred one, a less preferred form is ignored, and a second block of the same form is an ENVELOPE_PARSE_ERROR (the first is kept). */
    private void add(Sheet sheet, String content, int line, Form form) {
        Block first = found.get(sheet);
        if (first == null || form.compareTo(first.form()) < 0) {
            found.put(sheet, new Block(content, line, form));
            return;
        }
        if (form.compareTo(first.form()) > 0) {
            return;
        }
        findings.add(Finding.row(Code.ENVELOPE_PARSE_ERROR, sheet.fileName(), null, null,
                "response line " + line + ": " + sheet.fileName() + " appears a second time; the block at line "
                        + first.line() + " is used"));
    }

    // ---- markers ----------------------------------------------------------------------------

    /** The text between the {@code ===}/{@code ---} runs of a marker line; empty when not a marker. */
    private static Optional<String> markerText(String line) {
        Matcher m = MARKER.matcher(line);
        if (!m.matches()) {
            return Optional.empty();
        }
        String text = clean(m.group(1));
        return text.chars().anyMatch(Character::isLetter) ? Optional.of(text) : Optional.empty();
    }

    private int nextMarker(int from) {
        int j = from;
        while (j < lines.size() && markerText(lines.get(j)).isEmpty()) {
            j++;
        }
        return j;
    }

    /** A marker section's lines, or the body of the first fence inside it. */
    private String section(int from, int to) {
        for (int k = from; k < to; k++) {
            if (isFence(lines.get(k))) {
                return join(lines.subList(k + 1, Math.min(closingFence(k), to)));
            }
        }
        int start = from;
        int end = to;
        while (start < end && lines.get(start).isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }
        return join(lines.subList(start, end));
    }

    // ---- headings ---------------------------------------------------------------------------

    private static Optional<Sheet> headingFile(String line) {
        Matcher m = HEADING.matcher(line);
        return m.matches() ? fileIn(clean(closingHashesStripped(m.group(1)))) : Optional.empty();
    }

    /** A heading's text without an optional closing run of {@code #} (a linear scan, no backtracking). */
    private static String closingHashesStripped(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int hashes = end;
        while (hashes > 0 && text.charAt(hashes - 1) == '#') {
            hashes--;
        }
        if (hashes < end && (hashes == 0 || Character.isWhitespace(text.charAt(hashes - 1)))) {
            end = hashes;
        }
        return text.substring(0, end).strip();
    }

    private static boolean isBoundary(String line) {
        return HEADING.matcher(line).matches() || markerText(line).isPresent();
    }

    /** Lower-cased text without backticks, asterisks, quotes and a trailing colon. */
    private static String clean(String text) {
        String t = DECORATION.matcher(text).replaceAll("").trim().toLowerCase(Locale.ROOT);
        return t.endsWith(":") ? t.substring(0, t.length() - 1).trim() : t;
    }

    /** The sheet whose file name the text is, or ends with after a non-name character (file: nodes.csv); node_products.csv therefore never reads as products.csv. */
    private static Optional<Sheet> fileIn(String text) {
        for (Sheet s : Sheet.values()) {
            String name = s.fileName();
            if (text.equals(name)) {
                return Optional.of(s);
            }
            if (text.endsWith(name) && !isNameChar(text.charAt(text.length() - name.length() - 1))) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
    }

    // ---- fences -----------------------------------------------------------------------------

    private static boolean isFence(String line) {
        return FENCE.matcher(line).matches();
    }

    /** The index of the line closing the fence opened at {@code open}; {@code lines.size} when unclosed. */
    private int closingFence(int open) {
        String opener = lines.get(open).trim();
        char c = opener.charAt(0);
        int length = 0;
        while (length < opener.length() && opener.charAt(length) == c) {
            length++;
        }
        for (int k = open + 1; k < lines.size(); k++) {
            String t = lines.get(k).trim();
            if (t.length() >= length && t.chars().allMatch(ch -> ch == c)) {
                return k;
            }
        }
        return lines.size();
    }

    /** The sheet a block's first line is the header of: every required column of the sheet is present, and of the sheets that qualify it maps the most header cells (a nodes header also carries the products name). */
    static Optional<Sheet> headerSheet(List<String> body) {
        Optional<String> header = body.stream().filter(l -> !l.isBlank()).findFirst();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        String delimiter = String.valueOf(LenientCsvReader.sniff(header.get()));
        List<String> cells = new ArrayList<>();
        for (String cell : header.get().split(Pattern.quote(delimiter), -1)) {
            cells.add(DECORATION.matcher(cell).replaceAll("").trim());
        }
        Sheet best = null;
        int bestScore = 0;
        boolean tie = false;
        for (Sheet s : Sheet.values()) {
            Set<Column> mapped = new LinkedHashSet<>();
            cells.forEach(c -> Schema.column(s, c).ifPresent(mapped::add));
            if (!mapped.containsAll(Schema.required(s))) {
                continue;
            }
            if (mapped.size() > bestScore) {
                best = s;
                bestScore = mapped.size();
                tie = false;
            } else if (mapped.size() == bestScore) {
                tie = true;
            }
        }
        return tie ? Optional.empty() : Optional.ofNullable(best);
    }

    private static String join(List<String> body) {
        return body.isEmpty() ? "" : String.join("\n", body) + "\n";
    }
}
