package com.aresstack.drawio.graal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.canvas.Java2DCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decode the original draw.io stencil sets offline inside the restricted
 * GraalJS runtime and paint them through the original mxStencil.
 */
class DrawioOfflineStencilsTest {

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void decodesBundledBpmnAndBasicSetsWithoutBrowserApis() {
        String source = stencilRuntime()
                + "var before = [typeof window, typeof document, typeof XMLHttpRequest,"
                + " typeof require, typeof process].join(':');"
                + "mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/bpmn.xml');"
                + "mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/basic.xml');"
                + "var names = DrawioStencils.names();"
                + "before + '|' + DrawioStencils.loadedFiles.join(',') + '|' + names.join(',');";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        String[] parts = result.getOutput().split("\\|", -1);
        assertEquals("undefined:undefined:undefined:undefined:undefined", parts[0]);
        assertEquals("stencils/bpmn.xml,stencils/basic.xml", parts[1]);
        List<String> names = Arrays.asList(parts[2].split(","));
        assertEquals(39 + 30, names.size(), names.toString());
        for (String expected : new String[] {
                "mxgraph.bpmn.gateway", "mxgraph.bpmn.gateway_and",
                "mxgraph.bpmn.gateway_xor_(data)", "mxgraph.bpmn.user_task",
                "mxgraph.bpmn.service_task", "mxgraph.bpmn.timer_start",
                "mxgraph.bpmn.message_end", "mxgraph.bpmn.terminate",
                "mxgraph.basic.star", "mxgraph.basic.heart", "mxgraph.basic.x",
                "mxgraph.basic.8_point_star"}) {
            assertTrue(names.contains(expected), expected + " missing from " + names);
        }
    }

    @Test
    void registersEveryStencilOfTheSelectedSets() {
        String source = stencilRuntime()
                + "var counts = {};"
                + "['bpmn', 'basic', 'flowchart', 'arrows'].forEach(function (set) {"
                + "  mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/' + set + '.xml'); });"
                + "DrawioStencils.names().forEach(function (name) {"
                + "  var set = name.split('.')[1]; counts[set] = (counts[set] || 0) + 1; });"
                + "['bpmn', 'basic', 'flowchart', 'arrows'].map(function (set) {"
                + "  return set + '=' + counts[set]; }).join(',')"
                + " + ',missing=' + DrawioStencils.missing.length;";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("bpmn=39,basic=30,flowchart=36,arrows=34,missing=0", result.getOutput());
    }

    @Test
    void parsesOriginalStencilGeometryAndConstraints() {
        String source = stencilRuntime()
                + "var s = mxStencilRegistry.getStencil('mxgraph.bpmn.gateway_and');"
                + "[s instanceof mxStencil, s.w0, s.h0, s.aspect, s.strokewidth,"
                + " s.constraints.length, s.constraints[0].name, s.constraints[0].point.x,"
                + " s.bgNode.nodeName, s.fgNode.nodeName].join(',');";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("true,99,99,fixed,inherit,4,N,0.5,background,foreground",
                result.getOutput());
    }

    @Test
    void resolvesStencilsOnDemandFromTheBundledTableOnly() {
        String source = stencilRuntime()
                + "var decision = mxStencilRegistry.getStencil('mxgraph.flowchart.decision');"
                + "var arrow = mxStencilRegistry.getStencil('mxgraph.arrows.arrow_right');"
                + "var again = mxStencilRegistry.getStencil('mxgraph.flowchart.decision');"
                + "var builtIn = mxStencilRegistry.getStencil('rectangle');"
                + "[decision != null, arrow != null, again === decision, builtIn === undefined,"
                + " DrawioStencils.loadedFiles.join(';'), DrawioStencils.missing.length].join(',');";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("true,true,true,true,stencils/flowchart.xml;stencils/arrows.xml,0",
                result.getOutput());
    }

    @Test
    void failsExplicitlyForStencilSetsThatAreNotBundled() {
        String source = stencilRuntime()
                + "mxStencilRegistry.getStencil('mxgraph.aws4.lambda_function');";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertFalse(result.isSuccessful());
        assertTrue(result.getErrorMessage().contains(
                "Unsupported draw.io stencil: mxgraph.aws4.lambda_function"),
                result.getErrorMessage());
    }

    @Test
    void recordsMissingSetsAndUnknownNamesInLenientMode() {
        String source = stencilRuntime()
                + "DrawioStencils.strict = false;"
                + "var aws = mxStencilRegistry.getStencil('mxgraph.aws4.lambda_function');"
                + "var unknown = mxStencilRegistry.getStencil('mxgraph.bpmn.no_such_shape');"
                + "var proto = mxStencilRegistry.getStencil('mxgraph.__proto__.constructor');"
                + "var plain = mxStencilRegistry.getStencil('ellipse');"
                + "var entries = DrawioStencils.missing.map(function (m) {"
                + "  return m.reason + '=' + (m.name || m.file); });"
                + "[aws == null, unknown == null, proto == null, plain === undefined].join(',')"
                + " + '|' + entries.join(';');";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("true,true,true,true|"
                + "stencil-set-not-bundled=stencils/aws4.xml;"
                + "unknown-stencil=mxgraph.aws4.lambda_function;"
                + "unknown-stencil=mxgraph.bpmn.no_such_shape;"
                + "stencil-set-not-bundled=stencils/__proto__.xml;"
                + "unknown-stencil=mxgraph.__proto__.constructor",
                result.getOutput());
    }

