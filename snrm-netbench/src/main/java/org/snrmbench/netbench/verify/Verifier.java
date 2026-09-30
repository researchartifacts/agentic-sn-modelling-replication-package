package org.snrmbench.netbench.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.snrmbench.netbench.csv.RawNetwork;
import org.snrmbench.netbench.describe.Decoy;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.model.TimeUnit;
import org.snrmbench.netbench.schema.Conventions;
import org.snrmbench.netbench.schema.Field;
import org.snrmbench.netbench.schema.Schema;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.util.Json;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.SnrmRules;
import org.snrmbench.netbench.validate.ValidationResult;
import org.snrmbench.netbench.verify.FieldComparator.Value;
import org.snrmbench.netbench.verify.Matcher.Pair;
import org.snrmbench.netbench.verify.Matcher.SheetMatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Scores a candidate network (C) against the truth (B), layer by layer: L0 SnrmRules over C plus the envelope findings; L1 structural precision, recall and F1; L2 per-field classification, fact recall and precision. */
public final class Verifier {

    public static final String TOOL_VERSION = "0.1.0";

    /** Everything one verification needs. */
    public record Inputs(RawNetwork truth, Candidate candidate, List<Fact> facts, List<Decoy> decoys,
                         JsonNode manifest, BigDecimal tolerance, String truthPath, String candidatePath,
                         boolean lenientNumbers) {

        /** Inputs with strict number parsing (the default). */
        public Inputs(RawNetwork truth, Candidate candidate, List<Fact> facts, List<Decoy> decoys, JsonNode manifest,
                      BigDecimal tolerance, String truthPath, String candidatePath) {
            this(truth, candidate, facts, decoys, manifest, tolerance, truthPath, candidatePath, false);
        }
    }

    /** The key a ledger fact or a decoy value is looked up by. */
    private record FactKey(Sheet sheet, List<String> norm, String field) {
    }

    private final Inputs in;
    private final Map<FactKey, Fact> ledger = new LinkedHashMap<>();
    private final Map<FactKey, List<Decoy>> valueDecoys = new LinkedHashMap<>();
    private final Map<Sheet, Map<List<String>, Decoy>> entityDecoys = new EnumMap<>(Sheet.class);
    /** Normalised decoy node name → the first decoy that introduces it. */
    private final Map<String, Decoy> decoyNodes = new LinkedHashMap<>();
    private final Map<String, NodeType> truthTypes = new LinkedHashMap<>();
    private TimeUnit truthPeriod;
    private TimeUnit candidatePeriod;

    private final Map<Field, Map<FactClass, Integer>> tally = new EnumMap<>(Field.class);
    private final Map<Field, Integer> stated = new EnumMap<>(Field.class);
    private final List<Report.Mismatch> mismatches = new ArrayList<>();
    private final List<Report.DecoyValue> decoyValues = new ArrayList<>();
    private final Map<Sheet, List<String>> fabricated = new EnumMap<>(Sheet.class);
    private int fabricatedFacts;

    private Verifier(Inputs in) {
        this.in = in;
    }

    public static Report verify(Inputs in) {
        return new Verifier(in).run();
    }

