package com.aresstack.drawio.graal;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the original pinned Draw.io JavaScript without loading any browser UI.
 */
class DrawioUpstreamBundleTest {

    private static final DrawioHost HOST = new DrawioHost(resourceId -> {
        throw new IllegalArgumentException("External resources are unavailable");
    });

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void registersTheOriginalBpmnGatewayAndRunsItsJavaScriptDrawingOperations() {
        String drawioSources = scripts.load("drawio-shapes.js");
        String testBootstrap = scripts.load("test-bootstrap.js");
        String probe = String.join("\n",
                "var gatewayType = mxCellRenderer.shapes['mxgraph.bpmn.gateway2'];",
                "var gateway = new gatewayType();",
                "gateway.style = {gwType: 'parallel'};",
                "var operations = [];",
                "var canvas = {",
                "  state: {fillColor:'#ffffff', strokeColor:'#000000'},",
                "  translate:function(x,y){operations.push('translate')},",
                "  begin:function(){operations.push('begin')},",
                "  moveTo:function(x,y){operations.push('moveTo')},",
                "  lineTo:function(x,y){operations.push('lineTo')},",
                "  close:function(){operations.push('close')},",
                "  fillAndStroke:function(){operations.push('fillAndStroke')},",
                "  setShadow:function(value){operations.push('shadow')},",
                "  setFillColor:function(value){this.state.fillColor=value}",
                "};",
                "gateway.paintVertexShape(canvas, 0, 0, 80, 80);",
                "operations.filter(function(op) { return op==='fillAndStroke'; }).length +",
                " ':' + typeof mxAbstractCanvas2D + ':' + typeof gatewayType");

        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
            ScriptExecutionResult result = runtime.execute(
                    "drawio-original-gateway-smoke.js",
                    testBootstrap + "\n" + drawioSources + "\n" + probe,
                    HOST);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("2:function:function", result.getOutput());
        }
    }

    @Test
    void preservesTheUpstreamCanvasScaleAndTranslationState() {
        String script = scripts.load("test-bootstrap.js") + "\n"
                + scripts.load("drawio-shapes.js") + "\n"
                + "var c = new mxAbstractCanvas2D();"
                + "c.translate(10,0); c.scale(2);"
                + "c.state.dx + ':' + c.state.scale;";

        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
            ScriptExecutionResult result = runtime.execute(
                    "drawio-original-canvas-smoke.js", script, HOST);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("10:2", result.getOutput());
        }
    }
}
