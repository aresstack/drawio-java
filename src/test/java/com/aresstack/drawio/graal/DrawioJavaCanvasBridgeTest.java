package com.aresstack.drawio.graal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.canvas.Java2DCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify original Draw.io JavaScript draws into Java2D via restricted GraalVM host calls.
 */
class DrawioJavaCanvasBridgeTest {

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void rendersOriginalParallelBpmnGatewayWithoutBrowserOrNode() {
        BufferedImage image = renderGateway("parallel");

        assertEquals(Color.BLACK.getRGB(), image.getRGB(45, 45));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(45, 15));
        assertEquals(0, image.getRGB(5, 5));
    }

    @Test
    void rendersOriginalExclusiveBpmnGatewayWithoutReimplementingItsShape() {
        BufferedImage image = renderGateway("exclusive");

        assertEquals(Color.BLACK.getRGB(), image.getRGB(45, 45));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(45, 15));
        assertEquals(0, image.getRGB(5, 5));
    }

    @Test
    void rendersOriginalComplexBpmnGatewayThroughTheSameBridge() {
        BufferedImage image = renderGateway("complex");

        assertEquals(Color.BLACK.getRGB(), image.getRGB(45, 45));
        assertEquals(Color.WHITE.getRGB(), image.getRGB(45, 15));
        assertEquals(0, image.getRGB(5, 5));
    }

    @Test
    void forwardsQuadraticAndCubicCurvesToJava2D() {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            String drawing = "var canvas = new DrawioJavaCanvas();"
                    + "canvas.setStrokeColor('#ff0000');"
                    + "canvas.setStrokeWidth(2);"
                    + "canvas.begin();"
                    + "canvas.moveTo(5, 40);"
                    + "canvas.quadTo(25, 5, 40, 40);"
                    + "canvas.curveTo(50, 60, 55, 10, 65, 40);"
                    + "canvas.stroke(); 'done';";

            ScriptExecutionResult result = executeDrawing(fixture, drawing);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertTrue(fixture.visiblePixelCount() > 30);
        }
    }

    @Test
    void forwardsSaveRestoreAndTransformationWithoutStateLeakage() {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            String drawing = "var canvas = new DrawioJavaCanvas();"
                    + "canvas.save();"
                    + "canvas.translate(20, 0);"
                    + "canvas.setFillColor('#0000ff');"
                    + "canvas.rect(0, 0, 10, 10);"
                    + "canvas.fill();"
                    + "canvas.restore();"
                    + "canvas.setFillColor('#ff0000');"
                    + "canvas.rect(0, 0, 10, 10);"
                    + "canvas.fill(); 'done';";

            ScriptExecutionResult result = executeDrawing(fixture, drawing);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(5, 5));
            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(25, 5));
            assertEquals(0, fixture.image.getRGB(15, 5));
        }
    }

    @Test
    void recordsMidPathTranslationsBeforeRestoringOriginalCoordinates() {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            String drawing = "var canvas = new DrawioJavaCanvas();"
                    + "canvas.setStrokeColor('#ff0000');"
                    + "canvas.begin();"
                    + "canvas.moveTo(5, 20);"
                    + "canvas.translate(20, 0);"
                    + "canvas.lineTo(15, 20);"
                    + "canvas.translate(-20, 0);"
                    + "canvas.stroke(); 'done';";

            ScriptExecutionResult result = executeDrawing(fixture, drawing);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(30, 20));
            assertEquals(0, fixture.image.getRGB(40, 20));
        }
    }

    @Test
    void preservesStrokeWidthWhenScaledJavaScriptStateIsRestored() {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            String drawing = "var canvas = new DrawioJavaCanvas();"
                    + "canvas.setStrokeColor('#ff0000');"
                    + "canvas.setStrokeWidth(2);"
                    + "canvas.scale(2);"
                    + "var original = canvas.state.strokeWidth;"
                    + "canvas.setStrokeWidth(original);"
                    + "canvas.begin();"
                    + "canvas.moveTo(5, 30);"
                    + "canvas.lineTo(35, 30);"
                    + "canvas.stroke(); original;";

            ScriptExecutionResult result = executeDrawing(fixture, drawing);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("4", result.getOutput());
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(20, 60));
            assertEquals(0, fixture.image.getRGB(20, 56));
        }
    }

    @Test
    void keepsJavaReflectionUnavailableOnCanvasBridge() {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            ScriptExecutionResult result = executeDrawing(fixture,
                    "typeof drawioHost.canvas().getClass + ':'"
                            + " + typeof drawioHost.canvas().canvas + ':'"
                            + " + typeof drawioHost.canvas().close;");

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("undefined:undefined:undefined", result.getOutput());
        }
    }

    @Test
    void rejectsShadowsInsteadOfSilentlyRenderingWrongDiagram() {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            ScriptExecutionResult result = executeDrawing(fixture,
                    "var canvas = new DrawioJavaCanvas(); canvas.setShadow(true);");

            assertFalse(result.isSuccessful());
            assertTrue(result.getErrorMessage().contains("Shadow")
                    || result.getErrorMessage().contains("shadow"));
        }
    }

    private BufferedImage renderGateway(String gatewayType) {
        try (GraphicsFixture fixture = new GraphicsFixture()) {
            String draw = "var Shape = mxCellRenderer.shapes['mxgraph.bpmn.gateway2'];"
                    + "var shape = new Shape();"
                    + "shape.style = {gwType:'" + gatewayType + "'};"
                    + "var canvas = new DrawioJavaCanvas();"
                    + "canvas.setFillColor('#ffffff');"
                    + "canvas.setStrokeColor('#000000');"
                    + "shape.paintVertexShape(canvas, 5, 5, 80, 80);"
                    + "'rendered';";

            ScriptExecutionResult result = executeDrawing(fixture, draw);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("rendered", result.getOutput());
            return fixture.image;
        }
    }

    private ScriptExecutionResult executeDrawing(GraphicsFixture fixture, String commands) {
        String script = scripts.load("test-bootstrap.js") + "\n"
                + scripts.load("drawio-shapes.js") + "\n"
                + scripts.load("drawio-canvas-adapter.js") + "\n"
                + commands;

        DrawioHost host = new DrawioHost(resourceId -> {
            throw new IllegalArgumentException("External resources are disabled");
        }, new DrawioCanvasBridge(fixture.canvas));

        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
            return runtime.execute("drawio-java2d-bridge.js", script, host);
        }
    }

    private static final class GraphicsFixture implements AutoCloseable {
        private final BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        private final Graphics2D graphics = image.createGraphics();
        private final DrawioCanvas canvas = new Java2DCanvas(graphics);

        private int visiblePixelCount() {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) {
                        count++;
                    }
                }
            }
            return count;
        }

        @Override
        public void close() {
            canvas.close();
            graphics.dispose();
        }
    }
}
