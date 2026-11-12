package com.aresstack.drawio.graph;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderWarning;
import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalGraphPipelineTest {

    static final List<String> TEST_SHAPES = OriginalGraphPipeline.OPTIONAL_SHAPE_BUNDLES;

    static String resource(String path) throws Exception {
        try (InputStream in = OriginalGraphPipelineTest.class.getResourceAsStream(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static List<String> record(DrawioScene scene) {
        List<String> calls = new ArrayList<String>();
        DrawioCanvas canvas = (DrawioCanvas) Proxy.newProxyInstance(DrawioCanvas.class.getClassLoader(),
                new Class<?>[] {DrawioCanvas.class}, (proxy, method, args) -> {
                    calls.add(method.getName() + (args == null ? "()" : Arrays.toString(args)));
                    return null;
                });
        scene.paint(canvas);
        return calls;
    }

    @Test
    void rendersConnectedDiagramWithGroupThroughOriginalView() throws Exception {
        String xml = resource("/drawio/golden/pipeline/pipeline-acceptance.drawio");
        RenderSession session = new RenderSession(RenderOptions.defaults());

        DrawioScene scene = new OriginalGraphPipeline(TEST_SHAPES, 1)
                .prepare(new DiagramPage(0, "p", "Page", xml), session);

        Rectangle2D bounds = scene.getLogicalBounds();
        assertNotNull(bounds);
        assertEquals(19.5, bounds.getX(), 1.0);
        assertEquals(600.5, bounds.getMaxX(), 1.0);
        List<String> calls = record(scene);
        String all = String.join("\n", calls);
        // Original ellipse and rounded rectangle shapes.
        assertTrue(all.contains("ellipse[20.0, 70.0, 80.0, 60.0]"), all);
        assertTrue(all.contains("roundrect[160.0, 70.0, 120.0, 60.0, 9.0, 9.0]"), all);
        // Orthogonal edge with waypoint routed by the original mxEdgeStyle.
        assertTrue(all.contains("moveTo[365.0, 75.0]") && all.contains("lineTo[365.0, 35.0]"), all);
        // Group children painted at absolute positions.
        assertTrue(all.contains("rect[460.0, 10.0, 140.0, 50.0]"), all);
        // Labels reach the canvas.
        assertTrue(all.contains("Review <b>request</b>"), all);
        for (RenderWarning warning : session.warnings()) {
            throw new AssertionError("unexpected warning " + warning.getCapabilityId() + " " + warning.getMessage());
        }
    }

    @Test
    void reportsUnknownShapeInsteadOfSilentRectangle() throws Exception {
        String xml = "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"x\" value=\"\" style=\"shape=mxgraph.nonexistent.thing;\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"40\" height=\"40\" as=\"geometry\"/></mxCell></root></mxGraphModel>";
        RenderException failure = org.junit.jupiter.api.Assertions.assertThrows(RenderException.class,
                () -> new OriginalGraphPipeline(Collections.<String>emptyList(), 1)
                        .prepare(new DiagramPage(0, "p", "Page", xml), new RenderSession(RenderOptions.defaults())));
        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());
        assertEquals("shape:mxgraph.nonexistent.thing", failure.getCapabilityId());
    }

    @Test
    void reportsStencilsMissingFromTheOfflineSetsThroughTheSession() throws Exception {
        String xml = "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"x\" value=\"\" style=\"shape=mxgraph.aws4.lambda_function;\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"40\" height=\"40\" as=\"geometry\"/></mxCell></root></mxGraphModel>";
        RenderException failure = org.junit.jupiter.api.Assertions.assertThrows(RenderException.class,
                () -> new OriginalGraphPipeline(TEST_SHAPES, 1)
                        .prepare(new DiagramPage(0, "p", "Page", xml), new RenderSession(RenderOptions.defaults())));
        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());
        assertEquals("stencil:mxgraph.aws4.lambda_function", failure.getCapabilityId());
    }

    @Test
    void paintsBpmnGatewayFromTheProductionShapeLibrary() throws Exception {
        String xml = "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"g\" value=\"\" style=\"shape=mxgraph.bpmn.gateway2;gwType=parallel;html=1;\""
                + " vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"50\" height=\"50\" as=\"geometry\"/></mxCell></root></mxGraphModel>";
        RenderSession session = new RenderSession(RenderOptions.defaults());

        String all = String.join("\n", record(new OriginalGraphPipeline(TEST_SHAPES, 1)
                .prepare(new DiagramPage(0, "p", "Page", xml), session)));

        // The diamond outline of mxgraph.bpmn.gateway2, not a rectangle fallback.
        assertTrue(all.contains("translate[10.0, 10.0]") && all.contains("moveTo[25.0, 0.0]")
                && all.contains("lineTo[50.0, 25.0]") && all.contains("lineTo[0.0, 25.0]"), all);
        assertTrue(!all.contains("rect["), all);
        assertTrue(session.warnings().isEmpty(), String.valueOf(session.warnings()));
    }

    @Test
    void escapesWrappedPlainTextLabelsLikeDrawio() throws Exception {
        String xml = "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                + "<mxCell id=\"plain\" value=\"A &lt; B &amp;&amp; &lt;b&gt;C&lt;/b&gt;\""
                + " style=\"whiteSpace=wrap;html=0;\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell>"
                + "<mxCell id=\"html\" value=\"&lt;b&gt;D&lt;/b&gt;\" style=\"whiteSpace=wrap;html=1;\""
                + " vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"60\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell>"
                + "</root></mxGraphModel>";
        DrawioScene scene = new OriginalGraphPipeline(Collections.<String>emptyList(), 1)
                .prepare(new DiagramPage(0, "p", "Page", xml), new RenderSession(RenderOptions.defaults()));
        String all = String.join("\n", record(scene));
        // Graph.js getLabelValue: wrapped html=0 labels are entity-escaped, so
        // the HTML text layout shows the markup literally.
        assertTrue(all.contains("A &lt; B &amp;&amp; &lt;b&gt;C&lt;/b&gt;"), all);
        // html=1 labels keep their markup.
        assertTrue(all.contains("<b>D</b>"), all);
    }

    @Test
    void replacesPlaceholdersLikeDrawio() throws Exception {
        String xml = "<mxGraphModel><root><mxCell id=\"0\"/>"
                + "<object id=\"1\" owner=\"Team A\"><mxCell parent=\"0\"/></object>"
                + "<object id=\"v\" label=\"%name% by %owner% (%width%x%height%, %id%) %missing% 100%%\""
                + " name=\"Order\" placeholders=\"1\">"
                + "<mxCell style=\"html=1;\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell></object>"
                + "<object id=\"p\" placeholder=\"owner\" placeholders=\"1\" label=\"ignored\">"
                + "<mxCell style=\"\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"60\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell></object>"
                + "<object id=\"d\" label=\"on %date{yyyy}%\" placeholders=\"1\">"
                + "<mxCell style=\"\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"110\" width=\"120\" height=\"40\" as=\"geometry\"/></mxCell></object>"
                + "</root></mxGraphModel>";
        RenderSession session = new RenderSession(RenderOptions.defaults());
        String all = String.join("\n", record(new OriginalGraphPipeline(Collections.<String>emptyList(), 1)
                .prepare(new DiagramPage(0, "p", "Page", xml), session)));

        assertTrue(all.contains("Order by Team A (120x40, v) %missing% 100%"), all);
        // placeholder="owner": the label is the inherited attribute value.
        assertTrue(all.contains("Team A"), all);
        assertTrue(!all.contains("ignored"), all);
        // Unported forms stay verbatim with a warning instead of failing.
        assertTrue(all.contains("on %date{yyyy}%"), all);
        assertEquals(1, session.warnings().size(), String.valueOf(session.warnings()));
        assertEquals("placeholder:unresolved", session.warnings().get(0).getCapabilityId());
    }
}