    private Report run() {
        ValidationResult truthResult = SnrmRules.validate(in.truth());
        ValidationResult candidateResult = SnrmRules.validate(in.candidate().network());
        truthPeriod = truthResult.timeBase().periodUnit();
        candidatePeriod = candidateResult.timeBase().periodUnit();
        indexLedger();
        indexDecoys();

        Map<Sheet, SheetMatch> matches = new EnumMap<>(Sheet.class);
        for (Sheet s : Sheet.values()) {
            matches.put(s, Matcher.match(s, Matcher.entities(in.truth(), s),
                    Matcher.entities(in.candidate().network(), s)));
        }
        for (Entity n : matches.get(Sheet.NODES).truth()) {
            Normaliser.nodeType(n.cell(Schema.NODE_TYPE)).ifPresent(t -> truthTypes.put(n.norm().get(0), t));
        }

        Report.L0 l0 = l0(candidateResult);
        Report.L1 l1 = l1(matches);
        for (Field f : Field.values()) {
            tally.put(f, new EnumMap<>(FactClass.class));
            stated.put(f, 0);
        }
        for (Sheet s : Sheet.values()) {
            matches.get(s).pairs().forEach(this::scoreTruthEntity);
        }
        for (Sheet s : Sheet.values()) {
            if (s != Sheet.NETWORK_META) {
                fabricated.put(s, new ArrayList<>());
                matches.get(s).fabricated().forEach(this::scoreFabricated);
            }
        }
        Report.L2 l2 = l2();
        Report.Decoys decoys = decoys(matches);
        double ncs = 0.2 * (l0.importValid() ? 1 : 0) + 0.3 * l1.structureF1() + 0.5 * l2.factRecall();

        Candidate c = in.candidate();
        Report.Envelope envelope = new Report.Envelope(c.source(), c.filesFound(), c.filesMissing(),
                c.envelopeFindings());
        return new Report(TOOL_VERSION, verifiedAt(), in.truthPath(), in.candidatePath(), runId(), manifestBlock(),
                envelope, l0, l1, l2, decoys, ncs);
    }

    // ---- L0 ---------------------------------------------------------------------------------

    private Report.L0 l0(ValidationResult candidateResult) {
        List<Finding> findings = new ArrayList<>(candidateResult.findings());
        findings.addAll(in.candidate().envelopeFindings());
        long errors = findings.stream().filter(Finding::isError).count();
        return new Report.L0(errors == 0, errors, findings.size() - errors, findings);
    }

    // ---- L1 ---------------------------------------------------------------------------------

    private static Report.L1 l1(Map<Sheet, SheetMatch> matches) {
        SheetMatch nodes = matches.get(Sheet.NODES);
        SheetMatch links = matches.get(Sheet.LINKS);
        Set<String> matchedNodes = new LinkedHashSet<>();
        int sameType = 0;
        for (Pair p : nodes.pairs()) {
            if (p.matched()) {
                matchedNodes.add(p.truth().norm().get(0));
                if (Normaliser.name(p.truth().cell(Schema.NODE_TYPE))
                        .equals(Normaliser.name(p.candidate().cell(Schema.NODE_TYPE)))) {
                    sameType++;
                }
            }
        }
        int eligible = 0;
        int eligibleMatched = 0;
        for (Pair p : links.pairs()) {
            if (matchedNodes.contains(p.truth().norm().get(0)) && matchedNodes.contains(p.truth().norm().get(1))) {
                eligible++;
                eligibleMatched += p.matched() ? 1 : 0;
            }
        }
        Report.Structure n = structure(nodes);
        Report.Structure l = structure(links);
        return new Report.L1(n, l, ratio(eligibleMatched, eligible), structure(matches.get(Sheet.PRODUCTS)),
                structure(matches.get(Sheet.NODE_PRODUCTS)), ratio(sameType, nodes.matched()), f1(n.f1(), l.f1()),
                missing(nodes), keys(nodes.extra()), missing(links), keys(links.extra()),
                links.pairs().stream().filter(Pair::reversed).map(p -> p.truth().key()).toList());
    }

    private static Report.Structure structure(SheetMatch m) {
        int matched = m.matched();
        double precision = ratio(matched, m.candidate().size());
        double recall = ratio(matched, m.truth().size());
        return new Report.Structure(m.truth().size(), m.candidate().size(), matched, precision, recall,
                f1(precision, recall));
    }

    private static List<String> missing(SheetMatch m) {
        return m.pairs().stream().filter(p -> !p.matched()).map(p -> p.truth().key()).toList();
    }

    private static List<String> keys(List<Entity> entities) {
        return entities.stream().map(Entity::key).toList();
    }

    // ---- L2 ---------------------------------------------------------------------------------

