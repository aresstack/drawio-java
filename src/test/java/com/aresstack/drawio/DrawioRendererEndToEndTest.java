package com.aresstack.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;

/**
 * Drive the production path through the public facade:
 * parser, original mxGraph pipeline (#9), shared Java2D/SVG output (#11).
 */
class DrawioRendererEndToEndTest {

    private static DrawioRenderer renderer;
    private static String gateways;

    @BeforeAll
    static void setUp() throws IOException {
        renderer = DrawioRenderer.builder().maxConcurrentRenders(2).build();
        gateways = new String(Files.readAllBytes(Paths.get(
                "src/test/resources/drawio/golden/bpmn-gateway-fixture.drawio")),
                StandardCharsets.UTF_8);
    }

    @AfterAll
    static void tearDown() {
        renderer.close();
    }

    /**
     * The production BPMN shape bundle comes from #10. Until it is on the
     * classpath the pipeline correctly reports gateway2 as unsupported, so the
     * gateway tests are skipped rather than weakened; they run automatically
     * once the bundle exists.
     */
    private static void assumeProductionShapeBundle() {
        assumeTrue(DrawioRendererEndToEndTest.class.getResource("/drawio/drawio-shape-library.js") != null,
                "production shape bundle (#10) not on the classpath yet");
    }

    @Test
    void rendersOriginalBpmnGatewaysAsImagePngAndSvg() throws RenderException {
        assumeProductionShapeBundle();
        RenderResult<BufferedImage> image = renderer.renderImage(gateways,
                RenderOptions.builder().background("#ffffff").build());
        assertTrue(image.isComplete());
        assertTrue(image.getWarnings().isEmpty(), image.getWarnings().toString());
        assertTrue(image.getWidth() >= 280 && image.getHeight() >= 80,
                image.getWidth() + "x" + image.getHeight());
        assertTrue(countDarkPixels(image.getOutput()) > 500, "gateways must be visible");

        RenderResult<byte[]> png = renderer.renderPng(gateways);
        assertEquals(image.getWidth(), png.getWidth());
        assertEquals((byte) 0x89, png.getOutput()[0]);

        RenderResult<String> svg = renderer.renderSvg(gateways);
        assertTrue(svg.getOutput().contains("<svg"));
        assertEquals(image.getWidth(), svg.getWidth());
    }

    @Test
    void rendersBasicShapesEdgeAndLabelThroughTheProductionPipeline() throws RenderException {
        String basic = "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"a\" value=\"Start\" style=\"rounded=1;whiteSpace=wrap;html=1;\""
                + " vertex=\"1\" parent=\"1\"><mxGeometry x=\"20\" y=\"20\" width=\"120\""
                + " height=\"60\" as=\"geometry\"/></mxCell>"
                + "<mxCell id=\"b\" value=\"Ende\" style=\"ellipse;whiteSpace=wrap;html=1;\""
                + " vertex=\"1\" parent=\"1\"><mxGeometry x=\"240\" y=\"20\" width=\"80\""
                + " height=\"60\" as=\"geometry\"/></mxCell>"
                + "<mxCell id=\"e\" style=\"edgeStyle=orthogonalEdgeStyle;\" edge=\"1\""
                + " parent=\"1\" source=\"a\" target=\"b\"><mxGeometry relative=\"1\""
                + " as=\"geometry\"/></mxCell></root></mxGraphModel>";
        RenderResult<BufferedImage> image = renderer.renderImage(basic,
                RenderOptions.builder().background("#ffffff").scale(2).build());
        assertTrue(image.isComplete(), image.getWarnings().toString());
        assertTrue(image.getWidth() >= 600, "width " + image.getWidth());
        assertTrue(countDarkPixels(image.getOutput()) > 300, "shapes, edge and text must be visible");
        RenderResult<String> svg = renderer.renderSvg(basic);
        assertTrue(svg.getOutput().contains("<svg"));
    }

    @Test
    void unknownStencilFailsByDefaultAndWarnsOnRequest() throws RenderException {
        String unknown = "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"a\" value=\"\" style=\"shape=mxgraph.doesnotexist.thing;\" vertex=\"1\""
                + " parent=\"1\"><mxGeometry x=\"10\" y=\"10\" width=\"80\" height=\"40\""
                + " as=\"geometry\"/></mxCell>"
                + "<mxCell id=\"b\" value=\"\" style=\"rounded=0;\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"120\" y=\"10\" width=\"80\" height=\"40\" as=\"geometry\"/>"
                + "</mxCell></root></mxGraphModel>";
        RenderException failure = assertThrows(RenderException.class,
                () -> renderer.renderPng(unknown));
        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());

        RenderResult<byte[]> partial = renderer.renderPng(unknown, RenderOptions.builder()
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN).build());
        assertFalse(partial.isComplete());
        assertFalse(partial.getWarnings().isEmpty());
    }

    @Test
    void parallelRealRendersAreIdentical() throws Exception {
        assumeProductionShapeBundle();
        final RenderOptions options = RenderOptions.builder().background("#ffffff").build();
        int[] reference = pixels(renderer.renderImage(gateways, options).getOutput());
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Future<int[]>> results = new ArrayList<Future<int[]>>();
            for (int i = 0; i < 12; i++) {
                results.add(callers.submit(() -> pixels(renderer.renderImage(gateways, options)
                        .getOutput())));
            }
            for (Future<int[]> result : results) {
                int[] actual = result.get(60, TimeUnit.SECONDS);
                assertEquals(reference.length, actual.length);
                for (int i = 0; i < actual.length; i++) {
                    if (actual[i] != reference[i]) {
                        assertNotEquals(i, i, "pixel " + i + " differs between parallel renders");
                    }
                }
            }
        } finally {
            callers.shutdownNow();
        }
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static int countDarkPixels(BufferedImage image) {
        int dark = 0;
        for (int rgb : pixels(image)) {
            int r = (rgb >> 16) & 0xff;
            int g = (rgb >> 8) & 0xff;
            int b = rgb & 0xff;
            if (((rgb >>> 24) & 0xff) > 128 && r + g + b < 200) {
                dark++;
            }
        }
        return dark;
    }
}
