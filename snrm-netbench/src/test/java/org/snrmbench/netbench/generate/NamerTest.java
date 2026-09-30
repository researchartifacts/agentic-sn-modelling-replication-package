package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.model.Network;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** naming schemes; realistic city names follow each node's region. */
class NamerTest {

    private static Network build(long seed, int nodes, int echelons, GenerateParams.Naming naming) {
        GenerateParams d = GenerateParams.defaults(seed, nodes, echelons, "Widget");
        return Generator.build(new GenerateParams(seed, nodes, echelons, "Widget", 1, naming, d.style(), d.order(),
                d.units(), d.periodUnit(), 1, 90, 1, 2, 0, d.attributeCoverage(), d.noiseType(), d.noiseRatio(),
                false, d.distanceMode(), d.locale()), null).full();
    }

    @Test
    void citiesAreGroupedIntoTheSevenRegions() {
        Map<String, List<String>> byRegion = Namer.citiesByRegion();
        assertThat(byRegion.keySet()).containsExactlyInAnyOrder("EU-West", "EU-North", "EU-South", "EU-East",
                "EU-Central", "APAC", "AMER");
        byRegion.forEach((region, cities) -> assertThat(cities).as(region)
                .hasSizeGreaterThanOrEqualTo(region.startsWith("EU-") ? 40 : 10));
    }

    @Test
    void realisticNamesComeFromTheNodesOwnRegion() {
        Map<String, List<String>> byRegion = Namer.citiesByRegion();
        List<String> companies = Namer.resourceLines("/names/companies.txt");
        Pattern roman = Pattern.compile(" (II|III|IV|V|VI|VII|VIII|IX|X)$");
        for (long seed = 1; seed <= 3; seed++) {
            for (Node n : build(seed, 600, 5, GenerateParams.Naming.REALISTIC).nodes()) {
                String base = roman.matcher(n.name()).replaceFirst("");
                if (n.type() == NodeType.SUPPLIER) {
                    assertThat(companies).as(n.name()).contains(base);
                } else {
                    assertThat(byRegion.get(n.region())).as("%s in %s", n.name(), n.region()).contains(base);
                }
            }
        }
    }

    @Test
    void typedAndNeutralNamesHaveTheirFormats() {
        assertThat(build(1, 25, 5, GenerateParams.Naming.TYPED).nodes()).extracting(Node::name)
                .allMatch(name -> name.matches("(SUP|PLT|DCC|DCR|CUS)-\\d{2}"));
        assertThat(build(1, 150, 4, GenerateParams.Naming.NEUTRAL).nodes()).extracting(Node::name)
                .allMatch(name -> name.matches("N\\d{3}")).doesNotHaveDuplicates();
        assertThat(build(1, 250, 3, GenerateParams.Naming.TYPED).nodes()).extracting(Node::name)
                .anyMatch(name -> name.matches("CUS-\\d{3}"));
    }
}
