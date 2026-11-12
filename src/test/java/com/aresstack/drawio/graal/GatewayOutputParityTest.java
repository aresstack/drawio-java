package com.aresstack.drawio.graal;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;

import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.aresstack.drawio.render.BoundsMode;
import com.aresstack.drawio.render.CanvasRecorder;
import com.aresstack.drawio.render.CanvasRecording;
import com.aresstack.drawio.render.OutputGeometry;
import com.aresstack.drawio.render.OutputOptions;
import com.aresstack.drawio.render.SceneRenderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Render the original Draw.io BPMN gateways once through GraalJS and write
 * them as BufferedImage, PNG and SVG from the same recorded canvas commands.
 *
 * <p>Both formats are compared against the pinned official Draw.io viewer
 * reference used by {@link BpmnGatewayVisualReferenceTest} and against each
 * other.
 */
class GatewayOutputParityTest {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 100;
    private static final int ALLOWED_STABLE_PIXEL_DIFFERENCES = 30;
    private static final String OFFICIAL_SVG = "/drawio/golden/bpmn-gateway-reference.svg";

    private static CanvasRecording gateways;

    private final SceneRenderer renderer = new SceneRenderer();
    private final OutputOptions referenceFrame = OutputOptions.builder()
            .boundsMode(BoundsMode.LOGICAL)
            .antialiasing(false)
            .build();

