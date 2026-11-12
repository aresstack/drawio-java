package com.aresstack.drawio.graal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.canvas.CanvasDiagnostics;
import com.aresstack.drawio.canvas.ImageSourcePolicy;
import com.aresstack.drawio.canvas.Java2DCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the #10 canvas features through the original-JavaScript adapter and bridge.
 */
class DrawioCanvasAdapterExtensionsTest {

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void appliesMxShapeShadowStyleObjects() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.setShadow(true, {dx: 4, dy: 4, blur: 2, color: '#000000', opacity: 100});"
                    + "c.setFillColor('#ff0000'); c.rect(10, 10, 30, 30); c.fill();"
                    + "c.setShadow(false);"
                    + "c.setFillColor('#00ff00'); c.rect(60, 10, 20, 20); c.fill(); 'ok';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(20, 20));
            assertEquals(Color.BLACK.getRGB(), fixture.image.getRGB(42, 42));
            assertEquals(0, fixture.image.getRGB(82, 32));
        }
    }

    @Test
    void forwardsGradientsAndFallsBackToSolidWithoutEndColor() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.setGradient('#ff0000', '#0000ff', 0, 0, 40, 40, 'east', 1, 1);"
                    + "c.rect(0, 0, 40, 40); c.fill();"
                    + "c.setGradient('#00ff00', 'none', 0, 0, 10, 10, 'east', 1, 1);"
                    + "c.rect(60, 0, 10, 10); c.fill(); c.state.gradientColor + '';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("null", result.getOutput());
            assertTrue(new Color(fixture.image.getRGB(1, 20)).getRed() > 220);
            assertTrue(new Color(fixture.image.getRGB(38, 20)).getBlue() > 220);
            assertEquals(Color.GREEN.getRGB(), fixture.image.getRGB(65, 5));
        }
    }

    @Test
    void convertsArcsWithOriginalArcToCurves() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.setFillColor('#0000ff'); c.begin(); c.moveTo(10, 50);"
                    + "c.arcTo(40, 40, 0, 0, 1, 90, 50); c.close(); c.fill(); 'ok';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            // Half disc above the chord from (10,50) to (90,50).
            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(50, 30));
            assertEquals(0, fixture.image.getRGB(50, 60));
            assertEquals(0, fixture.image.getRGB(15, 15));
        }
    }

    @Test
    void paintsTextWithAllMxSvgCanvasArguments() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.setFontColor('#000000'); c.setFontSize(14); c.setFontFamily('Helvetica');"
                    + "c.setFontStyle(1);"
                    + "c.text(50, 50, 80, 40, 'Task<br>Name', 'center', 'middle', true, 'html',"
                    + " 'visible', false, 0, null); 'ok';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertTrue(fixture.inkPixels() > 40);
        }
    }

    @Test
    void reportsVerticalWritingModeAndDomNodeLabels() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.setFontColor('#000000');"
                    + "c.text(50, 50, 0, 0, 'x', 'center', 'middle', false, '', null, false, 0,"
                    + " 'vertical-rl');"
                    + "c.text(50, 50, 0, 0, {nodeType: 1}, 'center', 'middle', false, 'html', null,"
                    + " false, 0, null); 'ok';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals(2, fixture.unsupported.size());
            assertTrue(fixture.unsupported.get(0).startsWith("canvas:text-direction:vertical-rl"));
            assertEquals("label:dom-node", fixture.unsupported.get(1));
            assertEquals(0, fixture.inkPixels());
        }
    }

    @Test
    void reportsBlockedImagesAsMissingResources() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.image(0, 0, 10, 10, 'https://example.invalid/logo.png', true, false, false);"
                    + "c.image(0, 0, 10, 10, 'data:image/svg+xml;base64,PHN2Zy8+');'ok';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals(1, fixture.missing.size());
            assertEquals("image:external", fixture.missing.get(0));
            assertEquals(1, fixture.unsupported.size());
            assertEquals("image:svg", fixture.unsupported.get(0));
            assertEquals(0, fixture.inkPixels());
        }
    }

    @Test
    void paintsPermittedDataUriImages() throws IOException {
        String source = redImage();
        try (Fixture fixture = new Fixture(ImageSourcePolicy.dataUris(4096, 4096))) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.image(10, 10, 20, 20, '" + source + "'); 'ok';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertTrue(fixture.missing.isEmpty());
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(20, 20));
        }
    }

    @Test
    void keepsFixDashAndSetLinkWithoutFailing() {
        try (Fixture fixture = new Fixture(ImageSourcePolicy.NONE)) {
            ScriptExecutionResult result = fixture.run("var c = new DrawioJavaCanvas();"
                    + "c.setLink('https://example.invalid'); c.setDashed(true, true);"
                    + "c.setDashPattern('2 2'); c.setStrokeWidth(3); c.setStrokeColor('#0000ff');"
                    + "c.begin(); c.moveTo(10, 50); c.lineTo(90, 50); c.stroke(); c.state.fixDash + '';");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("true", result.getOutput());
            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(11, 50));
            assertEquals(0, fixture.image.getRGB(13, 50));
        }
    }

    private static String redImage() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private final class Fixture implements AutoCloseable, CanvasDiagnostics {
        private final BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        private final Graphics2D graphics = image.createGraphics();
        private final Java2DCanvas canvas = new Java2DCanvas(graphics);
        private final ImageSourcePolicy images;
        private final List<String> unsupported = new ArrayList<String>();
        private final List<String> missing = new ArrayList<String>();

        private Fixture(ImageSourcePolicy images) {
            this.images = images;
        }

        private ScriptExecutionResult run(String commands) {
            String script = scripts.load("test-bootstrap.js") + "\n"
                    + scripts.load("drawio-shapes.js") + "\n"
                    + scripts.load("drawio-canvas-adapter.js") + "\n"
                    + commands;
            DrawioHost host = new DrawioHost(resourceId -> {
                throw new IllegalArgumentException("External resources are disabled");
            }, new DrawioCanvasBridge(canvas, images, this));
            try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
                return runtime.execute("drawio-canvas-extensions.js", script, host);
            }
        }

        private int inkPixels() {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 64) {
                        count++;
                    }
                }
            }
            return count;
        }

        @Override
        public void unsupported(String capabilityId, String message) {
            unsupported.add(capabilityId);
        }

        @Override
        public void missingResource(String capabilityId, String message) {
            missing.add(capabilityId);
        }

        @Override
        public void close() {
            canvas.close();
            graphics.dispose();
        }
    }
}
