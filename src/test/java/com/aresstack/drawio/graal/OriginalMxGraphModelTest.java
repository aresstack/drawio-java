package com.aresstack.drawio.graal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.canvas.Java2DCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercise original upstream mxGraphModel objects under a restricted GraalJS host.
 */
class OriginalMxGraphModelTest {

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void instantiatesRealGraphModelWithParentChildLookup() {
        String source = modelRuntime() + "\n"
                + "var model = new mxGraphModel();"
                + "var layer = model.getRoot().getChildAt(0);"
                + "var node = new mxCell('gateway', new mxGeometry(12, 34, 80, 70),"
                + " 'shape=mxgraph.bpmn.gateway2');"
                + "node.setVertex(true);"
                + "model.add(layer, node);"
                + "model.getCell(node.getId()) === node &&"
                + " model.getParent(node) === layer &&"
                + " model.getGeometry(node).width === 80 &&"
                + " model.isVertex(node);";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("true", result.getOutput());
    }

    @Test
    void retainsActualEdgeTerminalsAndNestedGroups() {
        String source = modelRuntime() + "\n"
                + "var model = new mxGraphModel();"
                + "var layer = model.getRoot().getChildAt(0);"
                + "var group = new mxCell('group', new mxGeometry(30, 40, 200, 100));"
                + "group.setVertex(true); model.add(layer, group);"
                + "var start = new mxCell('start', new mxGeometry(10, 20, 50, 50));"
                + "start.setVertex(true); model.add(group, start);"
                + "var end = new mxCell('end', new mxGeometry(80, 20, 50, 50));"
                + "end.setVertex(true); model.add(group, end);"
                + "var edge = new mxCell('arrow', new mxGeometry(), 'edgeStyle=orthogonalEdgeStyle');"
                + "edge.setEdge(true); model.add(group, edge);"
                + "model.setTerminal(edge, start, true);"
                + "model.setTerminal(edge, end, false);"
                + "model.getTerminal(edge, true) === start &&"
                + "model.getTerminal(edge, false) === end &&"
                + "model.getParent(start) === group && model.isEdge(edge);";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("true", result.getOutput());
    }

    @Test
    void emitsOriginalGraphModelChangeEventsWithoutBrowserDom() {
        String source = modelRuntime() + "\n"
                + "var model = new mxGraphModel();"
                + "var changes = 0;"
                + "model.addListener(mxEvent.CHANGE, function () { changes++; });"
                + "var cell = new mxCell('changed', new mxGeometry(0, 0, 20, 20));"
                + "cell.setVertex(true);"
                + "model.add(model.getRoot().getChildAt(0), cell);"
                + "changes > 0 && model.getCell(cell.getId()) === cell;";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("true", result.getOutput());
    }

    @Test
    void keepsBrowserDocumentAndNetworkApisUnavailable() {
        String source = modelRuntime() + "\n"
                + "typeof document + ':' + typeof window.fetch + ':'"
                + " + typeof require + ':' + typeof process;";

        ScriptExecutionResult result = evaluate(source, resourceOnlyHost());

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("undefined:undefined:undefined:undefined", result.getOutput());
    }

    @Test
    void paintsOriginalBpmnShapeFromTheRealGraphModelIntoJava2D() {
        BufferedImage image = new BufferedImage(110, 110, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try (Java2DCanvas canvas = new Java2DCanvas(graphics)) {
            DrawioHost host = new DrawioHost(resourceId -> {
                throw new IllegalArgumentException("External resource access is disabled");
            }, new DrawioCanvasBridge(canvas));

            String source = modelRuntime() + "\n"
                    + scripts.load("drawio-shapes.js") + "\n"
                    + scripts.load("drawio-canvas-adapter.js") + "\n"
                    + "var model = new mxGraphModel();"
                    + "var layer = model.getRoot().getChildAt(0);"
                    + "var cell = new mxCell('gateway', new mxGeometry(10, 10, 80, 80),"
                    + "'shape=mxgraph.bpmn.gateway2;gwType=parallel');"
                    + "cell.setVertex(true); model.add(layer, cell);"
                    + "var shapeConstructor = mxCellRenderer.shapes['mxgraph.bpmn.gateway2'];"
                    + "var shape = new shapeConstructor();"
                    + "shape.style = {gwType: 'parallel'};"
                    + "var c = new DrawioJavaCanvas();"
                    + "c.setFillColor('#ffffff'); c.setStrokeColor('#000000');"
                    + "var bounds = model.getGeometry(model.getCell(cell.getId()));"
                    + "shape.paintVertexShape(c, bounds.x, bounds.y, bounds.width, bounds.height);"
                    + "'done';";

            ScriptExecutionResult result = evaluate(source, host);
            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("done", result.getOutput());
            assertEquals(Color.BLACK.getRGB(), image.getRGB(50, 50));
            assertEquals(Color.WHITE.getRGB(), image.getRGB(50, 20));
        } finally {
            graphics.dispose();
        }
    }

    private String modelRuntime() {
        return scripts.load("test-bootstrap.js") + "\n"
                + scripts.load("model-bootstrap.js") + "\n"
                + scripts.load("drawio-model.js");
    }

    private DrawioHost resourceOnlyHost() {
        return new DrawioHost(resourceId -> {
            throw new IllegalArgumentException("External resource access is disabled");
        });
    }

    private ScriptExecutionResult evaluate(String source, DrawioHost host) {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
            return runtime.execute("mxgraph-real-model-test.js", source, host);
        }
    }
}