    /** Classifies every field of a truth entity against its candidate entity (null when missing). */
    private void scoreTruthEntity(Pair p) {
        Entity t = p.truth();
        Entity c = p.candidate();
        for (Field f : Field.of(t.sheet())) {
            Optional<Conventions.Value> convention = convention(f, t);
            // the period length cannot resolve a blank unit through itself: it stays unresolved (wrong)
            boolean period = f == Field.META_PERIOD_LENGTH;
            Value truth = FieldComparator.of(f, t, period ? null : truthPeriod);
            Value candidate = FieldComparator.of(f, c, period ? null : candidatePeriod, in.lenientNumbers());
            Optional<Fact> fact = Optional.ofNullable(ledger.get(new FactKey(t.sheet(), t.norm(), f.stem())));
            boolean isStated = convention.isEmpty()
                    && fact.map(Fact::stated).orElseGet(() -> f.isExistence() || !FieldComparator.blank(f, truth));
            FactClass cls;
            if (c == null) {
                if (!isStated) {
                    continue; // a missing entity's unstated and convention fields are not scored
                }
                cls = FactClass.OMITTED;
            } else if (f.isExistence() && p.reversed()) {
                cls = isStated ? FactClass.WRONG_DIRECTION : FactClass.FILLED_UNSTATED;
            } else {
                cls = absorbDecoy(t, f, candidate,
                        FieldComparator.classify(f, truth, candidate, isStated, convention, in.tolerance()));
            }
            tally.get(f).merge(cls, 1, Integer::sum);
            if (isStated) {
                stated.merge(f, 1, Integer::sum);
            }
            if (cls.isMismatch()) {
                // a wrong_direction fact shows the candidate's link as written (b->a)
                String shown = cls == FactClass.WRONG_DIRECTION ? c.key() : FieldComparator.display(f, candidate);
                mismatches.add(new Report.Mismatch(t.sheet().factKind(), t.key(), f.stem(), cls.id(),
                        FieldComparator.display(f, truth), FieldComparator.unitDisplay(f, truth), shown,
                        FieldComparator.unitDisplay(f, candidate), fact.map(Fact::sentence).orElse(null)));
            }
        }
    }

    /** The convention of a node or node-product field, decided by the truth node's type. */
    private Optional<Conventions.Value> convention(Field f, Entity truth) {
        String node = switch (truth.sheet()) {
            case NODES, NODE_PRODUCTS -> truth.norm().get(0);
            default -> null;
        };
        return node == null ? Optional.empty() : Conventions.of(f, truthTypes.get(node));
    }

    /** A wrong value that equals a decoy value for the same entity and field becomes decoy_absorbed. */
    private FactClass absorbDecoy(Entity t, Field f, Value candidate, FactClass cls) {
        if (cls != FactClass.WRONG && cls != FactClass.WRONG_UNIT && cls != FactClass.WRONG_TYPE
                && cls != FactClass.FILLED_UNSTATED) {
            return cls;
        }
        for (Decoy d : valueDecoys.getOrDefault(new FactKey(t.sheet(), t.norm(), f.stem()), List.of())) {
            if (FieldComparator.equal(f, decoyValue(f, d), candidate, in.tolerance())) {
                decoyValues.add(new Report.DecoyValue(d.entity(), d.field(), d.value(), d.unit(), d.sentence()));
                return FactClass.DECOY_ABSORBED;
            }
        }
        return cls;
    }

    private Value decoyValue(Field f, Decoy d) {
        String value = d.value() == null ? "" : d.value().trim();
        if (f.type() == Field.Type.GEO) {
            String[] latLng = value.split(",", 2);
            return new Value(latLng[0].trim(), latLng.length > 1 ? latLng[1].trim() : "", truthPeriod);
        }
        return new Value(value, d.unit() == null ? "" : d.unit(), truthPeriod);
    }

    /** A candidate entity matched by nothing: one fabricated mismatch — carrying the decoy's sentence when it is a decoy entity, which decoys.decoy_entities_absorbed counts. */
    private void scoreFabricated(Entity e) {
        Optional<Decoy> decoy = decoyIn(e);
        int facts = 1;
        Field existence = null;
        for (Field f : Field.of(e.sheet())) {
            if (f.isExistence()) {
                existence = f;
            } else if (!FieldComparator.blank(f, FieldComparator.of(f, e, candidatePeriod))) {
                facts++;
            }
        }
        fabricatedFacts += facts;
        fabricated.get(e.sheet()).add(e.key());
        mismatches.add(new Report.Mismatch(e.sheet().factKind(), e.key(), existence.stem(),
                FactClass.FABRICATED.id(), null, null,
                FieldComparator.display(existence, FieldComparator.of(existence, e, candidatePeriod)), null,
                decoy.map(Decoy::sentence).orElse(null)));
    }

