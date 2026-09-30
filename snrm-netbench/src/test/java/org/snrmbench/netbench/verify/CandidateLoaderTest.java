package org.snrmbench.netbench.verify;

import org.snrmbench.netbench.csv.LenientCsvReader;
import org.snrmbench.netbench.schema.Sheet;
import org.snrmbench.netbench.validate.Code;
import org.snrmbench.netbench.validate.Finding;
import org.snrmbench.netbench.validate.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.snrmbench.netbench.verify.CsvEdits.sample;
import static org.snrmbench.netbench.verify.CsvEdits.without;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** /: the envelope findings of a loaded candidate. */
class CandidateLoaderTest {

    private static final Map<String, String> FILES = sample("minimal-6-node");

    private static String response(Map<String, String> files) {
        StringBuilder sb = new StringBuilder("Here is the network.\n\n");
        files.forEach((name, content) -> sb.append("### ").append(name).append("\n```csv\n")
                .append(content.replace("\r\n", "\n")).append("```\n\n"));
        return sb.toString();
    }

    @Test
    void missingFilesAreErrorsForNodesAndLinksAndWarningsOtherwise() {
        Candidate c = CandidateLoader.fromFiles(without(without(FILES, Sheet.NODES), Sheet.PRODUCTS),
                CandidateLoader.SOURCE_FILE);

        assertThat(c.filesFound()).containsExactly("network_meta.csv", "links.csv", "node_products.csv");
        assertThat(c.filesMissing()).containsExactly("nodes.csv", "products.csv");
        assertThat(c.envelopeFindings())
                .extracting(Finding::code, Finding::severity, Finding::file)
                .containsExactly(tuple(Code.FILE_MISSING, Severity.ERROR, "nodes.csv"),
                        tuple(Code.FILE_MISSING, Severity.WARNING, "products.csv"));
    }

    @Test
    void missingNetworkMetaIsDefaulted() {
        Candidate c = CandidateLoader.fromFiles(without(FILES, Sheet.NETWORK_META), CandidateLoader.SOURCE_DIRECTORY);

        assertThat(c.envelopeFindings())
                .extracting(Finding::code, Finding::severity)
                .containsExactly(tuple(Code.FILE_MISSING, Severity.WARNING),
                        tuple(Code.NETWORK_META_DEFAULTED, Severity.WARNING));
    }

    @Test
    void aSheetGivenTwiceKeepsTheFirst() {
        Map<String, String> files = new LinkedHashMap<>(FILES);
        files.put("NODES.CSV", "name,type\nX,DC\n");
        Candidate c = CandidateLoader.fromFiles(files, CandidateLoader.SOURCE_DIRECTORY);

        assertThat(c.envelopeFindings()).extracting(Finding::code).containsExactly(Code.ENVELOPE_PARSE_ERROR);
        assertThat(c.network().table(Sheet.NODES).orElseThrow().rows()).hasSize(6);
    }

    @Test
    void aFileThatIsNotCsvIsAParseErrorAndTheRestStillLoads() {
        Map<String, String> files = new LinkedHashMap<>(FILES);
        files.put("nodes.csv", "name,type\n\"SUP-1,SUPPLIER\n");
        Candidate c = CandidateLoader.fromFiles(files, CandidateLoader.SOURCE_FILE);

        assertThat(c.envelopeFindings()).singleElement().satisfies(f -> {
            assertThat(f.code()).isEqualTo(Code.ENVELOPE_PARSE_ERROR);
            assertThat(f.message()).startsWith("nodes.csv");
        });
        assertThat(c.filesFound()).contains("nodes.csv");
        assertThat(c.network().has(Sheet.NODES)).isFalse();
        assertThat(c.network().has(Sheet.LINKS)).isTrue();
    }

    @Test
    void aResponseFileIsSplitIntoTheSplitDirectory(@TempDir Path dir) throws Exception {
        Path response = dir.resolve("response.md");
        Files.writeString(response, response(without(FILES, Sheet.PRODUCTS)), StandardCharsets.UTF_8);
        Path stale = dir.resolve("response.md.split").resolve("products.csv");
        Files.createDirectories(stale.getParent());
        Files.writeString(stale, "left over from an earlier run", StandardCharsets.UTF_8);

        Candidate c = CandidateLoader.load(response, null);

        assertThat(c.source()).isEqualTo(CandidateLoader.SOURCE_FILE);
        assertThat(c.filesMissing()).containsExactly("products.csv");
        assertThat(dir.resolve("response.md.split").resolve("nodes.csv")).exists();
        assertThat(stale).doesNotExist();

        Path explicit = dir.resolve("elsewhere");
        CandidateLoader.load(response, explicit);
        assertThat(Files.readString(explicit.resolve("links.csv"), StandardCharsets.UTF_8))
                .isEqualTo(FILES.get("links.csv").replace("\r\n", "\n"));
    }

    @Test
    void anUnparsableResponseStillYieldsAReport(@TempDir Path dir) throws Exception {
        Path response = dir.resolve("response.txt");
        Files.writeString(response, "I cannot help with that.", StandardCharsets.UTF_8);
        Candidate c = CandidateLoader.load(response, null);
        Report r = Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(FILES), c, null, null, null,
                new BigDecimal("1e-6"), "truth", response.toString()));

        assertThat(c.envelopeFindings()).extracting(Finding::code).startsWith(Code.ENVELOPE_PARSE_ERROR)
                .contains(Code.FILE_MISSING, Code.NETWORK_META_DEFAULTED);
        assertThat(r.importValid()).isFalse();
        assertThat(r.structureF1()).isZero();
        assertThat(r.factRecall()).isZero();
        assertThat(r.envelope().filesFound()).isEmpty();
    }

    @Test
    void aStrayCsvInADirectoryIsAnUnrecognisedSheet(@TempDir Path dir) throws Exception {
        for (Map.Entry<String, String> e : FILES.entrySet()) {
            Files.writeString(dir.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
        Files.writeString(dir.resolve("assumptions.csv"), "note\nnone\n", StandardCharsets.UTF_8);
        Candidate c = CandidateLoader.load(dir, null);
        Report r = Verifier.verify(new Verifier.Inputs(LenientCsvReader.read(FILES), c, null, null, null,
                new BigDecimal("1e-6"), "truth", dir.toString()));

        assertThat(r.l0().findings()).extracting(Finding::code).containsExactly(Code.UNRECOGNISED_SHEET);
        assertThat(r.importValid()).isTrue();
        assertThat(r.l2().mismatches()).isEmpty();
    }
}
