package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.GenerateParams.DistanceMode;
import org.snrmbench.netbench.generate.GenerateParams.Naming;
import org.snrmbench.netbench.generate.GenerateParams.NoiseType;
import org.snrmbench.netbench.generate.GenerateParams.NumberLocale;
import org.snrmbench.netbench.generate.GenerateParams.Style;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.model.TimeUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression tests for the confirmed findings of the code review. */
class Review2RegressionTest {

    private static final Pattern TYPED_NAME = Pattern.compile("\\b(?:SUP|PLT|DC|CUS)-\\d{2,3}\\b");

    private static GenerateParams params(long seed, int nodes, int products, Style style, Naming naming,
                                         NoiseType noise, String ratio, DistanceMode distance, TimeUnit period) {
        GenerateParams d = GenerateParams.defaults(seed, nodes, 4, "Widget");
        return new GenerateParams(seed, nodes, 4, "Widget", products, naming, style, d.order(),
                GenerateParams.Units.UNIFORM, period, 1, 90, 1, 2, 0, BigDecimal.ONE, noise, new BigDecimal(ratio),
                false, distance, NumberLocale.PLAIN);
    }

    private static List<String> names(String sentence) {
        List<String> out = new ArrayList<>();
        Matcher m = TYPED_NAME.matcher(sentence);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** A coreference noun never directly follows a sentence that names another node of the chain's type. */
    @Test
    void narrativeCoreferenceNeverFollowsAnotherNodesName() {
        List<String> nouns = new ArrayList<>();
        for (String pool : List.of("coref-supplier", "coref-plant", "coref-dc")) {
            nouns.addAll(ParaphrasePool.lines("/narrative/" + pool + ".txt"));
        }
        int checked = 0;
        for (long seed = 1; seed <= 30; seed++) {
            Generator.Generated g = Generator.build(params(seed, 50, seed % 3 == 0 ? 3 : 1, Style.NARRATIVE,
                    Naming.TYPED, NoiseType.NONE, "0", DistanceMode.NONE, TimeUnit.DAY), null);
            for (String unit : g.description().sentences()) {
                String[] sentences = unit.split("(?<=\\.) (?=[A-Z])");
                List<String> first = names(sentences[0]);
                if (first.isEmpty()) {
                    continue;
                }
                String own = first.get(0);
                for (int i = 1; i < sentences.length; i++) {
                    String s = sentences[i];
                    if (nouns.stream().anyMatch(s::startsWith)) {
                        checked++;
                        assertThat(names(sentences[i - 1])).as("before \"%s\" in: %s", s, unit)
                                .allMatch(own::equals);
                    }
                }
            }
        }
        assertThat(checked).isPositive();
    }

    /** In distance mode every statement about a lane gives a distance, decoys included. */
    @Test
    void distanceModeDecoysStateDistancesToo() {
        for (TimeUnit period : List.of(TimeUnit.DAY, TimeUnit.HOUR)) {
            for (long seed = 1; seed <= 5; seed++) {
                Generator.Generated g = Generator.build(params(seed, 30, 1, Style.TEMPLATE, Naming.TYPED,
                        NoiseType.ADVERSARIAL, "0.6", DistanceMode.KM, period), null);
                assertThat(g.description().markdown()).doesNotContain("lead time of");
                assertThat(g.description().decoys()).anyMatch(d -> d.field().equals("lead_time"));
            }
        }
    }

    /** Realistic decoy names count Roman numerals up without limit (this run used to crash). */
    @Test
    void manyRealisticDecoysDoNotRunOutOfNames() {
        GenerateParams d = GenerateParams.defaults(1, 700, 5, "Widget");
        Generator.Generated g = Generator.build(new GenerateParams(1, 700, 5, "Widget", 1, Naming.REALISTIC,
                d.style(), d.order(), d.units(), d.periodUnit(), 1, 90, 1, 2, 0, d.attributeCoverage(),
                NoiseType.ADVERSARIAL, new BigDecimal("0.8"), false, d.distanceMode(), d.locale()), null);
        assertThat(g.description().decoyEntities()).doesNotHaveDuplicates();
    }

    /** Decoy markers name clearly past years, so no reader takes "Until {year}" for the present. */
    @Test
    void markerYearsLieClearlyInThePast() {
        Pattern year = Pattern.compile("\\b(20\\d\\d)\\b");
        for (long seed = 1; seed <= 5; seed++) {
            Generator.Generated g = Generator.build(params(seed, 30, 1, Style.TEMPLATE, Naming.TYPED,
                    NoiseType.ADVERSARIAL, "0.6", DistanceMode.NONE, TimeUnit.DAY), null);
            for (Decoy decoy : g.description().decoys()) {
                Matcher m = year.matcher(decoy.marker());
                if (m.find()) {
                    assertThat(Integer.parseInt(m.group(1))).isBetween(AdversarialNoise.FIRST_MARKER_YEAR,
                            AdversarialNoise.LAST_MARKER_YEAR);
                }
            }
        }
    }

    /** A localized description declares its number format in the header. */
    @Test
    void aLocalizedHeaderDeclaresItsNumberFormat() {
        for (NumberLocale locale : List.of(NumberLocale.EN, NumberLocale.DE)) {
            GenerateParams d = GenerateParams.defaults(2, 20, 4, "Widget");
            Generator.Generated g = Generator.build(new GenerateParams(2, 20, 4, "Widget", 1, d.naming(), d.style(),
                    d.order(), d.units(), d.periodUnit(), 1, 90, 1, 2, 0, d.attributeCoverage(), d.noiseType(),
                    d.noiseRatio(), false, d.distanceMode(), locale), null);
            assertThat(g.description().sentences().subList(0, g.description().headerSentences()))
                    .contains(NumberStyle.of(locale).headerSentence());
        }
    }
}
