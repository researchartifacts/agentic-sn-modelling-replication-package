package org.snrmbench.netbench.cli;

import org.snrmbench.netbench.csv.CanonicalCsvWriter;
import org.snrmbench.netbench.describe.NumberStyle;
import org.snrmbench.netbench.extract.TemplateExtractor;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.util.Json;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/** {@code netbench extract}: the regex baseline for template-style descriptions. */
@Command(name = "extract", mixinStandardHelpOptions = true,
        description = "Parse a template-style description into the five canonical CSV files.")
public final class ExtractCommand implements Callable<Integer> {

    @Option(names = "--description", required = true, description = "The description (description.md).")
    Path description;

    @Option(names = "--out", required = true, description = "Output directory for the CSV files and extract-log.json.")
    Path out;

    @Option(names = "--strict", description = "Report sentences that match nothing and exit 4.")
    boolean strict;

    @Option(names = "--locale", defaultValue = "plain",
            description = "How the description writes numbers: plain|en|de (default ${DEFAULT-VALUE}).")
    String locale;

    @Override
    public Integer call() {
        NumberStyle numbers;
        try {
            numbers = NumberStyle.of(GenerateParams.parse(GenerateParams.NumberLocale.class, locale));
        } catch (IllegalArgumentException e) {
            System.err.println("netbench extract: --locale " + e.getMessage());
            return 2;
        }
        String text;
        try {
            text = Files.readString(description, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("netbench extract: cannot read " + description + ": " + e.getMessage());
            return 2;
        }
        TemplateExtractor.Result r;
        try {
            r = TemplateExtractor.extract(text, numbers);
        } catch (IllegalArgumentException e) {
            System.err.println("netbench extract: " + description + ": " + e.getMessage());
            return 4;
        }
        try {
            CanonicalCsvWriter.write(r.network(), r.geo(), out);
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("description", description.toString());
            log.put("matched", r.matched());
            log.put("skipped", r.skipped());
            log.put("ignored", r.ignored());
            log.put("ignored_sentences", r.ignoredSentences());
            log.put("problems", r.problems());
            Json.write(out.resolve("extract-log.json"), log);
        } catch (UncheckedIOException e) {
            System.err.println("netbench extract: " + e.getMessage());
            return 2;
        }
        if (strict && (r.ignored() > 0 || !r.problems().isEmpty())) {
            r.ignoredSentences().forEach(s -> System.err.println("unmatched: " + s));
            r.problems().forEach(s -> System.err.println("problem: " + s));
            return 4;
        }
        return 0;
    }
}
