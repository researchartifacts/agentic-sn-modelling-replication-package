package org.snrmbench.netbench.describe;

import org.snrmbench.netbench.cli.Main;
import org.snrmbench.netbench.generate.Fact;
import org.snrmbench.netbench.generate.GenerateParams;
import org.snrmbench.netbench.generate.GenerateParams.DistanceMode;
import org.snrmbench.netbench.generate.GenerateParams.NumberLocale;
import org.snrmbench.netbench.generate.GenerateParams.Style;
import org.snrmbench.netbench.generate.GenerateParams.Units;
import org.snrmbench.netbench.generate.Generator;
import org.snrmbench.netbench.model.Link;
import org.snrmbench.netbench.model.Node;
import org.snrmbench.netbench.model.NodeType;
import org.snrmbench.netbench.schema.Sheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** description options: --locale, --distance-mode km and --geo. */
class LocaleDistanceGeoTest {

    static GenerateParams params(long seed, Style style, NumberLocale locale, DistanceMode distance, Units units,
                                 boolean geo) {
        GenerateParams d = GenerateParams.defaults(seed, 30, 5, "Widget");
        return new GenerateParams(seed, 30, 5, "Widget", 2, d.naming(), style, d.order(), units, d.periodUnit(), 1, 90,
                1, 2, 0, new BigDecimal("0.8"), d.noiseType(), d.noiseRatio(), geo, distance, locale);
    }

    @ParameterizedTest
    @CsvSource({
            "EN, 1234567.89, '1,234,567.89'", "DE, 1234567.89, '1.234.567,89'", "EN, 999, 999",
            "DE, 1000, 1.000", "EN, -2.35, -2.35", "DE, -12345.5, '-12.345,5'", "PLAIN, 1234.50, 1234.50"})
    void numbersAreWrittenAndReadInTheirStyle(NumberStyle style, String canonical, String written) {
        assertThat(style.format(canonical)).isEqualTo(written);
        assertThat(written).matches(style.regex());
        assertThat(style.parse(written)).isEqualByComparingTo(canonical);
    }

    static Stream<Arguments> grid() {
        List<Arguments> out = new ArrayList<>();
        for (Style style : Style.values()) {
            for (NumberLocale locale : NumberLocale.values()) {
                for (DistanceMode distance : DistanceMode.values()) {
                    for (Units units : Units.values()) {
                        out.add(Arguments.of(style, locale, distance, units));
                    }
                }
            }
        }
        return out.stream();
    }

    /** Every combination builds: the self-checks, and the template round trip, pass in every style. */
    @ParameterizedTest(name = "{0} {1} {2} {3}")
    @MethodSource("grid")
    void everyCombinationBuilds(Style style, NumberLocale locale, DistanceMode distance, Units units) {
        for (long seed = 1; seed <= 2; seed++) {
            Generator.Generated g = Generator.build(params(seed, style, locale, distance, units, seed == 2), null);
            String body = g.description().markdown();
            if (distance == DistanceMode.KM) {
                assertThat(body).contains("Road lanes cover").doesNotContainPattern("\\blead time of\\b");
            }
            if (locale == NumberLocale.DE) {
                assertThat(body).containsPattern("\\d,\\d{2} per unit");
            }
        }
    }

    @Test
    void oceanLanesAreExactlyTheLanesFromOverseasSuppliersToPlants() {
        for (long seed = 1; seed <= 10; seed++) {
            Generator.Generated g = Generator.build(params(seed, Style.TEMPLATE, NumberLocale.PLAIN, DistanceMode.KM,
                    Units.UNIFORM, false), null);
            var distances = Describer.distances(g.truth(), g.facts());
            for (Link l : g.full().links()) {
                Node s = g.full().node(l.source()).orElseThrow();
                boolean overseas = s.type() == NodeType.SUPPLIER && !s.region().startsWith("EU-")
                        && g.full().node(l.target()).orElseThrow().type() == NodeType.PLANT;
                assertThat(distances.get(l.key()).mode()).as(l.key())
                        .isEqualTo(overseas ? Templates.MODE_OCEAN : Templates.MODE_ROAD);
                BigDecimal days = l.leadTime().value().multiply(BigDecimal.valueOf(l.leadTime().unit().seconds()))
                        .divide(BigDecimal.valueOf(86_400));
                assertThat(new BigDecimal(distances.get(l.key()).km()))
                        .isEqualByComparingTo(days.multiply(BigDecimal.valueOf(overseas ? 600 : 480)));
            }
        }
    }

    @Test
    void extractReadsAGermanDescriptionBackToB(@TempDir Path tmp) throws IOException {
        assertThat(Main.run("generate", "--seed", "3", "--nodes", "40", "--echelons", "4", "--final-product-name",
                "Widget", "--locale", "de", "--geo", "--distance-mode", "km", "--out-dir", tmp.toString(), "--run-id",
                "r")).isZero();
        Path out = tmp.resolve("x");
        assertThat(Main.run("extract", "--description", tmp.resolve("r/description/description.md").toString(),
                "--out", out.toString(), "--locale", "de", "--strict")).isZero();
        for (Sheet s : Sheet.values()) {
            assertThat(out.resolve(s.fileName())).hasSameBinaryContentAs(tmp.resolve("r/truth").resolve(s.fileName()));
        }
        // read as plain, the German numbers do not parse
        assertThat(Main.run("extract", "--description", tmp.resolve("r/description/description.md").toString(),
                "--out", tmp.resolve("y").toString(), "--strict")).isEqualTo(4);
        assertThat(Files.readString(tmp.resolve("r/truth/nodes.csv"))).startsWith(
                "name,type,capacity_value,capacity_time_unit,processing_time_value,processing_time_unit,fixed_cost,"
                        + "var_cost,failure_prob,region,lat,lng\n");
    }

    /** --geo, --locale and --distance-mode change A (and lat/lng), never another value of B. */
    @Test
    void descriptionOptionsChangeNoOtherValue() {
        GenerateParams base = params(9, Style.TEMPLATE, NumberLocale.PLAIN, DistanceMode.NONE, Units.MIXED, false);
        Generator.Generated plain = Generator.build(base, null);
        for (GenerateParams p : List.of(
                params(9, Style.TEMPLATE, NumberLocale.DE, DistanceMode.KM, Units.MIXED, false),
                params(9, Style.TEMPLATE, NumberLocale.PLAIN, DistanceMode.NONE, Units.MIXED, true))) {
            Generator.Generated other = Generator.build(p, null);
            for (int i = 0; i < plain.full().nodes().size(); i++) {
                Node a = plain.full().nodes().get(i);
                Node b = other.full().nodes().get(i);
                assertThat(new Node(b.name(), b.type(), b.tierIndex(), b.capacity(), b.processingTime(), b.fixedCost(),
                        b.varCost(), b.failureProb(), b.region(), null, null)).isEqualTo(a);
            }
            assertThat(other.full().links()).isEqualTo(plain.full().links());
            assertThat(other.full().nodeProducts()).isEqualTo(plain.full().nodeProducts());
            if (p.geo()) {
                assertThat(other.facts()).anyMatch(f -> f.field().equals("geo") && f.stated());
                assertThat(other.full().nodes()).allMatch(n -> n.lat() != null && n.lng() != null);
            } else {
                assertThat(other.facts()).extracting(Fact::field).doesNotContain("geo");
            }
        }
    }
}
