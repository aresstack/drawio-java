package com.aresstack.drawio.graph;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderWarning;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;
import com.aresstack.drawio.render.RenderSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pipeline as a consumer sees it without the optional
 * {@code drawio-java-stencils} artifact: {@code drawio-stencils.js} is not on
 * the classpath, so {@link OriginalGraphPipeline#availableBundles} leaves it
 * out while the stencil runtime is still loaded.
 */
class StencilArtifactAbsentTest {

    /** What the default constructor picks when only the main jar is present. */
    static final List<String> WITHOUT_STENCIL_DATA;

    static {
        List<String> bundles = new ArrayList<String>(OriginalGraphPipeline.OPTIONAL_SHAPE_BUNDLES);
        bundles.remove(OriginalGraphPipeline.STENCIL_DATA_BUNDLE);
        WITHOUT_STENCIL_DATA = bundles;
    }

    @Test
    void bundleListStillLoadsTheStencilRuntime() {
        assertEquals(java.util.Arrays.asList("drawio-shape-library.js", "drawio-stencil-runtime.js"),
                WITHOUT_STENCIL_DATA);
    }

    @Test
    void xmlFlowchartStencilFailsAsUnsupportedStencil() {
        RenderException failure = assertThrows(RenderException.class, () -> prepare(WITHOUT_STENCIL_DATA,
                cell("shape=mxgraph.flowchart.decision;"), RenderOptions.defaults()));

        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());
        assertEquals("stencil:mxgraph.flowchart.decision", failure.getCapabilityId());
    }

    @Test
    void xmlBpmnStencilFailsAsUnsupportedStencil() {
        RenderException failure = assertThrows(RenderException.class, () -> prepare(WITHOUT_STENCIL_DATA,
                cell("shape=mxgraph.bpmn.gateway_and;"), RenderOptions.defaults()));

        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());
        assertEquals("stencil:mxgraph.bpmn.gateway_and", failure.getCapabilityId());
    }

    @Test
    void bpmnTaskMarkerThatNeedsStencilDataIsReported() {
        // mxgraph.bpmn.task is JavaScript, but its service marker is the XML
        // stencil mxgraph.bpmn.service_task.
        RenderException failure = assertThrows(RenderException.class, () -> prepare(WITHOUT_STENCIL_DATA,
                cell("shape=mxgraph.bpmn.task;taskMarker=service;"), RenderOptions.defaults()));

        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, failure.getCode());
        assertEquals("stencil:mxgraph.bpmn.service_task", failure.getCapabilityId());
    }

    @Test
    void warnPolicyReportsTheMissingStencilAsWarning() throws Exception {
        RenderSession session = new RenderSession(RenderOptions.builder()
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN).build());

        new OriginalGraphPipeline(WITHOUT_STENCIL_DATA, 1)
                .prepare(page(cell("shape=mxgraph.flowchart.decision;")), session);

        List<String> ids = new ArrayList<String>();
        for (RenderWarning warning : session.warnings()) {
            ids.add(warning.getCapabilityId());
        }
        assertTrue(ids.contains("stencil:mxgraph.flowchart.decision"), String.valueOf(ids));
    }

    @Test
    void javaScriptShapesStillRenderWithoutStencilData() throws Exception {
        RenderSession session = new RenderSession(RenderOptions.defaults());

        String all = String.join("\n", OriginalGraphPipelineTest.record(new OriginalGraphPipeline(
                WITHOUT_STENCIL_DATA, 1).prepare(page(
                cell("shape=mxgraph.bpmn.gateway2;gwType=parallel;")
                        + "<mxCell id=\"p\" value=\"\" style=\"shape=process;\" vertex=\"1\" parent=\"1\">"
                        + "<mxGeometry x=\"100\" y=\"10\" width=\"80\" height=\"40\" as=\"geometry\"/></mxCell>"),
                session)));

        // Diamond of mxgraph.bpmn.gateway2.
        assertTrue(all.contains("translate[10.0, 10.0]") && all.contains("moveTo[25.0, 0.0]")
                && all.contains("lineTo[50.0, 25.0]"), all);
        // shape=process (Shapes.js): the outer rectangle plus the two inner bars.
        assertTrue(all.contains("moveTo[108.0, 10.0]") && all.contains("lineTo[108.0, 50.0]"), all);
        assertTrue(session.warnings().isEmpty(), String.valueOf(session.warnings()));
    }

    @Test
    void sameStencilRendersWhenTheDataBundleIsPresent() throws Exception {
        RenderSession session = new RenderSession(RenderOptions.defaults());

        String all = String.join("\n", OriginalGraphPipelineTest.record(new OriginalGraphPipeline(
                OriginalGraphPipeline.OPTIONAL_SHAPE_BUNDLES, 1)
                .prepare(page(cell("shape=mxgraph.flowchart.decision;")), session)));

        // Original mxStencil diamond, scaled to the 50x50 geometry.
        assertTrue(all.contains("moveTo[") && all.contains("lineTo["), all);
        assertFalse(all.contains("rect["), all);
        assertTrue(session.warnings().isEmpty(), String.valueOf(session.warnings()));
    }

    private static void prepare(List<String> bundles, String cells, RenderOptions options) throws Exception {
        new OriginalGraphPipeline(bundles, 1).prepare(page(cells), new RenderSession(options));
    }

    private static String cell(String style) {
        return "<mxCell id=\"s\" value=\"\" style=\"" + style + "\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"50\" height=\"50\" as=\"geometry\"/></mxCell>";
    }

    private static DiagramPage page(String cells) {
        return new DiagramPage(0, "p", "Page", "<mxGraphModel><root><mxCell id=\"0\"/>"
                + "<mxCell id=\"1\" parent=\"0\"/>" + cells + "</root></mxGraphModel>");
    }
}
