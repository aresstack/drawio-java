package com.aresstack.drawio.corpus;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.PageSelector;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.RenderWarning;
import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Golden-image comparison of every corpus page, rendered through the public
 * {@link DrawioRenderer} facade, against the official draw.io viewer
 * reference of the pinned upstream commit (#13).
 *
 * <p>Fixtures marked {@code render="match"} in {@code corpus.xml} must render
 * and reach their {@code minScore}; a regression fails the build. Fixtures
 * marked {@code render="pending"} are rendered and scored for the report in
 * {@code build/reports/corpus/render.html} but never fail; the report flags
 * them "ready to promote" once they pass, and promoting them (editing
 * {@code corpus.xml}) turns the result into a regression gate.</p>
 */
class CorpusRenderTest {

    private static final int BOUNDS_SLACK = 6;

    private static final CorpusManifest MANIFEST = CorpusManifest.load();
    private static final CorpusReport REPORT = new CorpusReport("drawio-java corpus: visual comparison", "render");
    private static DrawioRenderer renderer;

    @BeforeAll
    static void createRenderer() {
        renderer = DrawioRenderer.create();
    }

    @AfterAll
    static void closeRendererAndWriteReport() {
        if (renderer != null) {
            renderer.close();
        }
        REPORT.write();
    }

    static RenderOptions visualOptions(int pageIndex) {
        return RenderOptions.builder()
                .page(PageSelector.index(pageIndex))
                .scale(1.0)
                .padding(0)
                .background("#ffffff")
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN)
                .resourcePolicy(ResourcePolicy.dataUrisOnly())
                .build();
    }

    @TestFactory
    List<DynamicTest> rendersLikeTheOfficialViewer() {
        List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final CorpusManifest.Page page : MANIFEST.pages()) {
            tests.add(DynamicTest.dynamicTest(page.displayName(), () -> verify(page)));
        }
        return tests;
    }

    private void verify(CorpusManifest.Page page) throws Exception {
        CorpusManifest.Fixture fixture = page.fixture;
        boolean enforced = fixture.render == CorpusManifest.RenderStatus.MATCH;
        String status = fixture.render.name().toLowerCase(Locale.ROOT)
                + (fixture.issue.isEmpty() ? "" : " (" + fixture.issue + ")");
        String name = page.displayName();
        String reference = CorpusReport.copyReference(page.png);

        RenderResult<BufferedImage> result;
        try {
            result = renderer.renderImage(fixture.read(), visualOptions(page.index));
        } catch (RenderException exception) {
            String detail = exception.getCode() + (exception.getCapabilityId() == null ? "" : " ["
                    + exception.getCapabilityId() + "]") + ": " + exception.getMessage();
            REPORT.add(new CorpusReport.Row(name, status, enforced ? "FAIL (render error)" : "not rendered",
                    detail, null, null, reference));
            if (enforced) {
                fail(name + " must render (render=\"match\") but failed with " + detail);
            }
            return;
        }

        List<String> missingLabels = missingLabels(page, renderer.renderSvg(fixture.read(),
                visualOptions(page.index)).getOutput());
        BufferedImage expected = ImageIO.read(new ByteArrayInputStream(CorpusManifest.readBytes(page.png)));
        ImageComparison comparison = ImageComparison.compare(result.getOutput(), expected,
                ImageComparison.DEFAULT_TOLERANCE);
        boolean passed = comparison.score >= page.minScore && comparison.boundsAgree(BOUNDS_SLACK)
                && missingLabels.isEmpty();
        StringBuilder detail = new StringBuilder(comparison.summary())
                .append(String.format(Locale.ROOT, ", minScore %.2f", page.minScore))
                .append(", SVG labels ").append(page.labels.size() - missingLabels.size()).append('/')
                .append(page.labels.size());
        if (!missingLabels.isEmpty()) {
            detail.append(" missing ").append(missingLabels);
        }
        if (!result.getWarnings().isEmpty()) {
            detail.append(", warnings:");
            for (RenderWarning warning : result.getWarnings()) {
                detail.append(' ').append(warning.getCapabilityId());
            }
        }
        String outcome = passed ? (enforced ? "PASS" : "PASS - ready to promote to render=\"match\"")
                : (enforced ? "FAIL" : "below threshold");
        REPORT.add(new CorpusReport.Row(name, status, outcome, detail.toString(),
                CorpusReport.writeImage(name + "-rendered", result.getOutput()),
                CorpusReport.writeImage(name + "-diff", comparison.diff), reference));
        if (enforced) {
            assertTrue(passed, name + ": " + detail + " (see build/reports/corpus/render.html)");
        }
    }

    /**
     * Labels from the manifest that do not appear as text in the SVG output.
     * Text nodes are concatenated and compared without whitespace, so labels
     * wrapped over several lines still match.
     */
    static List<String> missingLabels(CorpusManifest.Page page, String svg) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setNamespaceAware(true);
        org.w3c.dom.Document document = factory.newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(svg)));
        org.w3c.dom.NodeList texts = document.getElementsByTagNameNS("*", "text");
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < texts.getLength(); i++) {
            all.append(texts.item(i).getTextContent());
        }
        String haystack = all.toString().replaceAll("\\s+", "");
        List<String> missing = new ArrayList<String>();
        for (CorpusManifest.Label label : page.labels) {
            if (!haystack.contains(label.text.replaceAll("\\s+", ""))) {
                missing.add(label.text);
            }
        }
        return missing;
    }
}
