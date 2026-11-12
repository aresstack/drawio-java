package com.aresstack.drawio.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The corpus is exactly what the manifest says: every file is listed and
 * checksummed, provenance and license are documented, and nothing that looks
 * like business, authentication or personal data slipped in (#13).
 */
class CorpusIntegrityTest {

    private static final Set<String> NON_FIXTURE_FILES = new HashSet<String>();

    static {
        NON_FIXTURE_FILES.add("corpus.xml");
        NON_FIXTURE_FILES.add("README.md");
    }

    // Conservative markers of material that must never be checked in.
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)(confluence|atlassian\\.net|jsessionid|bearer\\s+[a-z0-9]|password\\s*=|"
                    + "api[_-]?key|BEGIN [A-Z ]*PRIVATE KEY|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.(de|com|org|net))");

    private final CorpusManifest manifest = CorpusManifest.load();

    @Test
    void documentsProvenanceVersionAndLicense() {
        for (String key : new String[] {"license", "origin", "upstreamRepository", "upstreamCommit",
                "referenceViewer", "referenceViewerSha256Lf", "referenceViewerSha256Crlf",
                "referenceGenerator", "referenceBrowser", "referenceCreated"}) {
            String value = manifest.provenance.get(key);
            assertTrue(value != null && !value.trim().isEmpty(), "corpus.xml lacks provenance attribute " + key);
        }
        assertEquals("c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3", manifest.provenance.get("upstreamCommit"),
                "references must come from the same upstream commit as the bundled draw.io sources");
        assertFalse(manifest.fixtures.isEmpty());
        assertFalse(manifest.negatives.isEmpty());
    }

    @Test
    void everyListedFileMatchesItsChecksum() throws IOException {
        List<String> mismatches = new ArrayList<String>();
        for (CorpusManifest.Fixture fixture : manifest.fixtures) {
            check(fixture.file, fixture.sha256, mismatches);
            for (CorpusManifest.Page page : fixture.pages) {
                check(page.png, page.pngSha256, mismatches);
                check(page.svg, page.svgSha256, mismatches);
            }
        }
        for (CorpusManifest.Negative negative : manifest.negatives) {
            check(negative.file, negative.sha256, mismatches);
        }
        assertTrue(mismatches.isEmpty(), "Checksum mismatches (update corpus.xml only after reviewing "
                + "the change and regenerating references):\n  " + String.join("\n  ", mismatches));
    }

    @Test
    void everyFileInTheCorpusDirectoryIsListed() throws IOException {
        Path directory = CorpusManifest.directory();
        Set<String> listed = new HashSet<String>(NON_FIXTURE_FILES);
        for (CorpusManifest.Fixture fixture : manifest.fixtures) {
            listed.add(fixture.file);
            for (CorpusManifest.Page page : fixture.pages) {
                listed.add(page.png);
                listed.add(page.svg);
            }
        }
        for (CorpusManifest.Negative negative : manifest.negatives) {
            listed.add(negative.file);
        }
        List<String> unlisted = new ArrayList<String>();
        for (Path file : CorpusManifest.listFiles(directory)) {
            String relative = directory.relativize(file).toString().replace('\\', '/');
            if (!listed.contains(relative)) {
                unlisted.add(relative);
            }
        }
        assertTrue(unlisted.isEmpty(), "Files in the corpus directory without a manifest entry "
                + "(provenance/checksum missing): " + unlisted);
    }

    @Test
    void containsNoConfidentialLookingContent() throws IOException {
        Path directory = CorpusManifest.directory();
        List<String> hits = new ArrayList<String>();
        for (Path file : CorpusManifest.listFiles(directory)) {
            String name = file.getFileName().toString();
            if (name.endsWith(".png") || NON_FIXTURE_FILES.contains(name)) {
                continue;
            }
            String text = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = FORBIDDEN.matcher(text);
            if (matcher.find()) {
                hits.add(directory.relativize(file) + ": '" + matcher.group() + "'");
            }
        }
        assertTrue(hits.isEmpty(), "Possible confidential content in corpus: " + hits);
    }

    private static void check(String relativePath, String expected, List<String> mismatches) throws IOException {
        String actual = CorpusManifest.sha256(CorpusManifest.readBytes(relativePath));
        if (!actual.equals(expected)) {
            mismatches.add(relativePath + " expected " + expected + " actual " + actual);
        }
    }
}
