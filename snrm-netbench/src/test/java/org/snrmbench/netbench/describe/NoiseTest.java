package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.FactLedger;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.model.Network;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** noise ratio, and decoys that never state a true value or name a real entity as a decoy. */
class NoiseTest {

    static GenerateParams params(long seed, int nodes, GenerateParams.Style style, GenerateParams.NoiseType type,
                                 String ratio, GenerateParams.Naming naming) {
        GenerateParams d = GenerateParams.defaults(seed, nodes, 4, "Widget");
        return new GenerateParams(seed, nodes, 4, "Widget", 2, naming, style, d.order(), GenerateParams.Units.MIXED,
                d.periodUnit(), 1, 90, 1, 2, 0, new BigDecimal("0.8"), type, new BigDecimal(ratio), false,
                d.distanceMode(), d.locale());
    }

    static Stream<Arguments> noisyRuns() {
        List<Arguments> out = new ArrayList<>();
        for (GenerateParams.NoiseType type : List.of(GenerateParams.NoiseType.BOILERPLATE,
                GenerateParams.NoiseType.TOPICAL, GenerateParams.NoiseType.ADVERSARIAL)) {
            for (String ratio : List.of("0.3", "0.6")) {
                for (GenerateParams.Style style : GenerateParams.Style.values()) {
                    out.add(Arguments.of(type, ratio, style));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("noisyRuns")
    void noiseRatioIsWithinTwoPointsOfTheRequest(GenerateParams.NoiseType type, String ratio,
                                                 GenerateParams.Style style) {
        Generator.Generated g = Generator.build(params(7, 40, style, type, ratio, GenerateParams.Naming.TYPED), null);
        Description d = g.description();
        assertThat(d.factSentences()).isGreaterThanOrEqualTo(50);
        double actual = (double) d.noiseSentences() / (d.noiseSentences() + d.factSentences());
        assertThat(actual).isCloseTo(Double.parseDouble(ratio), org.assertj.core.data.Offset.offset(0.02));
        // noise never enters the header: the header sentences carry the meta facts
        assertThat(d.sentences().subList(0, d.headerSentences())).allMatch(s -> s.startsWith("This document")
                || s.startsWith("The network") || s.startsWith("Planning uses"));
    }

    @ParameterizedTest(name = "seed {0} {1}")
    @MethodSource("adversarialRuns")
    void decoysNeverStateATrueValueAndDecoyEntitiesDoNotExist(long seed, GenerateParams.Naming naming) {
        Generator.Generated g = Generator.build(params(seed, 30, GenerateParams.Style.TEMPLATE,
                GenerateParams.NoiseType.ADVERSARIAL, "0.6", naming), null);
        Network b = g.truth();
        Map<String, Fact> ledger = FactLedger.index(g.facts());
        List<Decoy> decoys = g.description().decoys();
        assertThat(decoys).isNotEmpty();
        List<String> realNodes = b.nodes().stream().map(n -> n.name().toLowerCase(Locale.ROOT)).toList();
        for (Decoy d : decoys) {
            assertThat(g.description().sentence(d.sentence())).startsWith(d.marker());
            if (d.decoyEntity()) {
                boolean exists = switch (d.kind()) {
                    case "node" -> realNodes.contains(d.entity().toLowerCase(Locale.ROOT));
                    case "link" -> b.links().stream().anyMatch(l -> l.key().equals(d.entity()));
                    default -> b.nodeProducts().stream().anyMatch(np -> np.key().equals(d.entity()));
                };
                assertThat(exists).as("decoy entity %s exists in B", d.entity()).isFalse();
            } else {
                String kind = switch (d.kind()) {
                    case "demand", "inventory" -> "node_product";
                    default -> d.kind();
                };
                Fact truth = ledger.get(FactLedger.key(kind, d.entity(), d.field()));
                assertThat(truth).as("decoy %s names a real fact", d).isNotNull();
                assertThat(d.value()).as("decoy %s repeats the truth", d).isNotEqualTo(truth.value());
                if (!d.field().equals("region")) {
                    BigDecimal t = new BigDecimal(truth.value());
                    BigDecimal v = new BigDecimal(d.value());
                    assertThat(v.subtract(t).abs()).as("decoy %s is at least 20%% off", d)
                            .isGreaterThanOrEqualTo(t.abs().multiply(new BigDecimal("0.2")));
                }
            }
        }
        List<String> decoyNames = g.description().decoyEntities().stream()
                .map(n -> n.toLowerCase(Locale.ROOT)).toList();
        assertThat(decoyNames).doesNotContainAnyElementsOf(realNodes).doesNotHaveDuplicates();
    }

    static Stream<Arguments> adversarialRuns() {
        List<Arguments> out = new ArrayList<>();
        for (long seed = 1; seed <= 3; seed++) {
            for (GenerateParams.Naming naming : GenerateParams.Naming.values()) {
                out.add(Arguments.of(seed, naming));
            }
        }
        return out.stream();
    }
}
