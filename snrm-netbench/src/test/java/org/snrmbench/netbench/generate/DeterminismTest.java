package org.snrmbench.netbench.generate;

import org.snrmbench.netbench.cli.Main;
import org.snrmbench.netbench.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** /: identical inputs give byte-identical outputs; the seed changes everything that depends on it. */
class DeterminismTest {

    private static final String[] BASE = {"generate", "--nodes", "25", "--echelons", "5",
            "--final-product-name", "Widget"};

    private static Map<String, String> hashes(Path runDir) throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(runDir)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                out.put(runDir.relativize(f).toString().replace('\\', '/'), Json.sha256(f));
            }
        }
        return out;
    }

    private static Path generate(Path out, long seed, String... extra) throws IOException {
        String[] args = Stream.concat(Stream.of(BASE), Stream.concat(
                Stream.of("--seed", Long.toString(seed), "--out-dir", out.toString(), "--run-id", "run"),
                Stream.of(extra))).toArray(String[]::new);
        assertThat(Main.run(args)).isZero();
        return out.resolve("run");
    }

    @Test
    void identicalArgumentsGiveByteIdenticalDirectories(@TempDir Path tmp) throws IOException {
        for (long seed : new long[] {1, 2, 3}) {
            Map<String, String> a = hashes(generate(tmp.resolve("a" + seed), seed));
            Map<String, String> b = hashes(generate(tmp.resolve("b" + seed), seed));
            assertThat(a).hasSize(10).isEqualTo(b);
        }
    }

    @Test
    void inMemoryBuildIsRepeatable() {
        GenerateParams p = GenerateParams.defaults(42, 60, 5, "Widget");
        assertThat(Generator.build(p, null).files()).isEqualTo(Generator.build(p, null).files());
    }

    static GenerateParams variant(long seed, GenerateParams.Style style, GenerateParams.Naming naming,
                                  GenerateParams.NoiseType noise) {
        GenerateParams d = GenerateParams.defaults(seed, 25, 5, "Widget");
        return new GenerateParams(seed, 25, 5, "Widget", 2, naming, style, d.order(), GenerateParams.Units.MIXED,
                d.periodUnit(), 1, 90, 1, 2, 1, new java.math.BigDecimal("0.7"), noise,
                noise == GenerateParams.NoiseType.NONE ? java.math.BigDecimal.ZERO : new java.math.BigDecimal("0.3"),
                false, d.distanceMode(), d.locale());
    }

    /** over every style, naming and noise combination and three seeds. */
    @Test
    void everyStyleNamingAndNoiseCombinationIsRepeatable() {
        for (long seed = 1; seed <= 3; seed++) {
            for (GenerateParams.Style style : GenerateParams.Style.values()) {
                for (GenerateParams.Naming naming : GenerateParams.Naming.values()) {
                    for (GenerateParams.NoiseType noise : GenerateParams.NoiseType.values()) {
                        GenerateParams p = variant(seed, style, naming, noise);
                        assertThat(Generator.build(p, null).files()).as("%s", p)
                                .isEqualTo(Generator.build(p, null).files());
                    }
                }
            }
        }
    }

    /** changing --noise-type changes only the run id, description.md, facts.jsonl, decoys.jsonl and manifest.json; B and the full network stay byte-identical. */
    @Test
    void noiseTypeChangesOnlyTheDescriptionLedgerDecoysAndManifest() {
        for (GenerateParams.Style style : GenerateParams.Style.values()) {
            Map<String, String> quiet = Generator.build(variant(4, style, GenerateParams.Naming.TYPED,
                    GenerateParams.NoiseType.NONE), null).files();
            for (GenerateParams.NoiseType noise : List.of(GenerateParams.NoiseType.BOILERPLATE,
                    GenerateParams.NoiseType.TOPICAL, GenerateParams.NoiseType.ADVERSARIAL)) {
                Map<String, String> noisy = Generator.build(variant(4, style, GenerateParams.Naming.TYPED, noise),
                        null).files();
                for (String file : quiet.keySet()) {
                    if (List.of(Generator.DESCRIPTION, Generator.FACTS, Generator.DECOYS, Generator.MANIFEST)
                            .contains(file)) {
                        continue;
                    }
                    assertThat(noisy.get(file)).as("%s with %s noise", file, noise).isEqualTo(quiet.get(file));
                }
                assertThat(noisy.get(Generator.DESCRIPTION)).isNotEqualTo(quiet.get(Generator.DESCRIPTION));
            }
        }
    }

    @Test
    void changingTheSeedChangesEverySeedDependentFile(@TempDir Path tmp) throws IOException {
        Map<String, String> a = hashes(generate(tmp.resolve("a"), 1));
        Map<String, String> b = hashes(generate(tmp.resolve("b"), 2));
        // network_meta.csv states only parameters, and decoys.jsonl is empty without adversarial noise
        List<String> seedIndependent = List.of("truth/network_meta.csv", "decoys.jsonl");
        for (String file : a.keySet()) {
            if (seedIndependent.contains(file)) {
                assertThat(b.get(file)).as(file).isEqualTo(a.get(file));
            } else {
                assertThat(b.get(file)).as(file).isNotEqualTo(a.get(file));
            }
        }
    }

    @Test
    void defaultRunIdHasReadablePrefixAndParameterHash() {
        GenerateParams p = GenerateParams.defaults(42, 25, 5, "Widget");
        assertThat(p.defaultRunId()).matches("s42-n25-e5-template-typed-uniform-none0-[0-9a-f]{8}");
        GenerateParams other = new GenerateParams(42, 25, 5, "Widget", 1, p.naming(), p.style(),
                GenerateParams.Order.SHUFFLED, p.units(), p.periodUnit(), 1, 90, 1, 2, 0, p.attributeCoverage(),
                p.noiseType(), p.noiseRatio(), false, p.distanceMode(), p.locale());
        assertThat(other.defaultRunId()).isNotEqualTo(p.defaultRunId())
                .startsWith("s42-n25-e5-template-typed-uniform-none0-");
    }

    /** at file level, per entity: B's rows are ordered by name, so another naming scheme reorders them; but every row, keyed by its (renamed) entity, keeps every non-name cell. */
    @Test
    void namingChangesOnlyNameCellsOfEveryWrittenRow() {
        for (GenerateParams.Naming naming : List.of(GenerateParams.Naming.NEUTRAL, GenerateParams.Naming.REALISTIC)) {
            GenerateParams typed = variant(6, GenerateParams.Style.TEMPLATE, GenerateParams.Naming.TYPED,
                    GenerateParams.NoiseType.NONE);
            GenerateParams other = variant(6, GenerateParams.Style.TEMPLATE, naming, GenerateParams.NoiseType.NONE);
            Generator.Generated a = Generator.build(typed, null);
            Generator.Generated b = Generator.build(other, null);
            Map<String, String> rename = new TreeMap<>();
            for (int i = 0; i < a.full().nodes().size(); i++) {
                rename.put(a.full().nodes().get(i).name(), b.full().nodes().get(i).name());
            }
            for (org.snrmbench.netbench.schema.Sheet sheet : org.snrmbench.netbench.schema.Sheet.values()) {
                Map<List<String>, List<String>> rowsA = rows(a, sheet, rename);
                Map<List<String>, List<String>> rowsB = rows(b, sheet, Map.of());
                assertThat(rowsB).as("%s under %s naming", sheet.fileName(), naming).isEqualTo(rowsA);
            }
        }
    }

    /** A sheet's rows keyed by their node-name cells (mapped through {@code rename}); values = the other cells. */
    private static Map<List<String>, List<String>> rows(Generator.Generated g, org.snrmbench.netbench.schema.Sheet sheet,
                                                         Map<String, String> rename) {
        var table = org.snrmbench.netbench.csv.LenientCsvReader.readTable(sheet, sheet.fileName(),
                g.files().get("truth/" + sheet.fileName()));
        List<org.snrmbench.netbench.schema.Column> nameColumns = List.of(org.snrmbench.netbench.schema.Schema.NODE_NAME,
                org.snrmbench.netbench.schema.Schema.LINK_SOURCE, org.snrmbench.netbench.schema.Schema.LINK_TARGET,
                org.snrmbench.netbench.schema.Schema.NP_NODE, org.snrmbench.netbench.schema.Schema.NP_PRODUCT,
                org.snrmbench.netbench.schema.Schema.PRODUCT_NAME);
        Map<List<String>, List<String>> out = new TreeMap<>(java.util.Comparator.comparing(List::toString));
        for (var row : table.rows()) {
            List<String> key = new java.util.ArrayList<>();
            List<String> rest = new java.util.ArrayList<>();
            table.columnIndex().forEach((column, i) -> {
                String cell = row.cells().get(i);
                if (nameColumns.contains(column)) {
                    key.add(rename.getOrDefault(cell, cell));
                } else {
                    rest.add(cell);
                }
            });
            out.put(key, rest);
        }
        return out;
    }

    @Test
    void namingChangesNamesAndNothingElse() {
        GenerateParams typed = GenerateParams.defaults(5, 30, 5, "Widget");
        GenerateParams neutral = new GenerateParams(5, 30, 5, "Widget", 1, GenerateParams.Naming.NEUTRAL,
                typed.style(), typed.order(), typed.units(), typed.periodUnit(), 1, 90, 1, 2, 0,
                typed.attributeCoverage(), typed.noiseType(), typed.noiseRatio(), false, typed.distanceMode(),
                typed.locale());
        Generator.Generated a = Generator.build(typed, null);
        Generator.Generated b = Generator.build(neutral, null);
        Map<String, String> rename = new TreeMap<>();
        for (int i = 0; i < a.full().nodes().size(); i++) {
            rename.put(a.full().nodes().get(i).name(), b.full().nodes().get(i).name());
        }
        // every node attribute is identical once names are mapped across
        for (int i = 0; i < a.full().nodes().size(); i++) {
            assertThat(b.full().nodes().get(i))
                    .isEqualTo(a.full().nodes().get(i).withName(rename.get(a.full().nodes().get(i).name())));
        }
        for (int i = 0; i < a.full().links().size(); i++) {
            var la = a.full().links().get(i);
            var lb = b.full().links().get(i);
            assertThat(lb.source()).isEqualTo(rename.get(la.source()));
            assertThat(lb.target()).isEqualTo(rename.get(la.target()));
            assertThat(List.of(lb.leadTime(), lb.capacity(), lb.unitCost(), lb.failureProb()))
                    .isEqualTo(List.of(la.leadTime(), la.capacity(), la.unitCost(), la.failureProb()));
        }
    }
}