    /** The decoy entity a fabricated entity carries: itself, or a decoy node it names. */
    private Optional<Decoy> decoyIn(Entity e) {
        Decoy own = entityDecoys.getOrDefault(e.sheet(), Map.of()).get(e.norm());
        if (own != null || e.sheet() == Sheet.PRODUCTS) {
            return Optional.ofNullable(own);
        }
        for (String name : e.sheet() == Sheet.LINKS ? e.norm() : e.norm().subList(0, 1)) {
            Decoy d = decoyNodes.get(name);
            if (d != null) {
                return Optional.of(d);
            }
        }
        return Optional.empty();
    }

    private Report.L2 l2() {
        Map<String, Map<String, Integer>> perField = new LinkedHashMap<>();
        int statedFacts = 0;
        int correct = 0;
        int denominator = fabricatedFacts;
        for (Field f : Field.values()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("stated", stated.get(f));
            for (FactClass c : FactClass.perField()) {
                int n = tally.get(f).getOrDefault(c, 0);
                counts.put(c.id(), n);
                denominator += c.inPrecision() ? n : 0;
            }
            perField.put(f.key(), counts);
            statedFacts += stated.get(f);
            correct += counts.get(FactClass.CORRECT.id());
        }
        Map<String, List<String>> fab = new LinkedHashMap<>();
        fabricated.forEach((sheet, keys) -> fab.put(sheet.id(), List.copyOf(keys)));
        List<Report.Mismatch> violations = mismatches.stream()
                .filter(m -> m.cls().equals(FactClass.CONVENTION_VIOLATION.id())).toList();
        return new Report.L2(ratio(correct, statedFacts), ratio(correct, denominator), statedFacts, correct,
                fabricatedFacts, perField, fab, violations, List.copyOf(mismatches), in.tolerance(), in.lenientNumbers());
    }

    // ---- decoys -----------------------------------------------------------------------------

    private void indexLedger() {
        if (in.facts() == null) {
            return;
        }
        for (Fact f : in.facts()) {
            Sheet.byFactKind(f.kind()).ifPresent(sheet -> Matcher.parseKey(sheet, f.entity())
                    .ifPresent(norm -> ledger.putIfAbsent(new FactKey(sheet, norm, f.field()), f)));
        }
    }

    private void indexDecoys() {
        if (in.decoys() == null) {
            return;
        }
        for (Decoy d : in.decoys()) {
            Sheet sheet = decoySheet(d.kind());
            if (sheet == null) {
                continue;
            }
            Optional<List<String>> norm = Matcher.parseKey(sheet, d.entity());
            if (norm.isEmpty()) {
                continue;
            }
            if (d.decoyEntity()) {
                entityDecoys.computeIfAbsent(sheet, s -> new LinkedHashMap<>()).putIfAbsent(norm.get(), d);
                decoyNodeNames(sheet, norm.get()).forEach(name -> decoyNodes.putIfAbsent(name, d));
            } else {
                valueDecoys.computeIfAbsent(new FactKey(sheet, norm.get(), d.field()), k -> new ArrayList<>()).add(d);
            }
        }
        // the manifest lists every decoy node by name, including ones only a link or row introduces
        JsonNode listed = in.manifest() == null ? null : in.manifest().get("decoy_entities");
        if (listed != null && listed.isArray()) {
            for (JsonNode name : listed) {
                String n = Normaliser.name(name.asText());
                in.decoys().stream().filter(Decoy::decoyEntity)
                        .filter(d -> Optional.ofNullable(decoySheet(d.kind()))
                                .flatMap(s -> Matcher.parseKey(s, d.entity())).map(k -> k.contains(n)).orElse(false))
                        .findFirst().ifPresent(d -> decoyNodes.putIfAbsent(n, d));
            }
        }
    }

