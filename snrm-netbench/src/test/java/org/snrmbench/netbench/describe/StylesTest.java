package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.GenerateParams.Naming;
import org.snrmbench.netbench.generate.GenerateParams.Order;
import org.snrmbench.netbench.generate.GenerateParams.Style;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The paraphrased and narrative styles over naming, order, coverage and seeds. */
class StylesTest {

    static GenerateParams params(long seed, int nodes, int echelons, Style style, Naming naming, Order order,
                                 String coverage) {
        GenerateParams d = GenerateParams.defaults(seed, nodes, echelons, "Widget");
        return new GenerateParams(seed, nodes, echelons, "Widget", seed == 2 ? 2 : 1, naming, style, order,
                seed == 3 ? GenerateParams.Units.MIXED : GenerateParams.Units.UNIFORM, d.periodUnit(), 1, 90, 1, 2,
                seed == 1 ? 2 : 0, new BigDecimal(coverage), d.noiseType(), d.noiseRatio(), false, d.distanceMode(),
                d.locale());
    }

    static Stream<Arguments> grid() {
        List<Arguments> out = new ArrayList<>();
        for (Style style : List.of(Style.PARAPHRASED, Style.NARRATIVE)) {
            for (Naming naming : Naming.values()) {
                for (Order order : Order.values()) {
                    for (long seed = 1; seed <= 3; seed++) {
                        out.add(Arguments.of(style, naming, order, seed, seed == 2 ? "0.6" : "1.0"));
                    }
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1} {2} seed={3} coverage={4}")
    @MethodSource("grid")
    void everyCellBuildsAndRelativeFactsFollowTheirReferent(Style style, Naming naming, Order order, long seed,
                                                             String coverage) {
        Generator.Generated g = Generator.build(params(seed, 30, 5, style, naming, order, coverage), null);
        List<Fact> facts = g.facts();
        if (style == Style.PARAPHRASED) {
            assertThat(facts).noneMatch(Fact::relative);
        }
        for (Fact f : facts) {
            if (!f.relative()) {
                continue;
            }
            // an absolute statement of the same value by another node of the same tier comes first
            assertThat(facts).as("referent of %s", f).anyMatch(r -> !r.relative() && r.stated()
                    && r.kind().equals(f.kind()) && r.field().equals(f.field()) && r.value().equals(f.value())
                    && java.util.Objects.equals(r.unit(), f.unit()) && !r.entity().equals(f.entity())
                    && sameTierAndProduct(g.truth(), r.entity(), f.entity()) && r.sentence() < f.sentence());
        }
    }

    private static boolean sameTierAndProduct(Network b, String a, String c) {
        String[] pa = a.split("\\|");
        String[] pc = c.split("\\|");
        if (pa.length != pc.length || (pa.length == 2 && !pa[1].equals(pc[1]))) {
            return false;
        }
        Node na = b.node(pa[0]).orElseThrow();
        Node nc = b.node(pc[0]).orElseThrow();
        return na.tierIndex() == nc.tierIndex();
    }

    @Test
    void narrativeHasTheFourSectionsInOrderAndTheHeaderOfEveryStyle() {
        String template = Generator.build(params(4, 25, 4, Style.TEMPLATE, Naming.TYPED, Order.BY_TIER, "1.0"),
                null).description().markdown();
        String narrative = Generator.build(params(4, 25, 4, Style.NARRATIVE, Naming.TYPED, Order.BY_TIER, "1.0"),
                null).description().markdown();
        String paraphrased = Generator.build(params(4, 25, 4, Style.PARAPHRASED, Naming.TYPED, Order.BY_TIER, "1.0"),
                null).description().markdown();
        assertThat(narrative).containsSubsequence("\n## Overview\n", "\n## Facilities\n", "\n## Logistics lanes\n",
                "\n## Demand and inventory\n");
        String header = template.substring(0, template.indexOf("\n\n", template.indexOf("\n\n") + 2));
        assertThat(narrative).startsWith(header);
        assertThat(paraphrased).startsWith(header);
    }

    @Test
    void paraphrasedMergesIdenticalLanesOfOneSourceIntoOneSentence() {
        // search seeds for a network with at least one merged lane group and check its facts share a sentence
        for (long seed = 1; seed <= 200; seed++) {
            Generator.Generated g = Generator.build(params(seed, 40, 4, Style.PARAPHRASED, Naming.TYPED,
                    Order.BY_TIER, "0.2"), null);
            List<Fact> linkExists = g.facts().stream()
                    .filter(f -> f.kind().equals("link") && f.field().equals("exists")).toList();
            long sentences = linkExists.stream().map(Fact::sentence).distinct().count();
            if (sentences < linkExists.size()) {
                Integer shared = linkExists.stream().map(Fact::sentence)
                        .filter(s -> linkExists.stream().filter(f -> f.sentence().equals(s)).count() > 1)
                        .findFirst().orElseThrow();
                String text = g.description().sentence(shared);
                linkExists.stream().filter(f -> f.sentence().equals(shared))
                        .forEach(f -> assertThat(text).contains(f.entity().split("->")[1]));
                return;
            }
        }
        throw new AssertionError("no seed produced a merged lane group");
    }
}
