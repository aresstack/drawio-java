package com.aresstack.drawio.graal;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.canvas.Java2DCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compare original Draw.io shapes rendered in Java2D against its pinned official viewer.
 */
class BpmnGatewayVisualReferenceTest {

    private static final int WIDTH = 300;
    private static final int HEIGHT = 100;
    private static final int ALLOWED_STABLE_PIXEL_DIFFERENCES = 30;

    private static final String DRAWIO_XML = "/drawio/golden/bpmn-gateway-fixture.drawio";
    private static final String OFFICIAL_SVG = "/drawio/golden/bpmn-gateway-reference.svg";

    private final ClasspathScriptLoader scripts = new ClasspathScriptLoader();

    @Test
    void matchesOfficialDrawioViewerForThreeOriginalGatewayVariants() throws Exception {
        List<Gateway> gateways = readGatewaysFromXml();
        BufferedImage official = rasterizeOfficialReference();
        BufferedImage java2d = renderOriginalJavaScriptShapes(gateways);

        assertEquals(3, gateways.size());
        assertEquals(WIDTH, official.getWidth());
        assertEquals(HEIGHT, official.getHeight());
        assertTrue(countDarkPixels(official) > 300,
                "The pinned official reference must contain actual gateway shapes");
        assertTrue(countDarkPixels(java2d) > 300,
                "The Java2D image must contain actual gateway shapes");

        int mismatches = countStablePixelDifferences(official, java2d);
        assertTrue(mismatches <= ALLOWED_STABLE_PIXEL_DIFFERENCES,
                "Rendered diagram differs from pinned official SVG at "
                        + mismatches + " stable pixels");
    }

    private List<Gateway> readGatewaysFromXml() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try (InputStream input = resource(DRAWIO_XML)) {
            Document document = factory.newDocumentBuilder().parse(input);
            NodeList cells = document.getElementsByTagName("mxCell");
            List<Gateway> gateways = new ArrayList<Gateway>();
            for (int index = 0; index < cells.getLength(); index++) {
                Element cell = (Element) cells.item(index);
                String style = cell.getAttribute("style");
                if (!"mxgraph.bpmn.gateway2".equals(styleAttribute(style, "shape"))) {
                    continue;
                }
                Element geometry = (Element) cell.getElementsByTagName("mxGeometry").item(0);
                gateways.add(new Gateway(
                        styleAttribute(style, "gwType"),
                        Double.parseDouble(geometry.getAttribute("x")),
                        Double.parseDouble(geometry.getAttribute("y")),
                        Double.parseDouble(geometry.getAttribute("width")),
                        Double.parseDouble(geometry.getAttribute("height"))));
            }
            return gateways;
        }
    }

    private String styleAttribute(String style, String name) {
        String key = name + "=";
        for (String item : style.split(";")) {
            if (item.startsWith(key)) {
                return item.substring(key.length());
            }
        }
        return "";
    }

    private BufferedImage rasterizeOfficialReference() throws IOException, TranscoderException {
        try (InputStream input = resource(OFFICIAL_SVG)) {
            ReferenceImageTranscoder transcoder = new ReferenceImageTranscoder();
            transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, Float.valueOf(WIDTH));
            transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, Float.valueOf(HEIGHT));
            transcoder.transcode(new TranscoderInput(input), null);
            return transcoder.image();
        }
    }

    private BufferedImage renderOriginalJavaScriptShapes(List<Gateway> gateways) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try (DrawioCanvas canvas = new Java2DCanvas(graphics);
             GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(20))) {
            DrawioHost host = new DrawioHost(resourceId -> {
                throw new IllegalArgumentException("External resources are disabled");
            }, new DrawioCanvasBridge(canvas));

            String source = scripts.load("test-bootstrap.js") + "\n"
                    + scripts.load("drawio-shapes.js") + "\n"
                    + scripts.load("drawio-canvas-adapter.js") + "\n"
                    + originalShapeCalls(gateways);

            ScriptExecutionResult result =
                    runtime.execute("official-gateway-parity.js", source, host);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("rendered", result.getOutput());
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private String originalShapeCalls(List<Gateway> gateways) {
        StringBuilder script = new StringBuilder();
        for (Gateway gateway : gateways) {
            if (!isKnownGatewayType(gateway.type)) {
                throw new IllegalArgumentException("Unsupported fixture gateway type: " + gateway.type);
            }
            script.append("var Shape = mxCellRenderer.shapes['mxgraph.bpmn.gateway2'];")
                    .append("var shape = new Shape();")
                    .append("shape.style = {gwType: '").append(gateway.type).append("'};")
                    .append("var canvas = new DrawioJavaCanvas();")
                    .append("canvas.save();")
                    .append("canvas.setFillColor('#ffffff');")
                    .append("canvas.setStrokeColor('#000000');")
                    .append("canvas.setStrokeWidth(1);")
                    .append(String.format(Locale.ROOT,
                            "shape.paintVertexShape(canvas, %s, %s, %s, %s);",
                            gateway.x, gateway.y, gateway.width, gateway.height))
                    .append("canvas.restore();");
        }
        return script.append("'rendered';").toString();
    }

    private boolean isKnownGatewayType(String type) {
        return "parallel".equals(type) || "exclusive".equals(type) || "complex".equals(type);
    }

    private int countDarkPixels(BufferedImage image) {
        int pixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (colorCategory(image.getRGB(x, y)) == 1) {
                    pixels++;
                }
            }
        }
        return pixels;
    }

    private int countStablePixelDifferences(BufferedImage official, BufferedImage rendered) {
        int mismatches = 0;
        for (int y = 1; y < HEIGHT - 1; y++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                if (isStableReferencePixel(official, x, y)
                        && colorCategory(official.getRGB(x, y))
                        != colorCategory(rendered.getRGB(x, y))) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    private boolean isStableReferencePixel(BufferedImage image, int x, int y) {
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

    private int colorCategory(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha < 64) {
            return 0;
        }
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        return red + green + blue < 384 ? 1 : 2;
    }

    private InputStream resource(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing test fixture: " + path);
        }
        return input;
    }

    private static final class ReferenceImageTranscoder extends ImageTranscoder {

        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage rendered, TranscoderOutput ignored) {
            image = rendered;
        }

        private BufferedImage image() {
            if (image == null) {
                throw new IllegalStateException("Official SVG has not been rendered");
            }
            return image;
        }
    }

    private static final class Gateway {
        private final String type;
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        private Gateway(String type, double x, double y, double width, double height) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
