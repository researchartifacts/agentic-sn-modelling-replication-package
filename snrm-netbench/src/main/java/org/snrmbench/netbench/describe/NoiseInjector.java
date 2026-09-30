package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.GenerateParams.NoiseType;
import org.snrmbench.netbench.generate.Namer;
import org.snrmbench.netbench.generate.Ranges;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Product;
import org.snrmbench.netbench.util.Rng;
import org.snrmbench.netbench.util.Rng.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stage noise: interleaves n_noise = round(r / (1 − r) × n_fact) noise sentences with the fact statements after the header, at uniformly random positions. */
public final class NoiseInjector {

    /** The body with noise inserted, the decoys it carries, and the names of decoy-only entities. */
    public record Result(List<Section> sections, List<Decoy> decoys, List<String> decoyEntities) {
    }

    private static final int FIRST_YEAR = 2012;
    private static final int LAST_YEAR = 2025;

    private NoiseInjector() {
    }

    public static Result inject(List<Section> body, Network truth, List<Fact> facts, GenerateParams p, Rng rng) {
        if (p.noiseType() == NoiseType.NONE || p.noiseRatio().signum() == 0) {
            return new Result(body, List.of(), List.of());
        }
        int nFact = (int) body.stream().flatMap(s -> s.paragraphs().stream()).mapToLong(List::size).sum();
        int nNoise = noiseCount(p.noiseRatio(), nFact);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < nFact + nNoise; i++) {
            slots.add(i);
        }
        List<Integer> noiseSlots = new ArrayList<>(rng.sample(Stage.NOISE, slots, nNoise));
        noiseSlots.sort(Integer::compare);

        AdversarialNoise adversarial = p.noiseType() == NoiseType.ADVERSARIAL
                ? new AdversarialNoise(truth, facts, p, rng) : null;
        Source source = new Source(p.noiseType(), truth, rng);
        List<Statement> noise = new ArrayList<>();
        for (int k = 0; k < nNoise; k++) {
            noise.add(adversarial != null ? adversarial.next(k + 1) : Statement.noise(source.next()));
        }

        List<Section> out = place(body, noiseSlots, noise, nFact + nNoise);
        return new Result(out, adversarial == null ? List.of() : adversarial.decoys(),
                adversarial == null ? List.of() : adversarial.decoyEntities());
    }

    /** round(r / (1 − r) × n_fact), half-up. */
    static int noiseCount(BigDecimal ratio, int nFact) {
        return ratio.divide(BigDecimal.ONE.subtract(ratio), java.math.MathContext.DECIMAL64)
                .multiply(BigDecimal.valueOf(nFact)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** Rebuilds the sections with each noise statement placed before the fact statement that follows its slot. */
    private static List<Section> place(List<Section> body, List<Integer> noiseSlots, List<Statement> noise, int total) {
        List<List<List<Statement>>> out = new ArrayList<>();
        List<int[]> factCoords = new ArrayList<>();
        List<Statement> factList = new ArrayList<>();
        for (int s = 0; s < body.size(); s++) {
            List<List<Statement>> paras = new ArrayList<>();
            for (int q = 0; q < body.get(s).paragraphs().size(); q++) {
                paras.add(new ArrayList<>());
                for (Statement st : body.get(s).paragraphs().get(q)) {
                    factCoords.add(new int[] {s, q});
                    factList.add(st);
                }
            }
            out.add(paras);
        }
        List<Statement> pending = new ArrayList<>();
        int nextNoise = 0;
        int nextFact = 0;
        int[] last = null;
        for (int slot = 0; slot < total; slot++) {
            if (nextNoise < noiseSlots.size() && noiseSlots.get(nextNoise) == slot) {
                pending.add(noise.get(nextNoise++));
                continue;
            }
            int[] c = factCoords.get(nextFact);
            List<Statement> para = out.get(c[0]).get(c[1]);
            para.addAll(pending);
            pending.clear();
            para.add(factList.get(nextFact++));
            last = c;
        }
        if (!pending.isEmpty()) {
            if (last == null) {
                throw new IllegalStateException("noise without any fact statement to place it among");
            }
            out.get(last[0]).get(last[1]).addAll(pending);
        }
        List<Section> sections = new ArrayList<>();
        for (int s = 0; s < body.size(); s++) {
            sections.add(new Section(body.get(s).heading(), out.get(s)));
        }
        return sections;
    }

    /** Boilerplate and topical sentences: drawn without replacement from the corpus until it is used up, then with replacement; topical placeholders filled with the network's own product, nodes and regions. */
    private static final class Source {
        private final List<String> order;
        private final List<String> corpus;
        private final Network truth;
        private final Rng rng;
        private final boolean topical;
        private int next;

        Source(NoiseType type, Network truth, Rng rng) {
            this.topical = type == NoiseType.TOPICAL;
            this.corpus = type == NoiseType.ADVERSARIAL ? List.of()
                    : ParaphrasePool.lines(topical ? "/noise/topical.txt" : "/noise/boilerplate.txt");
            this.order = new ArrayList<>(corpus);
            this.truth = truth;
            this.rng = rng;
            rng.shuffle(order, Stage.NOISE);
        }

        String next() {
            String line = next < order.size() ? order.get(next++) : rng.choice(Stage.NOISE, corpus);
            if (!topical) {
                return line;
            }
            Map<String, String> v = new LinkedHashMap<>();
            v.put("product", rng.choice(Stage.NOISE, truth.products()).name());
            v.put("region", rng.choice(Stage.NOISE, allRegions()));
            v.put("node", rng.choice(Stage.NOISE, truth.nodes()).name());
            v.put("year", Integer.toString(rng.uniformInt(Stage.NOISE, FIRST_YEAR, LAST_YEAR)));
            v.put("pct", Integer.toString(rng.uniformInt(Stage.NOISE, 2, 45)));
            return Templates.fill(line, v);
        }
    }

    static List<String> allRegions() {
        List<String> regions = new ArrayList<>(Ranges.DOMESTIC_REGIONS);
        regions.addAll(Ranges.OVERSEAS_REGIONS);
        return regions;
    }

    /** The products, for decoy sentences. */
    static List<String> productNames(Network truth) {
        return truth.products().stream().map(Product::name).toList();
    }

    /** The company names used for realistic supplier decoys. */
    static List<String> companies() {
        return Namer.resourceLines("/names/companies.txt");
    }
}