    @BeforeAll
    static void runOriginalJavaScriptOnce() {
        ClasspathScriptLoader scripts = new ClasspathScriptLoader();
        CanvasRecorder recorder = new CanvasRecorder();
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
            DrawioHost host = new DrawioHost(resourceId -> {
                throw new IllegalArgumentException("External resources are disabled");
            }, new DrawioCanvasBridge(recorder.canvas()));
            String source = scripts.load("test-bootstrap.js") + "\n"
                    + scripts.load("drawio-shapes.js") + "\n"
                    + scripts.load("drawio-canvas-adapter.js") + "\n"
                    + gatewayCalls();
            ScriptExecutionResult result = runtime.execute("gateway-output.js", source, host);
            assertTrue(result.isSuccessful(), result.getErrorMessage());
        }
        gateways = recorder.toScene(new Rectangle2D.Double(0, 0, WIDTH, HEIGHT));
        assertTrue(gateways.size() > 20, "JavaScript must have issued canvas commands");
    }

    @Test
    void bufferedImageMatchesOfficialViewer() throws Exception {
        BufferedImage image = renderer.renderImage(gateways, referenceFrame);

        assertMatchesOfficial(image);
    }

    @Test
    void pngMatchesOfficialViewer() throws Exception {
        byte[] png = renderer.renderPng(gateways, referenceFrame);

        assertMatchesOfficial(ImageIO.read(new ByteArrayInputStream(png)));
    }

    @Test
    void svgMatchesOfficialViewerAndIsLocal() throws Exception {
        String svg = renderer.renderSvg(gateways, referenceFrame);

        Element root = parseSecure(svg).getDocumentElement();
        assertEquals("300", root.getAttribute("width"));
        assertEquals("100", root.getAttribute("height"));
        assertEquals("0 0 300 100", root.getAttribute("viewBox"));
        assertFalse(svg.contains("<!DOCTYPE"));
        assertFalse(svg.contains("http://www.w3.org/TR"), "no external DTD reference");
        assertMatchesOfficial(rasterize(new ByteArrayInputStream(svg.getBytes("UTF-8"))));
    }

    @Test
    void defaultBoundsKeepEveryStrokeInside() {
        OutputGeometry geometry = renderer.layout(gateways, OutputOptions.builder()
                .boundsMode(BoundsMode.PAINTED).build());
        Rectangle2D painted = geometry.getSourceBounds();

        // Diamonds span x 5..285, y 5..85; stroke and miter joins add < 1 unit.
        assertTrue(painted.getMinX() < 5 && painted.getMinX() > 3.5, painted.toString());
        assertTrue(painted.getMaxX() > 285 && painted.getMaxX() < 286.5, painted.toString());
        assertTrue(painted.getMinY() < 5 && painted.getMinY() > 3.5, painted.toString());
        assertTrue(painted.getMaxY() > 85 && painted.getMaxY() < 86.5, painted.toString());
        assertEquals((int) Math.ceil(painted.getWidth()), geometry.getWidth());
    }

    @Test
    void scaledRasterAndSvgAgree() throws Exception {
        OutputOptions options = OutputOptions.builder().scale(2).padding(10).build();

        BufferedImage raster = renderer.renderImage(gateways, options);
        String svg = renderer.renderSvg(gateways, options);
        BufferedImage vector = rasterize(new ByteArrayInputStream(svg.getBytes("UTF-8")));

        assertEquals(raster.getWidth(), vector.getWidth());
        assertEquals(raster.getHeight(), vector.getHeight());
        // Anti-aliased edge pixels may fall on either side of a category
        // threshold; compare every pixel whose 3x3 neighborhood is uniform.
        int different = 0;
        int compared = 0;
        for (int y = 1; y < raster.getHeight() - 1; y++) {
            for (int x = 1; x < raster.getWidth() - 1; x++) {
                if (isStable(raster, x, y)) {
                    compared++;
                    if (colorCategory(raster.getRGB(x, y)) != colorCategory(vector.getRGB(x, y))) {
                        different++;
                    }
                }
            }
        }
        assertTrue(compared > raster.getWidth() * raster.getHeight() * 9 / 10);
        assertTrue(different <= ALLOWED_STABLE_PIXEL_DIFFERENCES,
                "Raster and SVG differ at " + different + " stable pixels");
    }

    private void assertMatchesOfficial(BufferedImage rendered) throws Exception {
        BufferedImage official;
        try (InputStream input = resource(OFFICIAL_SVG)) {
            official = rasterize(input);
        }
        assertEquals(WIDTH, rendered.getWidth());
        assertEquals(HEIGHT, rendered.getHeight());
        assertTrue(countDark(rendered) > 300, "rendered output must contain gateways");
        int mismatches = 0;
        for (int y = 1; y < HEIGHT - 1; y++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                if (isStable(official, x, y)
                        && colorCategory(official.getRGB(x, y)) != colorCategory(rendered.getRGB(x, y))) {
                    mismatches++;
                }
            }
        }
        assertTrue(mismatches <= ALLOWED_STABLE_PIXEL_DIFFERENCES,
                "Output differs from pinned official viewer at " + mismatches + " stable pixels");
    }

    private static String gatewayCalls() {
        String[] types = {"parallel", "exclusive", "complex"};
        StringBuilder script = new StringBuilder();
        for (int index = 0; index < types.length; index++) {
            script.append("var Shape = mxCellRenderer.shapes['mxgraph.bpmn.gateway2'];")
                    .append("var shape = new Shape();")
                    .append("shape.style = {gwType: '").append(types[index]).append("'};")
                    .append("var canvas = new DrawioJavaCanvas();")
                    .append("canvas.save();")
                    .append("canvas.setFillColor('#ffffff');")
                    .append("canvas.setStrokeColor('#000000');")
                    .append("canvas.setStrokeWidth(1);")
                    .append("shape.paintVertexShape(canvas, ").append(5 + 100 * index)
                    .append(", 5, 80, 80);")
                    .append("canvas.restore();");
        }
        return script.append("'rendered';").toString();
    }

    private static Document parseSecure(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static BufferedImage rasterize(InputStream svg) throws TranscoderException {
        Capture transcoder = new Capture();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES,
                Boolean.FALSE);
        TranscoderInput input = new TranscoderInput(svg);
        input.setURI("about:blank");
        transcoder.transcode(input, null);
        return transcoder.image;
    }

    private static boolean isStable(BufferedImage image, int x, int y) {
        int expected = colorCategory(image.getRGB(x, y));
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (expected != colorCategory(image.getRGB(x + dx, y + dy))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countDark(BufferedImage image) {
        int dark = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (colorCategory(image.getRGB(x, y)) == 1) {
                    dark++;
                }
            }
        }
        return dark;
    }

    private static int colorCategory(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha < 64) {
            return 0;
        }
        int sum = ((argb >>> 16) & 0xff) + ((argb >>> 8) & 0xff) + (argb & 0xff);
        return sum < 384 ? 1 : 2;
    }

    private InputStream resource(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing test fixture: " + path);
        }
        return input;
    }

    private static final class Capture extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage rendered, TranscoderOutput ignored) {
            image = rendered;
        }
    }
}
