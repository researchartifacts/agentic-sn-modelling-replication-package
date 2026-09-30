package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.extract.TemplateExtractor;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The resource files keep their contract: word lists for --naming realistic, noise corpora, and the paraphrase and narrative pools the renderer concatenates without looking. */
class ResourcesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    /** How a pool's lines are shaped, which decides how the renderer can splice them. */
    private enum Shape {
        /** A whole sentence: starts with a capital or a placeholder, ends with one full stop. */
        SENTENCE,
        /** A second sentence appended to another: one leading space, then a capital; ends with a full stop. */
        APPENDED_SENTENCE,
        /** A modifier appended to a noun: one leading space, lower case, no final full stop. */
        APPENDED_PHRASE,
        /** A noun or verb phrase joined into a clause list: lower case or a placeholder, no final full stop. */
        PHRASE,
        /** A capitalised definite noun phrase used as a subject: "The plant", "This hub". */
        SUBJECT
    }

    /** A pool file. */
    private record Pool(String dir, String id, Shape shape, Set<String> placeholders, String first) {
        String path() {
            return dir + "/" + id + ".txt";
        }
    }

    private static Pool paraphrase(String id, Shape shape, String first, String... placeholders) {
        return new Pool("paraphrases", id, shape, new TreeSet<>(List.of(placeholders)), first);
    }

    private static Pool narrative(String id, Shape shape, String... placeholders) {
        return new Pool("narrative", id, shape, new TreeSet<>(List.of(placeholders)), null);
    }

    private static final List<Pool> PARAPHRASES = List.of(
            paraphrase("node", Shape.SENTENCE, Templates.NODE, "name", "role", "region"),
            paraphrase("node-region", Shape.APPENDED_PHRASE, Templates.NODE_REGION, "region"),
            paraphrase("node-attributes", Shape.APPENDED_SENTENCE, Templates.NODE_ATTRIBUTES, "clauses"),
            paraphrase("node-capacity", Shape.PHRASE, Templates.NODE_CAPACITY, "value", "unit"),
            paraphrase("node-processing-time", Shape.PHRASE, Templates.NODE_PROCESSING_TIME, "value", "unit"),
            paraphrase("node-fixed-cost", Shape.PHRASE, Templates.NODE_FIXED_COST, "value"),
            paraphrase("node-var-cost", Shape.PHRASE, Templates.NODE_VAR_COST, "value"),
            paraphrase("node-failure-prob", Shape.PHRASE, Templates.NODE_FAILURE_PROB, "value"),
            paraphrase("geo", Shape.SENTENCE, Templates.GEO, "name", "lat", "lng"),
            paraphrase("demand", Shape.SENTENCE, Templates.DEMAND, "name", "value", "product", "unit"),
            paraphrase("inventory", Shape.SENTENCE, Templates.INVENTORY, "name", "clauses"),
            paraphrase("inventory-bare", Shape.PHRASE, Templates.INVENTORY_BARE, "product"),
            paraphrase("inventory-initial", Shape.PHRASE, Templates.INVENTORY_INITIAL, "value", "product"),
            paraphrase("inventory-safety", Shape.PHRASE, Templates.INVENTORY_SAFETY, "value", "product"),
            paraphrase("inventory-holding", Shape.PHRASE, Templates.INVENTORY_HOLDING, "value", "unit"),
            paraphrase("link", Shape.SENTENCE, Templates.LINK, "source", "target", "clauses"),
            paraphrase("link-lead-time", Shape.PHRASE, Templates.LINK_LEAD_TIME, "value", "unit"),
            paraphrase("link-distance", Shape.PHRASE, Templates.LINK_DISTANCE, "mode", "value"),
            paraphrase("link-capacity", Shape.PHRASE, Templates.LINK_CAPACITY, "value", "unit"),
            paraphrase("link-unit-cost", Shape.PHRASE, Templates.LINK_UNIT_COST, "value"),
            paraphrase("link-failure-prob", Shape.PHRASE, Templates.LINK_FAILURE_PROB, "value"),
            paraphrase("link-group", Shape.SENTENCE, "{source} supplies {targets}; each lane has {clauses}.",
                    "source", "targets", "clauses"));

    private static final List<Pool> NARRATIVE = List.of(
            narrative("overview-tier-one", Shape.SENTENCE, "name", "role"),
            narrative("overview-tier-many", Shape.SENTENCE, "members", "roles", "count"),
            narrative("facility-region", Shape.SENTENCE, "name", "region"),
            narrative("facility-attributes", Shape.SENTENCE, "subject", "clauses"),
            narrative("coref-supplier", Shape.SUBJECT),
            narrative("coref-plant", Shape.SUBJECT),
            narrative("coref-dc", Shape.SUBJECT),
            narrative("customer-region-one", Shape.SENTENCE, "name", "region"),
            narrative("customer-region-many", Shape.SENTENCE, "members", "region"),
            narrative("relative-node", Shape.SENTENCE, "subject", "field", "other"),
            narrative("demand-group", Shape.SENTENCE, "members", "value", "product", "unit"),
            narrative("relative-inventory", Shape.SENTENCE, "subject", "field", "product", "other"));

    private static final String COMPANIES = "names/companies.txt";
    private static final String CITIES = "names/cities.txt";
    private static final String BOILERPLATE = "noise/boilerplate.txt";
    private static final String TOPICAL = "noise/topical.txt";

    /** The only shapes an inventory sentence may take (the renderer fills a verb-phrase list). */
    private static final Set<String> INVENTORY_SHAPES = new TreeSet<>(List.of(
            "{name} {clauses}.", "{name} currently {clauses}.", "{name} also {clauses}.",
            "On inventory, {name} {clauses}."));

    /** Openers of the adversarial markers: the extractor skips such lines, so no other line may open so. */
    private static final List<String> MARKER_OPENERS = List.of("Until ", "Before the ", "Under the proposed",
            "In the competitor", "It was originally planned", "In the discontinued");

    /** The contract's forbidden noise terms. */
    private static final List<Pattern> NETWORK_TERMS = Stream.of("capacit(?:y|ies)", "lead[ -]time", "demand",
                    "inventor(?:y|ies)", "safety stock", "holding cost", "failure", "ships", "supplies", "delivers",
                    "stock", "lane", "transit", "per day", "per week", "per hour", "per unit", "per period",
                    "unit value", "probabilit(?:y|ies)", "latitude", "longitude")
            .map(t -> Pattern.compile("(?i)\\b(?:" + t + ")(?:s|es|ed|ing)?\\b"))
            .toList();

    /** Stricter than the contract: other words for the things a network fact is about (flows, costs, time bases, node roles and their coreferences), so that no noise sentence can be read as one. */
    private static final List<Pattern> ALSO_AVOIDED_IN_NOISE = Stream.of("ship\\w*", "suppl\\w*", "deliver\\w*",
                    "cost\\w*", "pric(?:e|es|ed|ing)", "throughput", "output", "volumes?", "freight", "warehous\\w*",
                    "logistic\\w*", "transport\\w*", "rout(?:e|es|ed|ing)", "flows?", "disruptions?", "outages?",
                    "downtime", "network\\w*", "nodes?", "echelons?", "tiers?", "periods?", "horizons?", "units?",
                    "per", "km", "kilomet\\w*", "plant\\w*", "factor(?:y|ies)", "sites?", "distribution", "hubs?",
                    "vendors?", "customers?")
            .map(t -> Pattern.compile("(?i)\\b(?:" + t + ")\\b"))
            .toList();

    private static final Pattern NUMBER_THEN_UNITS =
            Pattern.compile("(?i)(?:\\d|\\{pct}|\\{year})(?:%| percent)?\\s+units?\\b");
    private static final Pattern NODE_INTRODUCTION =
            Pattern.compile("(?i)\\bis an?\\b.*(?:supplier|plant|customer|distribution centre)");
    private static final Pattern INNER_STOP = Pattern.compile("[.!?](?:\\s|$)");
    /** "a" before a vowel sound, "an" before a consonant (lower-case words only; "a one-off", "an MBA" pass). */
    private static final Pattern WRONG_A = Pattern.compile("\\b[Aa] (?!eu|one)[aeio]");
    private static final Pattern WRONG_AN = Pattern.compile("\\b[Aa]n [b-df-gj-np-tv-z]");

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9]+(?:[ -][A-Za-z0-9]+)*");
    private static final Pattern ROMAN_SUFFIX = Pattern.compile("(?i)(?:^| )(?:I{1,3}|IV|VI{0,3}|IX|X)$");
    /** Fact-template phrases a name must not contain; "and" because lists of names are joined "A, B and C". */
    private static final Pattern NAME_PHRASES = Pattern.compile("(?i)\\b(?:is an?|ships to|demands|holds|keeps"
            + "|stocks|has a|with a|located at|regions?|suppliers?|plants?|customers?|distribution centres?|tiers?"
            + "|and)\\b");

    private static final Pattern DETERMINER = Pattern.compile("(?:a|an|the|its|this|each|every) ");
    private static final Pattern RATE_UNIT = Pattern.compile("(?i)\\b(?:per|each|every) \\{unit}");
    private static final Pattern THIRD_PERSON_VERB = Pattern.compile("[a-z]+s ");
    private static final Pattern SAME = Pattern.compile("(?i)\\b(?:same|identical)\\b");
    private static final Pattern EACH = Pattern.compile("(?i)\\b(?:each|every)\\b");
    private static final Pattern ARTICLE_BEFORE_ROLE = Pattern.compile("(?i)\\ban? \\{role}");
    private static final Pattern NAME_FIRST = Pattern.compile("(?:[A-Z][a-z]*(?: [a-z]+){0,4}, )?\\{name} .*");
    private static final Pattern COREF = Pattern.compile("(?:The|This) [a-z]+(?: [a-z]+)*");

    // ---- files ------------------------------------------------------------------------------

    @Test
    void everyResourceFileIsPlainAsciiWithLfLineEndings() {
        List<String> problems = new ArrayList<>();
        for (String path : allFiles()) {
            byte[] b = bytes(path);
            for (int i = 0; i < b.length; i++) {
                int c = b[i] & 0xFF;
                if (c != '\n' && (c < 0x20 || c >= 0x7F)) {
                    problems.add(path + ": byte " + i + " is 0x" + Integer.toHexString(c));
                    break;
                }
            }
            if (b.length == 0 || b[b.length - 1] != '\n') {
                problems.add(path + ": does not end with a newline");
            }
            boolean leadingSpace = path.equals(pool("node-region").path())
                    || path.equals(pool("node-attributes").path());
            for (String line : entries(path)) {
                String expected = (leadingSpace ? " " : "") + line.strip();
                if (!line.equals(expected) || line.strip().contains("  ")) {
                    problems.add(path + ": stray whitespace in \"" + line + "\"");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void onlyTheContractedFilesExist() {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        expected.put("names", new TreeSet<>(List.of("companies.txt", "cities.txt")));
        expected.put("noise", new TreeSet<>(List.of("boilerplate.txt", "topical.txt")));
        expected.put("paraphrases", new TreeSet<>(PARAPHRASES.stream().map(p -> p.id() + ".txt").toList()));
        expected.put("narrative", new TreeSet<>(NARRATIVE.stream().map(p -> p.id() + ".txt").toList()));
        for (Map.Entry<String, Set<String>> e : expected.entrySet()) {
            assertThat(listing(e.getKey())).as(e.getKey()).isEqualTo(e.getValue());
        }
    }

    @Test
    void noLineOpensWithAnAdversarialMarker() {
        List<String> problems = new ArrayList<>();
        for (String path : allFiles()) {
            for (String line : entries(path)) {
                for (String marker : MARKER_OPENERS) {
                    if (line.strip().regionMatches(true, 0, marker, 0, marker.length())) {
                        problems.add(path + ": \"" + line + "\" opens like the marker \"" + marker + "\"");
                    }
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    // ---- names ------------------------------------------------------------------------------

    @Test
    void namesAreCleanUniqueAndSafeToEmbedInSentences() {
        List<String> companies = entries(COMPANIES);
        List<String> cities = entries(CITIES);
        assertThat(companies).as(COMPANIES).hasSizeGreaterThanOrEqualTo(150);
        assertThat(cities).as(CITIES).hasSizeGreaterThanOrEqualTo(250);

        List<String> problems = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (String path : List.of(COMPANIES, CITIES)) {
            for (String name : entries(path)) {
                String where = path + ": \"" + name + "\" ";
                if (!NAME.matcher(name).matches()) {
                    problems.add(where + "may use only ASCII letters, digits, single spaces and hyphens");
                }
                if (path.equals(COMPANIES) && name.split(" ").length > 3) {
                    problems.add(where + "has more than 3 words");
                }
                if (ROMAN_SUFFIX.matcher(name).find()) {
                    problems.add(where + "ends in a Roman numeral, which Namer appends to reused names");
                }
                Matcher phrase = NAME_PHRASES.matcher(name);
                if (phrase.find()) {
                    problems.add(where + "contains \"" + phrase.group() + "\"");
                }
                // names are filled into topical noise ({node}), so they obey the noise vocabulary too
                for (Pattern term : NETWORK_TERMS) {
                    if (term.matcher(name).find()) {
                        problems.add(where + "contains a term forbidden in noise: " + term.pattern());
                    }
                }
                String key = name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                String previous = seen.putIfAbsent(key, path);
                if (previous != null) {
                    problems.add(where + "duplicates an entry of " + previous + " after normalisation");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    // ---- noise ------------------------------------------------------------------------------

    @Test
    void noiseLinesAreSingleSentencesWithTheirPlaceholders() {
        List<String> boilerplate = entries(BOILERPLATE);
        List<String> topical = entries(TOPICAL);
        assertThat(boilerplate).as(BOILERPLATE).hasSizeGreaterThanOrEqualTo(80);
        assertThat(topical).as(TOPICAL).hasSizeGreaterThanOrEqualTo(60);

        List<String> problems = new ArrayList<>();
        for (String line : boilerplate) {
            String where = BOILERPLATE + ": \"" + line + "\" ";
            if (line.indexOf('{') >= 0 || line.indexOf('}') >= 0) {
                problems.add(where + "has a placeholder");
            }
            int words = line.split(" ").length;
            if (words < 8 || words > 30) {
                problems.add(where + "has " + words + " words (8 to 30 allowed)");
            }
            if (!Character.isUpperCase(line.charAt(0)) || !oneSentence(line)) {
                problems.add(where + "is not one capitalised sentence ending in a full stop");
            }
        }
        Set<String> allowed = new TreeSet<>(List.of("product", "region", "node", "year", "pct"));
        int withNode = 0;
        int withProduct = 0;
        for (String line : topical) {
            String where = TOPICAL + ": \"" + line + "\" ";
            List<String> used = placeholders(line);
            if (!allowed.containsAll(used) || used.size() != new TreeSet<>(used).size() || strayBrace(line)) {
                problems.add(where + "may use each of " + allowed + " at most once, and nothing else");
            }
            Matcher pct = Pattern.compile("\\{pct}").matcher(line);
            while (pct.find()) {
                String rest = line.substring(pct.end());
                if (!rest.startsWith("%") && !rest.startsWith(" percent")) {
                    problems.add(where + "must write {pct}% or {pct} percent");
                }
            }
            // a node named next to a region or a product could be read as its region or its stock
            if (used.contains("node") && (used.contains("region") || used.contains("product"))) {
                problems.add(where + "names {node} together with {region} or {product}");
            }
            if (!(Character.isUpperCase(line.charAt(0)) || line.startsWith("{")) || !oneSentence(line)) {
                problems.add(where + "is not one sentence ending in a full stop");
            }
            withNode += used.contains("node") ? 1 : 0;
            withProduct += used.contains("product") ? 1 : 0;
        }
        assertThat(problems).isEmpty();
        assertThat(withNode).as("topical lines using {node}").isGreaterThanOrEqualTo(20);
        assertThat(withProduct).as("topical lines using {product}").isGreaterThanOrEqualTo(20);
    }

    @Test
    void noiseNeverUsesNetworkVocabulary() {
        List<String> problems = new ArrayList<>();
        for (String path : List.of(BOILERPLATE, TOPICAL)) {
            for (String line : entries(path)) {
                String where = path + ": \"" + line + "\" ";
                String words = PLACEHOLDER.matcher(line).replaceAll("X"); // a placeholder's key is not prose
                for (Pattern term : NETWORK_TERMS) {
                    Matcher m = term.matcher(words);
                    if (m.find()) {
                        problems.add(where + "contains \"" + m.group() + "\"");
                    }
                }
                for (Pattern term : ALSO_AVOIDED_IN_NOISE) {
                    Matcher m = term.matcher(words);
                    if (m.find()) {
                        problems.add(where + "contains \"" + m.group() + "\" (network vocabulary)");
                    }
                }
                if (NUMBER_THEN_UNITS.matcher(line).find()) {
                    problems.add(where + "puts a number before \"units\"");
                }
                if (NODE_INTRODUCTION.matcher(line).find()) {
                    problems.add(where + "reads like a node introduction");
                }
                if (WRONG_A.matcher(line).find() || WRONG_AN.matcher(line).find()) {
                    problems.add(where + "has a/an before the wrong sound");
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    /** No noise line matches any statement pattern of the extractor: appended to a template description (topical lines filled with sample values), each is ignored and the extracted network is unchanged. */
    @Test
    void noNoiseLineMatchesAnExtractorStatementPattern() {
        Map<String, String> sample = Map.of("product", "Widget", "region", "EU-West", "node", "PLT-01",
                "year", "2021", "pct", "12");
        List<String> noise = new ArrayList<>(entries(BOILERPLATE));
        entries(TOPICAL).forEach(line -> noise.add(Templates.fill(line, sample)));
        String markdown = Generator.build(GenerateParams.defaults(1, 12, 4, "Widget"), "r").description().markdown();

        TemplateExtractor.Result before = TemplateExtractor.extract(markdown);
        TemplateExtractor.Result after = TemplateExtractor.extract(markdown + String.join("\n", noise) + "\n");

        List<String> ignored = new ArrayList<>(before.ignoredSentences());
        ignored.addAll(noise);
        assertThat(after.ignored()).isEqualTo(before.ignored() + noise.size());
        assertThat(after.ignoredSentences()).isEqualTo(ignored);
        assertThat(after.matched()).isEqualTo(before.matched());
        assertThat(after.skipped()).isEqualTo(before.skipped());
        assertThat(after.problems()).isEqualTo(before.problems());
        assertThat(after.geo()).isEqualTo(before.geo());
        assertThat(after.network()).isEqualTo(before.network());
    }

    // ---- pools ------------------------------------------------------------------------------

    @Test
    void paraphrasePoolsOpenWithTheTemplateAndKeepTheirGrammar() {
        List<String> problems = new ArrayList<>();
        for (Pool pool : PARAPHRASES) {
            List<String> lines = entries(pool.path());
            if (lines.isEmpty() || !lines.get(0).equals(pool.first())) {
                problems.add(pool.path() + ": line 1 must be the template wording \"" + pool.first() + "\"");
            }
            checkPool(pool, lines, problems);
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void narrativePoolsKeepTheirGrammar() {
        List<String> problems = new ArrayList<>();
        for (Pool pool : NARRATIVE) {
            checkPool(pool, entries(pool.path()), problems);
        }
        assertThat(problems).isEmpty();
    }

    private static void checkPool(Pool pool, List<String> lines, List<String> problems) {
        if (lines.size() < 4) {
            problems.add(pool.path() + ": has " + lines.size() + " lines (at least 4 required)");
        }
        if (new TreeSet<>(lines).size() != lines.size()) {
            problems.add(pool.path() + ": has duplicate lines");
        }
        for (String line : lines) {
            String where = pool.path() + ": \"" + line + "\" ";
            List<String> used = placeholders(line);
            if (!new TreeSet<>(used).equals(pool.placeholders()) || used.size() != pool.placeholders().size()
                    || strayBrace(line)) {
                problems.add(where + "must use each of " + pool.placeholders() + " exactly once, and nothing else");
            }
            if (WRONG_A.matcher(line).find() || WRONG_AN.matcher(line).find()) {
                problems.add(where + "has a/an before the wrong sound");
            }
            String shapeProblem = shapeProblem(pool.shape(), line);
            if (shapeProblem != null) {
                problems.add(where + shapeProblem);
            }
            String grammarProblem = grammarProblem(pool.id(), line);
            if (grammarProblem != null) {
                problems.add(where + grammarProblem);
            }
        }
    }

    private static String shapeProblem(Shape shape, String line) {
        return switch (shape) {
            case SENTENCE -> (Character.isUpperCase(line.charAt(0)) || line.startsWith("{")) && oneSentence(line)
                    ? null : "must be one sentence opening with a capital or a placeholder and ending in a full stop";
            case APPENDED_SENTENCE -> line.startsWith(" ") && Character.isUpperCase(line.charAt(1))
                    && oneSentence(line)
                    ? null : "must be one space, then one capitalised sentence ending in a full stop";
            case APPENDED_PHRASE -> line.startsWith(" ") && Character.isLowerCase(line.charAt(1))
                    && noStop(line)
                    ? null : "must be one space, then a lower-case phrase without a final full stop";
            case PHRASE -> (Character.isLowerCase(line.charAt(0)) || line.startsWith("{")) && noStop(line)
                    ? null : "must be a lower-case phrase (or open with a placeholder) without a full stop";
            case SUBJECT -> COREF.matcher(line).matches()
                    ? null : "must be a capitalised definite noun phrase such as \"The plant\" or \"This hub\"";
        };
    }

    /** The constraints of the pool table that go beyond shape and placeholders. */
    private static String grammarProblem(String id, String line) {
        return switch (id) {
            case "node" -> line.startsWith("{name} ") && line.endsWith("{role}{region}.")
                    ? null : "must open with {name} and end with {role}{region}.";
            case "node-attributes" -> line.startsWith(" It ") ? null : "must have It as its subject";
            case "node-capacity" -> DETERMINER.matcher(line).lookingAt() && line.contains("{value} units ")
                    && RATE_UNIT.matcher(line).find()
                    ? null : "must open with a determiner and read {value} units per|each|every {unit}";
            case "link-capacity" -> line.contains("{value} units ") && RATE_UNIT.matcher(line).find()
                    ? null : "must read {value} units per|each|every {unit}";
            case "node-processing-time", "link-lead-time" -> line.contains("{value} {unit}")
                    ? null : "must read {value} {unit}";
            case "node-fixed-cost" -> line.contains("{value} per period") ? null : "must read {value} per period";
            case "node-var-cost", "link-unit-cost" -> line.contains("{value} per unit")
                    ? null : "must read {value} per unit";
            case "link-distance" -> line.endsWith("{value} km") && line.startsWith("{mode} ") ? null
                    : "must read {mode} ... {value} km ({mode} carries its article)";
            case "geo" -> line.startsWith("{name} ") && line.contains("latitude {lat}")
                    && line.contains("longitude {lng}")
                    ? null : "must open with {name} and read latitude {lat} and longitude {lng}";
            case "demand" -> line.contains("{value} units of {product}") && RATE_UNIT.matcher(line).find()
                    ? null : "must read {value} units of {product} and per|each|every {unit}";
            case "demand-group" -> line.contains("{value} units of {product}") && RATE_UNIT.matcher(line).find()
                    && EACH.matcher(line).find()
                    ? null : "must say each member demands {value} units of {product} per|each|every {unit}";
            case "inventory" -> INVENTORY_SHAPES.contains(line) ? null : "must be one of " + INVENTORY_SHAPES;
            case "inventory-bare" -> THIRD_PERSON_VERB.matcher(line).lookingAt()
                    ? null : "must open with a third-person singular verb";
            case "inventory-initial", "inventory-safety" -> THIRD_PERSON_VERB.matcher(line).lookingAt()
                    && line.contains("{value} units of {product}")
                    ? null : "must open with a third-person singular verb and read {value} units of {product}";
            case "inventory-holding" -> THIRD_PERSON_VERB.matcher(line).lookingAt()
                    && line.contains("{value} per unit per {unit}")
                    ? null : "must open with a third-person singular verb and read {value} per unit per {unit}";
            case "link" -> line.indexOf("{source}") < line.indexOf("{target}")
                    ? null : "must name {source} before {target}";
            case "link-group" -> line.indexOf("{source}") < line.indexOf("{targets}") && EACH.matcher(line).find()
                    ? null : "must name {source} before {targets} and say the clauses hold for each lane";
            case "overview-tier-one" -> !ARTICLE_BEFORE_ROLE.matcher(line).find()
                    ? null : "must not put a/an before {role} (the article would not agree)";
            case "overview-tier-many" -> line.contains("{count} {roles}") ? null : "must read {count} {roles}";
            case "facility-region" -> NAME_FIRST.matcher(line).matches()
                    ? null : "must open with {name}, or a short prepositional opener and {name}";
            case "facility-attributes" -> line.startsWith("{subject} ") ? null : "must open with {subject}";
            case "relative-node", "relative-inventory" -> line.startsWith("{subject} ") && SAME.matcher(line).find()
                    ? null : "must open with {subject} and say same or identical";
            default -> null;
        };
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static Pool pool(String id) {
        return Stream.concat(PARAPHRASES.stream(), NARRATIVE.stream()).filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow();
    }

    private static List<String> allFiles() {
        List<String> out = new ArrayList<>(List.of(COMPANIES, CITIES, BOILERPLATE, TOPICAL));
        Stream.concat(PARAPHRASES.stream(), NARRATIVE.stream()).forEach(p -> out.add(p.path()));
        return out;
    }

    private static byte[] bytes(String path) {
        try (InputStream in = ResourcesTest.class.getResourceAsStream("/" + path)) {
            assertThat(in).as("resource " + path).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Non-blank, non-comment lines, NOT trimmed (a leading space can be part of the entry). */
    private static List<String> entries(String path) {
        List<String> out = new ArrayList<>();
        for (String line : new String(bytes(path), StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank() && !line.strip().startsWith("#")) {
                out.add(line);
            }
        }
        return out;
    }

    private static Set<String> listing(String dir) {
        try {
            URL url = ResourcesTest.class.getResource("/" + dir);
            assertThat(url).as("resource directory " + dir).isNotNull();
            try (Stream<Path> files = Files.list(Paths.get(url.toURI()))) {
                return new TreeSet<>(files.map(f -> f.getFileName().toString()).toList());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> placeholders(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(line);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** A brace left over once the well-formed placeholders are removed. */
    private static boolean strayBrace(String line) {
        String rest = PLACEHOLDER.matcher(line).replaceAll("");
        return rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0;
    }

    /** Ends with a full stop, and no other sentence ends inside it. */
    private static boolean oneSentence(String line) {
        String s = line.strip();
        return s.endsWith(".") && !INNER_STOP.matcher(s.substring(0, s.length() - 1)).find();
    }

    private static boolean noStop(String line) {
        return !line.strip().endsWith(".") && !INNER_STOP.matcher(line).find();
    }
}
