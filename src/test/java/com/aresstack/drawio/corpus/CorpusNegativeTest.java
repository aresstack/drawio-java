package com.aresstack.drawio.corpus;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.DiagramFormatException;
import com.aresstack.drawio.api.DrawioDocument;
import com.aresstack.drawio.api.PageSelector;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.parse.DrawioDocumentParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Hostile and invalid inputs from {@code corpus/negative} (#13): XML attacks,
 * decompression bombs, broken compression, ambiguous page selection, unknown
 * stencils, dangling references and active HTML content.
 *
 * <p>Parse-stage cases are enforced now, both in the parser and through the
 * public {@link DrawioRenderer}. Render-stage cases marked
 * {@code render="pending"} are recorded in
 * {@code build/reports/corpus/negative.html} until the graph pipeline and
 * shape coverage land (#9/#10). Expectations: a {@code RenderErrorCode} name,
 * {@code SAFE} (must not crash, execute or fetch; may render) or {@code WARN}
 * (must render and report at least one structured warning).</p>
 */
class CorpusNegativeTest {

    private static final CorpusManifest MANIFEST = CorpusManifest.load();
    private static final CorpusReport REPORT = new CorpusReport("drawio-java corpus: negative cases", "negative");
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

    @TestFactory
    List<DynamicTest> rejectsOrNeutralisesHostileInput() {
        List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final CorpusManifest.Negative negative : MANIFEST.negatives) {
            tests.add(DynamicTest.dynamicTest(negative.id + " -> " + negative.expect, () -> verify(negative)));
        }
        return tests;
    }

    private void verify(CorpusManifest.Negative negative) throws Exception {
        String xml = negative.read();
        if ("parse".equals(negative.stage)) {
            DiagramFormatException.Reason expected = DiagramFormatException.Reason.valueOf(negative.expect);
            DiagramFormatException parseError = assertThrows(DiagramFormatException.class,
                    () -> new DrawioDocumentParser().parse(xml));
            assertEquals(expected, parseError.getReason(), "parser reason");
            assertNoInputEcho(parseError.getMessage(), xml);

            RenderException renderError = assertThrows(RenderException.class,
                    () -> renderer.renderImage(xml, RenderOptions.defaults()));
            assertEquals(expected, renderError.getFormatReason(), "renderer must surface the parser reason");
            REPORT.add(row(negative, "PASS", renderError.getCode() + " / " + expected));
            return;
        }
        if (negative.stage.startsWith("select:")) {
            String pageName = negative.stage.substring("select:".length());
            DiagramFormatException.Reason expected = DiagramFormatException.Reason.valueOf(negative.expect);
            DrawioDocument document = new DrawioDocumentParser().parse(xml);
            DiagramFormatException selectError = assertThrows(DiagramFormatException.class,
                    () -> document.pageByName(pageName));
            assertEquals(expected, selectError.getReason());
            RenderException renderError = assertThrows(RenderException.class, () -> renderer.renderImage(xml,
                    RenderOptions.builder().page(PageSelector.name(pageName)).build()));
            assertEquals(expected, renderError.getFormatReason());
            REPORT.add(row(negative, "PASS", renderError.getCode() + " / " + expected));
            return;
        }
        verifyRenderStage(negative, xml);
    }

    private void verifyRenderStage(CorpusManifest.Negative negative, String xml) {
        boolean enforced = negative.render == CorpusManifest.RenderStatus.MATCH;
        RenderOptions strict = RenderOptions.builder().resourcePolicy(ResourcePolicy.dataUrisOnly()).build();
        String observed;
        boolean ok;
        try {
            RenderResult<BufferedImage> result = renderer.renderImage(xml, strict);
            observed = "rendered " + result.getWidth() + "x" + result.getHeight()
                    + (result.isComplete() ? "" : ", incomplete") + ", warnings " + result.getWarnings();
            ok = "SAFE".equals(negative.expect)
                    || ("WARN".equals(negative.expect) && !result.getWarnings().isEmpty());
        } catch (RenderException exception) {
            observed = exception.getCode() + ": " + exception.getMessage();
            ok = "SAFE".equals(negative.expect) || exception.getCode().name().equals(negative.expect);
            assertNoInputEcho(exception.getMessage(), xml);
        } catch (RuntimeException exception) {
            observed = "unexpected " + exception.getClass().getName() + ": " + exception.getMessage();
            ok = false;
            if (enforced) {
                throw exception;
            }
        }
        String outcome = ok ? (enforced ? "PASS" : "PASS - ready to promote") : (enforced ? "FAIL" : "pending");
        REPORT.add(row(negative, outcome, observed));
        if (enforced && !ok) {
            fail(negative.id + ": expected " + negative.expect + " but observed " + observed);
        }
    }

    private static CorpusReport.Row row(CorpusManifest.Negative negative, String outcome, String detail) {
        String status = negative.stage + " expects " + negative.expect
                + (negative.render == CorpusManifest.RenderStatus.PENDING
                ? " (" + negative.render.name().toLowerCase(Locale.ROOT) + " " + negative.issue + ")" : "");
        return new CorpusReport.Row(negative.id, status, outcome, detail, null, null, null);
    }

    /** Error messages must not leak diagram content (it can be confidential). */
    private static void assertNoInputEcho(String message, String xml) {
        if (message == null) {
            return;
        }
        for (String marker : new String[] {"Grüße", "einzig", "unbekannt", "Bombe", "kaputt", "file:///etc"}) {
            if (xml.contains(marker) && message.contains(marker)) {
                fail("error message echoes diagram content: " + message);
            }
        }
    }
}
