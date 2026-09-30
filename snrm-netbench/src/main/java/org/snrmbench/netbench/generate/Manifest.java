package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.describe.Description;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Tier;
import org.snrmbench.netbench.util.Json;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code manifest.json}. */
public final class Manifest {

    public static final String TOOL_VERSION = "0.1.0";
    private static final Pattern WORD = Pattern.compile("\\S+");

    private Manifest() {
    }

    /** The manifest. */
    public static Map<String, Object> build(GenerateParams p, String runId, Network truth, List<Fact> facts,
                                            Description d, int expectedLateral, List<String> notes,
                                            Map<String, String> files) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool_version", TOOL_VERSION);
        m.put("generated_at", generatedAt());
        m.put("run_id", runId);
        m.put("seed", p.seed());
        m.put("params", p.asMap());

        List<Map<String, Object>> tiers = new ArrayList<>();
        for (Tier t : truth.tiers()) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("index", t.index());
            tm.put("role", t.role().word());
            tm.put("type", t.type().name());
            tm.put("nodes", t.nodeNames().stream().sorted().toList());
            tiers.add(tm);
        }
        m.put("tiers", tiers);

        int words = words(d.markdown());
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("nodes", truth.nodes().size());
        counts.put("links", truth.links().size());
        counts.put("products", truth.products().size());
        counts.put("node_products", truth.nodeProducts().size());
        counts.put("facts_total", facts.size());
        counts.put("facts_stated", facts.stream().filter(Fact::stated).count());
        counts.put("sentences_header", d.headerSentences());
        counts.put("sentences_fact", d.factSentences());
        counts.put("sentences_noise", d.noiseSentences());
        counts.put("noise_ratio_actual", noiseRatio(d));
        counts.put("words", words);
        counts.put("token_estimate", BigDecimal.valueOf(words).multiply(new BigDecimal("1.3"))
                .setScale(0, RoundingMode.HALF_UP).intValueExact());
        m.put("counts", counts);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("errors", 0);
        Map<String, Integer> warnings = new TreeMap<>();
        if (expectedLateral > 0) {
            warnings.put("LATERAL_LINK", expectedLateral);
        }
        expected.put("warnings", warnings);
        m.put("expected_import", expected);
        m.put("decoy_entities", d.decoyEntities());
        m.put("notes", notes);

        Map<String, Object> fileNames = new LinkedHashMap<>();
        fileNames.put("description", Generator.DESCRIPTION);
        fileNames.put("truth", Generator.TRUTH_DIR);
        fileNames.put("facts", Generator.FACTS);
        fileNames.put("decoys", Generator.DECOYS);
        fileNames.put("full_network", Generator.FULL_NETWORK);
        m.put("files", fileNames);

        Map<String, String> sha = new TreeMap<>();
        files.forEach((path, content) -> sha.put(path, Json.sha256(content)));
        m.put("sha256", sha);
        return m;
    }

    /** Noise statements ÷ all statements after the header, to four decimals. */
    static BigDecimal noiseRatio(Description d) {
        int all = d.factSentences() + d.noiseSentences();
        if (d.noiseSentences() == 0 || all == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(d.noiseSentences()).divide(BigDecimal.valueOf(all), 4, RoundingMode.HALF_UP);
    }

    static int words(String text) {
        Matcher m = WORD.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static String generatedAt() {
        String epoch = System.getenv("SOURCE_DATE_EPOCH");
        if (epoch == null || epoch.isBlank()) {
            return null;
        }
        return Instant.ofEpochSecond(Long.parseLong(epoch.trim())).toString();
    }
}
