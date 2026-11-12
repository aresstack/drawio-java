package com.aresstack.drawio.graph;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.render.RenderSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inline {@code shape=stencil(...)} definitions through the production pipeline.
 */
class InlineStencilPipelineTest {

    private static final String TRIANGLE = "<shape name=\"t\" w=\"10\" h=\"10\" aspect=\"variable\">"
            + "<foreground><path><move x=\"0\" y=\"10\"/><line x=\"5\" y=\"0\"/><line x=\"10\" y=\"10\"/>"
            + "<close/></path><fillstroke/></foreground></shape>";

    @Test
    void paintsInlineStencilDefinitions() throws Exception {
        RenderSession session = new RenderSession(RenderOptions.defaults());

        String all = String.join("\n", OriginalGraphPipelineTest.record(new OriginalGraphPipeline()
                .prepare(page(cell("stencil(" + compress(TRIANGLE) + ")")), session)));

        // Original mxStencil scales the 10x10 definition to the 40x20 geometry.
        assertTrue(all.contains("moveTo[10.0, 30.0]"), all);
        assertTrue(all.contains("lineTo[30.0, 10.0]"), all);
        assertTrue(all.contains("lineTo[50.0, 30.0]"), all);
        assertTrue(session.warnings().isEmpty(), String.valueOf(session.warnings()));
    }

    @Test
    void reportsUndecodableInlineStencils() {
        RenderException failure = assertThrows(RenderException.class, () -> new OriginalGraphPipeline()
                .prepare(page(cell("stencil(bm90IGRlZmxhdGU=)")), new RenderSession(RenderOptions.defaults())));

        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());
        assertEquals("stencil:inline", failure.getCapabilityId());
    }

    @Test
    void rejectsDoctypesInsideInlineStencils() throws Exception {
        String xml = "<!DOCTYPE shape [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" + TRIANGLE;

        RenderException failure = assertThrows(RenderException.class, () -> new OriginalGraphPipeline()
                .prepare(page(cell("stencil(" + compress(xml) + ")")), new RenderSession(RenderOptions.defaults())));

        assertEquals("stencil:inline", failure.getCapabilityId());
    }

    private static String cell(String shape) {
        return "<mxCell id=\"s\" value=\"\" style=\"shape=" + shape + ";\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"40\" height=\"20\" as=\"geometry\"/></mxCell>";
    }

    private static DiagramPage page(String cell) {
        return new DiagramPage(0, "p", "Page", "<mxGraphModel><root><mxCell id=\"0\"/>"
                + "<mxCell id=\"1\" parent=\"0\"/>" + cell + "</root></mxGraphModel>");
    }

    /** Graph.compress: raw DEFLATE of encodeURIComponent(xml), base64. */
    private static String compress(String xml) throws Exception {
        String uri = URLEncoder.encode(xml, "UTF-8").replace("+", "%20");
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(uri.getBytes(StandardCharsets.US_ASCII));
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } finally {
            deflater.end();
        }
    }
}
