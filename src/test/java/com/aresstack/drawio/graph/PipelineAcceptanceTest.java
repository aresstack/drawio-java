package com.aresstack.drawio.graph;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #9 acceptance: a complete draw.io file with several shapes, gateway2, a group,
 * routed edges with waypoints and labels, rendered through the production
 * XML -> mxCodec -> mxGraphView -> mxImageExport -> Java2D path and compared with
 * the pinned official viewer (see golden/pipeline/REFERENCE.md).
 */
class PipelineAcceptanceTest {

    private static final String GOLDEN = "/drawio/golden/pipeline/";
    /** Viewer root transform translate(-19,-9): viewer pixel + offset = diagram unit. */
    private static final double VIEWER_DX = 19;
    private static final double VIEWER_DY = 9;

    private static DrawioRenderer renderer() {
        return DrawioRenderer.builder()
                .pipeline(new OriginalGraphPipeline(OriginalGraphPipelineTest.TEST_SHAPES, 1))
                .build();
    }

    @Test
    void shapesEdgesAndArrowsMatchTheOfficialViewer() throws Exception {
        assertMatchesViewer("pipeline-acceptance-shapes");
    }

    /** Rotation, swimlane container, hidden layer, curved/elbow/ER edges, markers, dash patterns. */
    @Test
    void graphFeaturesMatchTheOfficialViewer() throws Exception {
        assertMatchesViewer("graph-features-shapes");
    }

    /** draw.io tables (Graph.js table helpers), stack layout container, collapsed swimlane. */
    @Test
    void tablesAndContainersMatchTheOfficialViewer() throws Exception {
        assertMatchesViewer("tables-shapes");
    }

    /** Line jumps (arc, gap), curved edge, ER markers (mxER.js), rhombus, cylinder3, doubleEllipse. */
    @Test
    void lineJumpsAndErMarkersMatchTheOfficialViewer() throws Exception {
        assertMatchesViewer("edge-jumps-shapes");
    }

    /** Zoom: the viewer at zoom 2 against RenderOptions.scale(2). */
    @Test
    void zoomedOutputMatchesTheOfficialViewerAtZoomTwo() throws Exception {
        assertMatchesViewer("pipeline-acceptance-shapes", 2);
        assertMatchesViewer("graph-features-shapes", 2);
    }

    private void assertMatchesViewer(String name) throws Exception {
        assertMatchesViewer(name, 1);
    }

    private void assertMatchesViewer(String name, int zoom) throws Exception {
        String xml = OriginalGraphPipelineTest.resource(GOLDEN + name + ".drawio");
        BufferedImage expected;
        String reference = GOLDEN + name + "-viewer" + (zoom == 1 ? "" : "-z" + zoom) + ".png";
        try (InputStream in = getClass().getResourceAsStream(reference)) {
            expected = ImageIO.read(in);
        }
        BufferedImage actual;
        try (DrawioRenderer renderer = renderer()) {
            RenderResult<BufferedImage> result = renderer.renderImage(xml,
                    RenderOptions.builder().background("#ffffff").scale(zoom).build());
            actual = result.getOutput();
            assertTrue(result.isComplete(), String.valueOf(result.getWarnings()));
        }
        assertEquals(expected.getWidth(), actual.getWidth(), 3.0 * zoom);
        assertEquals(expected.getHeight(), actual.getHeight(), 3.0 * zoom);

        double best = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                best = Math.max(best, Math.min(recall(expected, actual, dx, dy), recall(actual, expected, -dx, -dy)));
            }
        }
        assertTrue(best >= 0.98, name + " at zoom " + zoom + ": dark-pixel agreement with the viewer: " + best);
    }

    @Test
    void labelsArePlacedWhereTheViewerPlacesThem() throws Exception {
        String xml = OriginalGraphPipelineTest.resource(GOLDEN + "pipeline-acceptance.drawio");
        DrawioScene scene = new OriginalGraphPipeline(OriginalGraphPipelineTest.TEST_SHAPES, 1)
                .prepare(new DiagramPage(0, "p", "Page", xml), new RenderSession(RenderOptions.defaults()));
        List<Object[]> texts = new ArrayList<Object[]>();
        DrawioCanvas canvas = (DrawioCanvas) Proxy.newProxyInstance(DrawioCanvas.class.getClassLoader(),
                new Class<?>[] {DrawioCanvas.class}, (proxy, method, args) -> {
                    if ("text".equals(method.getName())) {
                        texts.add(args);
                    }
                    return null;
                });
        scene.paint(canvas);

        String json = OriginalGraphPipelineTest.resource(GOLDEN + "pipeline-acceptance-labels.json");
        Matcher m = Pattern.compile("\"text\": \"([^\"]*)\", \"x\": ([\\d.]+), \"y\": ([\\d.]+), "
                + "\"w\": ([\\d.]+), \"h\": ([\\d.]+)").matcher(json);
        int compared = 0;
        while (m.find()) {
            String label = m.group(1);
            double x = Double.parseDouble(m.group(2)) + VIEWER_DX;
            double y = Double.parseDouble(m.group(3)) + VIEWER_DY;
            double w = Double.parseDouble(m.group(4));
            double h = Double.parseDouble(m.group(5));
            Object[] call = find(texts, label);
            String align = (String) call[5];
            String valign = (String) call[6];
            double ax = (Double) call[0];
            double ay = (Double) call[1];
            double expectedX = "left".equals(align) ? x : "right".equals(align) ? x + w : x + w / 2;
            double expectedY = "top".equals(valign) ? y : "bottom".equals(valign) ? y + h : y + h / 2;
            assertEquals(expectedX, ax, 1.5, label + " x");
            assertEquals(expectedY, ay, 1.5, label + " y");
            compared++;
        }
        assertEquals(7, compared);
    }

    private static Object[] find(List<Object[]> texts, String label) {
        for (Object[] call : texts) {
            String value = ((String) call[4]).replaceAll("<[^>]*>", "");
            if (value.equals(label)) {
                return call;
            }
        }
        throw new AssertionError("no text call for " + label);
    }

    /** Fraction of dark pixels in a that have a dark pixel within 1 px in b (b shifted by dx, dy). */
    private static double recall(BufferedImage a, BufferedImage b, int dx, int dy) {
        int dark = 0;
        int matched = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (!isDark(a, x, y)) {
                    continue;
                }
                dark++;
                search:
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        if (isDark(b, x + dx + ox, y + dy + oy)) {
                            matched++;
                            break search;
                        }
                    }
                }
            }
        }
        return dark == 0 ? 0 : (double) matched / dark;
    }

    private static boolean isDark(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return false;
        }
        int argb = image.getRGB(x, y);
        int alpha = (argb >>> 24) & 0xff;
        int r = (argb >> 16) & 0xff;
        int g = (argb >> 8) & 0xff;
        int b = argb & 0xff;
        double luma = (0.299 * r + 0.587 * g + 0.114 * b) * alpha / 255.0 + 255.0 * (255 - alpha) / 255.0;
        return luma < 128;
    }
}