    @Test
    void enforcesThePerSetInflateBudget() {
        DrawioHost host = new DrawioHost(denyResources(), null, null,
                new DrawioBinaryCodec(1024, 1024));

        ScriptExecutionResult result = evaluate(stencilRuntime()
                + "mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/bpmn.xml');", host);

        assertFalse(result.isSuccessful());
        assertTrue(result.getErrorMessage().contains("Inflated stencil data exceeds 1024 bytes"),
                result.getErrorMessage());
    }

    @Test
    void enforcesTheCumulativeInflateBudget() {
        DrawioBinaryCodec measuring = new DrawioBinaryCodec();
        ScriptExecutionResult first = evaluate(stencilRuntime()
                        + "mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/bpmn.xml'); 'ok';",
                new DrawioHost(denyResources(), null, null, measuring));
        assertTrue(first.isSuccessful(), first.getErrorMessage());
        long bpmnBytes = measuring.getTotalInflatedBytes();
        assertTrue(bpmnBytes > 1024, "inflated bpmn.xml size " + bpmnBytes);

        DrawioBinaryCodec budget = new DrawioBinaryCodec(
                DrawioBinaryCodec.DEFAULT_MAX_INFLATED_BYTES_PER_CALL, bpmnBytes + 100);
        ScriptExecutionResult result = evaluate(stencilRuntime()
                        + "mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/bpmn.xml');"
                        + "mxStencilRegistry.loadStencilSet(STENCIL_PATH + '/basic.xml');",
                new DrawioHost(denyResources(), null, null, budget));

        assertFalse(result.isSuccessful());
        assertTrue(result.getErrorMessage().contains("Cumulative inflated stencil data exceeds"),
                result.getErrorMessage());
        assertEquals(bpmnBytes, budget.getTotalInflatedBytes());
    }

    @Test
    void exposesOnlyTheExportedCodecMethods() {
        String source = stencilRuntime()
                + "var codec = drawioHost.binaryCodec();"
                + "[typeof codec.inflateRaw, typeof codec.getTotalInflatedBytes,"
                + " typeof codec.getClass, atob('aGk='), new TextDecoder().decode("
                + " new Uint8Array([0xE2, 0x82, 0xAC]))].join(',');";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("function,undefined,undefined,hi,€", result.getOutput());
    }

    @Test
    void paintsOriginalBpmnGatewayStencilIntoJava2D() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try (Java2DCanvas canvas = new Java2DCanvas(graphics)) {
            DrawioHost host = new DrawioHost(denyResources(), new DrawioCanvasBridge(canvas));
            String source = stencilRuntime()
                    + scripts.load("drawio-canvas-adapter.js") + "\n"
                    + "var stencil = mxStencilRegistry.getStencil('mxgraph.bpmn.gateway_and');"
                    + "var shape = {style: {strokeWidth: 4}, fill: '#ffffff',"
                    + " stroke: '#000000', outline: false};"
                    + "var c = new DrawioJavaCanvas();"
                    + "c.setFillColor(shape.fill); c.setStrokeColor(shape.stroke);"
                    + "stencil.drawShape(c, shape, 10, 10, 80, 80);"
                    + "'painted';";

            ScriptExecutionResult result = evaluate(source, host);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("painted", result.getOutput());
        } finally {
            graphics.dispose();
        }

        int black = Color.BLACK.getRGB();
        int white = Color.WHITE.getRGB();
        // Centre of the plus sign drawn by <foreground>.
        assertEquals(black, image.getRGB(50, 50));
        // Horizontal and vertical bars of the plus sign.
        assertEquals(black, image.getRGB(35, 50));
        assertEquals(black, image.getRGB(50, 65));
        // White diamond fill between the plus sign arms.
        assertEquals(white, image.getRGB(40, 40));
        assertEquals(white, image.getRGB(60, 60));
        assertEquals(white, image.getRGB(50, 20));
        // Diamond outline on the left edge (vertex at x=10, y=50).
        assertEquals(black, image.getRGB(12, 50));
        // Corners stay outside the diamond.
        assertEquals(0, image.getRGB(15, 15));
        assertEquals(0, image.getRGB(85, 85));
    }

    private String stencilRuntime() {
        return scripts.load("test-bootstrap.js") + "\n"
                + scripts.load("drawio-shapes.js") + "\n"
                + scripts.load("stencil-test-bootstrap.js") + "\n"
                + scripts.load("test-upstream/mxConnectionConstraint.js") + "\n"
                + scripts.load("test-upstream/mxStencilRegistry.js") + "\n"
                + scripts.load("test-upstream/mxStencil.js") + "\n"
                + scripts.load("drawio-stencil-runtime.js") + "\n"
                + scripts.load("drawio-stencils.js") + "\n";
    }

    private static ScriptResourceProvider denyResources() {
        return resourceId -> {
            throw new IllegalArgumentException("External resource access is disabled");
        };
    }

    private DrawioHost resourceOnlyHost() {
        return new DrawioHost(denyResources());
    }

    private ScriptExecutionResult evaluate(String source, DrawioHost host) {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(30))) {
            return runtime.execute("drawio-offline-stencils-test.js", source, host);
        }
    }
}