    /** The decoy node names a decoy entity introduces: a decoy node itself, a link endpoint that is no truth node, or the node of a node-product row that is no truth node. */
    private List<String> decoyNodeNames(Sheet sheet, List<String> norm) {
        if (sheet == Sheet.NODES) {
            return norm;
        }
        Set<String> truthNodes = new LinkedHashSet<>();
        Matcher.entities(in.truth(), Sheet.NODES).forEach(e -> truthNodes.add(e.norm().get(0)));
        List<String> parts = sheet == Sheet.LINKS ? norm : norm.subList(0, 1);
        return parts.stream().filter(n -> !truthNodes.contains(n)).toList();
    }

    /** The sheet a decoy's kind is about: node, link, demand and inventory (node-product rows). */
    private static Sheet decoySheet(String kind) {
        return switch (kind == null ? "" : kind) {
            case "node" -> Sheet.NODES;
            case "link" -> Sheet.LINKS;
            case "demand", "inventory" -> Sheet.NODE_PRODUCTS;
            default -> null;
        };
    }

    /** Decoy entities C contains: a node as a node, a link endpoint or a node-product node; a link as that link; a demand or inventory row as that node-product row. */
    private Report.Decoys decoys(Map<Sheet, SheetMatch> matches) {
        if (in.decoys() == null) {
            return new Report.Decoys(0, List.of(), List.of());
        }
        Map<Sheet, Set<List<String>>> present = new EnumMap<>(Sheet.class);
        for (Sheet s : Sheet.values()) {
            Set<List<String>> keys = new LinkedHashSet<>();
            matches.get(s).candidate().forEach(e -> keys.add(e.norm()));
            present.put(s, keys);
        }
        Set<List<String>> nodes = present.get(Sheet.NODES);
        matches.get(Sheet.LINKS).candidate().forEach(e -> e.norm().forEach(n -> nodes.add(List.of(n))));
        matches.get(Sheet.NODE_PRODUCTS).candidate().forEach(e -> nodes.add(e.norm().subList(0, 1)));
        Set<String> absorbed = new LinkedHashSet<>();
        for (Decoy d : in.decoys()) {
            Sheet sheet = decoySheet(d.kind());
            Optional<List<String>> key = sheet == null ? Optional.empty() : Matcher.parseKey(sheet, d.entity());
            if (!d.decoyEntity() || key.isEmpty()) {
                continue;
            }
            if (present.get(sheet).contains(key.get())) {
                absorbed.add(d.entity());
            }
            // a decoy node the sentence introduced, used by C anywhere (as a node, endpoint or row node)
            List<String> raw = List.of(sheet == Sheet.LINKS ? d.entity().split("->", -1) : d.entity().split("\\|", 2));
            for (int i = 0; i < key.get().size() && i < raw.size(); i++) {
                String name = key.get().get(i);
                if (decoyNodes.containsKey(name) && nodes.contains(List.of(name))) {
                    absorbed.add(raw.get(i).trim());
                }
            }
        }
        return new Report.Decoys(in.decoys().size(), List.copyOf(absorbed), List.copyOf(decoyValues));
    }

    // ---- report extras ----------------------------------------------------------------------

    private String runId() {
        JsonNode id = in.manifest() == null ? null : in.manifest().get("run_id");
        return id == null || id.isNull() ? null : id.asText();
    }

    /** {seed, params, counts} from manifest.json, or null. */
    private JsonNode manifestBlock() {
        if (in.manifest() == null) {
            return null;
        }
        ObjectNode block = Json.mapper().createObjectNode();
        for (String key : List.of("seed", "params", "counts")) {
            block.set(key, in.manifest().get(key));
        }
        return block;
    }

    /** ISO-8601 UTC to the second: SOURCE_DATE_EPOCH when set (reproducible reports), else now. */
    private static String verifiedAt() {
        String epoch = System.getenv("SOURCE_DATE_EPOCH");
        Instant when = epoch != null && epoch.trim().matches("\\d+")
                ? Instant.ofEpochSecond(Long.parseLong(epoch.trim())) : Instant.now();
        return DateTimeFormatter.ISO_INSTANT.format(when.truncatedTo(ChronoUnit.SECONDS));
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    /** Harmonic mean; 0 when either is 0. */
    private static double f1(double a, double b) {
        return a == 0 || b == 0 ? 0.0 : 2 * a * b / (a + b);
    }
}
