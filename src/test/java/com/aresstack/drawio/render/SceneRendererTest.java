package com.aresstack.drawio.render;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.aresstack.drawio.canvas.DrawioCanvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneRendererTest {

    private final SceneRenderer renderer = new SceneRenderer();

    /** A 100x50 box at (10,10) with a 4-unit black stroke and red fill. */
    private static DrawioScene box(Rectangle2D logical) {
        return new DrawioScene() {
            @Override
            public void paint(DrawioCanvas canvas) {
                canvas.setFillColor("#ff0000");
                canvas.setStrokeColor("#000000");
                canvas.setStrokeWidth(4);
                canvas.rect(10, 10, 100, 50);
                canvas.fillAndStroke();
            }

            @Override
            public Rectangle2D getLogicalBounds() {
                return logical;
            }
        };
    }

    @Test
    void paintedBoundsIncludeStrokeWidth() {
        CanvasRecording recording = renderer.record(box(null), OutputOptions.defaults());

        Rectangle2D painted = SceneRenderer.paintedBounds(recording);

        assertRect(8, 8, 104, 54, painted);
    }

    @Test
    void paintedBoundsFollowCanvasTransformsAndClip() {
        DrawioScene scene = canvas -> {
            canvas.setFillColor("#000000");
            canvas.translate(100, 0);
            canvas.scale(2);
            canvas.rect(0, 0, 10, 10);
            canvas.fill();
            canvas.begin();
            canvas.rect(0, 0, 5, 5);
            canvas.clip();
            canvas.rect(0, 0, 100, 100);
            canvas.fill();
        };

        Rectangle2D painted = SceneRenderer.paintedBounds(renderer.record(scene,
                OutputOptions.defaults()));

        assertRect(200, 0, 20, 20, painted);
    }

    @Test
    void emptySceneHasNoPaintedBoundsAndRendersOnePixel() {
        DrawioScene empty = canvas -> { };

        assertNull(SceneRenderer.paintedBounds(renderer.record(empty, OutputOptions.defaults())));
        BufferedImage image = renderer.renderImage(empty, OutputOptions.defaults());
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
    }

    @Test
    void unionBoundsKeepStrokeOutsideLogicalBounds() {
        OutputGeometry geometry = renderer.layout(
                box(new Rectangle2D.Double(10, 10, 100, 50)), OutputOptions.defaults());

        assertRect(8, 8, 104, 54, geometry.getSourceBounds());
        assertEquals(104, geometry.getWidth());
        assertEquals(54, geometry.getHeight());
    }

    @Test
    void unionBoundsKeepLogicalAreaLargerThanPaint() {
        OutputGeometry geometry = renderer.layout(
                box(new Rectangle2D.Double(0, 0, 200, 100)), OutputOptions.defaults());

        assertRect(0, 0, 200, 100, geometry.getSourceBounds());
    }

    @Test
    void logicalBoundsModeClipsLikeDrawioExport() {
        OutputOptions options = OutputOptions.builder().boundsMode(BoundsMode.LOGICAL).build();
        DrawioScene scene = box(new Rectangle2D.Double(10, 10, 100, 50));

        OutputGeometry geometry = renderer.layout(scene, options);
        BufferedImage image = renderer.renderImage(scene, options);

        assertEquals(100, image.getWidth());
        assertEquals(50, image.getHeight());
        assertEquals(new Color(0, 0, 0), new Color(image.getRGB(0, 25)));
        assertEquals(new Color(255, 0, 0), new Color(image.getRGB(50, 25)));
        assertRect(10, 10, 100, 50, geometry.getSourceBounds());
        assertThrows(IllegalArgumentException.class,
                () -> renderer.layout(box(null), options));
    }

    @Test
    void scaleAndPaddingDetermineSizeAndPositions() {
        OutputOptions options = OutputOptions.builder().scale(2).padding(5).build();

        OutputGeometry geometry = renderer.layout(box(null), options);
        BufferedImage image = renderer.renderImage(box(null), options);

        assertEquals(2 * 104 + 10, geometry.getWidth());
        assertEquals(2 * 54 + 10, geometry.getHeight());
        assertEquals(geometry.getWidth(), image.getWidth());
        assertEquals(geometry.getHeight(), image.getHeight());
        // Fill corner (10,10) maps to padding + 2 * (10 - 8) = 9.
        assertEquals(9.0, geometry.toDevice(10, 10).getX(), 1e-9);
        assertEquals(0, TestImages.alpha(image, 2, 2), "padding stays transparent");
        assertEquals(new Color(0, 0, 0), new Color(image.getRGB(7, 50)));
        assertEquals(new Color(255, 0, 0), new Color(image.getRGB(60, 50)));
    }

    @Test
    void fractionalOriginMovesContentByWholePixelsLikeDrawioExport() {
        // Graph bounds of a 1px-stroked cell at x=20 are 19.5 (half stroke).
        DrawioScene scene = new DrawioScene() {
            @Override
            public void paint(DrawioCanvas canvas) {
                canvas.setStrokeColor("#000000");
                canvas.setStrokeWidth(1);
                canvas.rect(20.5, 10.5, 100, 50);
                canvas.stroke();
            }

            @Override
            public Rectangle2D getLogicalBounds() {
                return new Rectangle2D.Double(19.5, 9.5, 102, 52);
            }
        };
        OutputOptions options = OutputOptions.builder().boundsMode(BoundsMode.LOGICAL).build();

        OutputGeometry geometry = renderer.layout(scene, options);
        BufferedImage image = renderer.renderImage(scene, options);

        // Draw.io Graph.getSvg: dx = floor(border - bounds.x * scale) = floor(-19.5) = -20.
        assertEquals(-20.0, geometry.getOffsetX(), 0);
        assertEquals(-10.0, geometry.getOffsetY(), 0);
        assertEquals(102, geometry.getWidth());
        assertEquals(52, geometry.getHeight());
        // The stroke centered on x = 20.5 covers exactly device column 0.
        assertEquals(255, TestImages.alpha(image, 0, 30), "crisp stroke column");
        assertEquals(0, TestImages.alpha(image, 1, 30), "no smear into the next column");
        assertEquals(255, TestImages.alpha(image, 50, 0), "crisp top stroke row");
        assertEquals(0, TestImages.alpha(image, 50, 1));
    }

    @Test
    void fractionalScaledOriginFloorsAtOutputScale() {
        OutputGeometry geometry = renderer.layout(box(new Rectangle2D.Double(0.3, 0.3, 120, 60)),
                OutputOptions.builder().boundsMode(BoundsMode.LOGICAL).scale(1.5).padding(4).build());

        // floor(4 - 0.45) = 3
        assertEquals(3.0, geometry.getOffsetX(), 0);
        assertEquals(3.0, geometry.toDevice(0, 0).getX(), 1e-9);
        assertEquals(3.0 + 1.5 * 10, geometry.toDevice(10, 0).getX(), 1e-9);
    }

    @Test
    void fitWidthScalesProportionally() {
        OutputOptions options = OutputOptions.builder().width(218).padding(5).build();

        OutputGeometry geometry = renderer.layout(box(null), options);

        assertEquals(218, geometry.getWidth());
        assertEquals(2.0, geometry.getScale(), 1e-9);
        assertEquals(118, geometry.getHeight());
    }

    @Test
    void resolutionScalesAndSetsDpi() {
        OutputOptions options = OutputOptions.builder().resolution(192).build();

        assertEquals(2.0, options.getScale(), 1e-9);
        assertEquals(192.0, options.getDpi(), 1e-9);
        assertEquals(208, renderer.layout(box(null), options).getWidth());
    }

    @Test
    void transparentAndOpaqueBackgrounds() {
        OutputOptions transparent = OutputOptions.builder().padding(3).build();
        OutputOptions white = transparent.toBuilder().background(Color.WHITE).build();

        assertEquals(0, TestImages.alpha(renderer.renderImage(box(null), transparent), 0, 0));
        BufferedImage opaque = renderer.renderImage(box(null), white);
        assertEquals(0xffffffff, opaque.getRGB(0, 0));
    }

    @Test
    void enforcesDimensionAndPixelLimits() {
        OutputLimitException dimension = assertThrows(OutputLimitException.class,
                () -> renderer.renderImage(box(null),
                        OutputOptions.builder().maxDimension(100).build()));
        assertEquals(OutputLimitException.Limit.DIMENSION, dimension.getLimit());

        OutputLimitException pixels = assertThrows(OutputLimitException.class,
                () -> renderer.renderImage(box(null),
                        OutputOptions.builder().maxPixels(1000).build()));
        assertEquals(OutputLimitException.Limit.PIXELS, pixels.getLimit());

        OutputLimitException huge = assertThrows(OutputLimitException.class,
                () -> renderer.layout(box(null), OutputOptions.builder().scale(1000).build()));
        assertEquals(OutputLimitException.Limit.DIMENSION, huge.getLimit());
    }

    @Test
    void enforcesCommandLimitWhileRecording() {
        OutputLimitException exception = assertThrows(OutputLimitException.class,
                () -> renderer.renderImage(box(null),
                        OutputOptions.builder().maxCommands(3).build()));
        assertEquals(OutputLimitException.Limit.COMMANDS, exception.getLimit());
    }

    @Test
    void pngRoundTripsPixelsAndDpi() throws Exception {
        OutputOptions options = OutputOptions.builder().padding(2).dpi(300).build();

        byte[] png = renderer.renderPng(box(null), options);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        BufferedImage direct = renderer.renderImage(box(null), options);

        assertEquals(0, TestImages.countDifferentPixels(direct, decoded, 0));
        assertEquals(Math.round(300 / 0.0254), pixelsPerMeter(png));
    }

    @Test
    void svgIsLocalStandaloneDocumentWithViewBox() throws Exception {
        OutputOptions options = OutputOptions.builder().scale(2).padding(5).build();

        String svg = renderer.renderSvg(box(null), options);
        Document document = TestImages.parseSecure(svg);
        Element root = document.getDocumentElement();

        assertEquals("svg", root.getLocalName());
        assertEquals(SvgOutput.SVG_NAMESPACE, root.getNamespaceURI());
        assertEquals("218", root.getAttribute("width"));
        assertEquals("118", root.getAttribute("height"));
        assertEquals("0 0 218 118", root.getAttribute("viewBox"));
        assertFalse(svg.contains("<!DOCTYPE"), "no DOCTYPE");
        assertFalse(svg.contains("<!--"), "no generator comment");
        assertFalse(svg.contains("linearRGB"), "colors stay in sRGB");
        assertNoExternalReferences(document);
        assertTrue(document.getElementsByTagNameNS(SvgOutput.SVG_NAMESPACE, "rect").getLength()
                + document.getElementsByTagNameNS(SvgOutput.SVG_NAMESPACE, "path").getLength() > 0);
    }

    @Test
    void svgAndRasterShowTheSameScene() throws Exception {
        OutputOptions options = OutputOptions.builder().scale(1.5).padding(4)
                .background(Color.WHITE).build();
        DrawioScene scene = richScene();
        CanvasRecording recording = renderer.record(scene, options);

        BufferedImage raster = renderer.renderImage(recording, options);
        BufferedImage vector = TestImages.rasterize(renderer.renderSvg(recording, options));

        int differences = TestImages.countDifferentPixels(raster, vector, 48);
        int total = raster.getWidth() * raster.getHeight();
        assertTrue(differences < total / 1000,
                "Raster and SVG differ at " + differences + " of " + total + " pixels");
    }

    @Test
    void embeddedImagesBecomeLocalPngDataUrisInSvg() throws Exception {
        BufferedImage red = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = red.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(red, "png", bytes);
        String dataUri = "data:image/png;base64,"
                + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        DrawioScene scene = canvas -> canvas.image(0, 0, 16, 16, dataUri, false, false, false);

        String svg = renderer.renderSvg(scene, OutputOptions.defaults());
        Document document = TestImages.parseSecure(svg);
        NodeList images = document.getElementsByTagNameNS(SvgOutput.SVG_NAMESPACE, "image");
        BufferedImage vector = TestImages.rasterize(svg);
        BufferedImage raster = renderer.renderImage(scene, OutputOptions.defaults());

        assertEquals(1, images.getLength());
        String href = ((Element) images.item(0)).getAttributeNS(SvgOutput.XLINK_NAMESPACE, "href");
        assertTrue(href.startsWith("data:image/png;base64,"), href);
        assertNoExternalReferences(document);
        assertEquals(16, raster.getWidth());
        assertEquals(new Color(255, 0, 0), new Color(raster.getRGB(8, 8)));
        assertEquals(new Color(255, 0, 0), new Color(vector.getRGB(8, 8)));
    }

    /** Gradient box with shadow and an underlined bold label (canvas features from #10). */
    private static DrawioScene labelledGradientBox() {
        return canvas -> {
            canvas.setStrokeColor("#6c8ebf");
            canvas.setGradient("#dae8fc", "#7ea6e0", 10, 10, 160, 60, "south", 1, 1);
            canvas.setShadow(true);
            canvas.setShadowColor("#000000");
            canvas.setShadowAlpha(0.25);
            canvas.setShadowOffset(3, 3);
            canvas.roundrect(10, 10, 160, 60, 8, 8);
            canvas.fillAndStroke();
            canvas.setShadow(false);
            canvas.setFontColor("#000000");
            canvas.setFontSize(14);
            canvas.setFontFamily("Helvetica,Arial,sans-serif");
            canvas.setFontStyle(DrawioCanvas.FONT_BOLD | DrawioCanvas.FONT_UNDERLINE);
            canvas.text(90, 40, 150, 50, "Gateway \u00c4rger <&>", "center", "middle", true, "",
                    null, false, 0, null);
        };
    }

    @Test
    void gradientsAreRealSvgGradients() throws Exception {
        OutputOptions options = OutputOptions.builder().scale(2).background(Color.WHITE).build();
        DrawioScene scene = canvas -> {
            canvas.setGradient("#ff0000", "#0000ff", 0, 0, 100, 100, "south", 1, 1);
            canvas.rect(0, 0, 100, 100);
            canvas.fill();
        };

        String svg = renderer.renderSvg(scene, options);
        Document document = TestImages.parseSecure(svg);
        BufferedImage raster = renderer.renderImage(scene, options);
        BufferedImage vector = TestImages.rasterize(svg);

        assertEquals(1, document.getElementsByTagNameNS(SvgOutput.SVG_NAMESPACE,
                "linearGradient").getLength());
        assertNoExternalReferences(document);
        assertTrue(new Color(vector.getRGB(100, 5)).getRed() > 200, "top is red in SVG");
        assertTrue(new Color(vector.getRGB(100, 195)).getBlue() > 200, "bottom is blue in SVG");
        assertEquals(0, TestImages.countDifferentPixels(raster, vector, 8));
    }

    @Test
    void textShadowAndGradientLookTheSameInSvgOutlines() throws Exception {
        OutputOptions options = OutputOptions.builder().scale(2).padding(4)
                .background(Color.WHITE).svgTextAsShapes(true).build();
        CanvasRecording scene = renderer.record(labelledGradientBox(), options);

        String svg = renderer.renderSvg(scene, options);
        Document document = TestImages.parseSecure(svg);
        BufferedImage raster = renderer.renderImage(scene, options);
        BufferedImage vector = TestImages.rasterize(svg);

        assertEquals(0, document.getElementsByTagNameNS(SvgOutput.SVG_NAMESPACE, "text")
                .getLength());
        assertNoExternalReferences(document);
        // Glyph rasterization and outline filling differ only along glyph
        // edges (amount depends on the platform's fonts); compare the pixels
        // whose 3x3 neighborhood is uniform.
        int total = raster.getWidth() * raster.getHeight();
        int differences = TestImages.countDifferentStablePixels(raster, vector, 48);
        assertTrue(differences < total / 500, "differ at " + differences + " of " + total);
        assertEquals(darkPixelsInRows(raster, 0, raster.getHeight()),
                darkPixelsInRows(vector, 0, vector.getHeight()),
                darkPixelsInRows(raster, 0, raster.getHeight()) / 10.0,
                "same amount of ink incl. underline");
        // Shadow to the lower right stays inside the output and is visible in both.
        // The box ends at x=170.5, its shadow at 173.5 (device x = -15 + 2 * x).
        int shadowX = (int) Math.round(-15 + 2 * 172.0);
        Color rasterShadow = new Color(raster.getRGB(shadowX, raster.getHeight() / 2));
        Color vectorShadow = new Color(vector.getRGB(shadowX, vector.getHeight() / 2));
        assertTrue(rasterShadow.getRed() < 230, "raster shadow " + rasterShadow);
        assertTrue(vectorShadow.getRed() < 230, "SVG shadow " + vectorShadow);
    }

    @Test
    void defaultSvgTextIsSelectableWithCssGenericFamiliesAndEscaped() throws Exception {
        OutputOptions options = OutputOptions.defaults();

        String svg = renderer.renderSvg(labelledGradientBox(), options);
        Document document = TestImages.parseSecure(svg);
        NodeList texts = document.getElementsByTagNameNS(SvgOutput.SVG_NAMESPACE, "text");

        assertEquals(1, texts.getLength());
        assertEquals("Gateway \u00c4rger <&>", texts.item(0).getTextContent());
        assertFalse(svg.contains("'SansSerif'") || svg.contains("'Dialog'"),
                "Java logical font names are mapped to CSS generics");
        assertTrue(svg.contains("&lt;&amp;&gt;"));
    }

    private static int darkPixelsInRows(BufferedImage image, int from, int to) {
        int dark = 0;
        for (int y = from; y < to; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() + color.getGreen() + color.getBlue() < 150) {
                    dark++;
                }
            }
        }
        return dark;
    }

    @Test
    void transparencySurvivesBothFormats() throws Exception {
        DrawioScene scene = canvas -> {
            canvas.setFillColor("#0000ff");
            canvas.setAlpha(0.5);
            canvas.rect(0, 0, 20, 20);
            canvas.fill();
        };

        BufferedImage raster = renderer.renderImage(scene, OutputOptions.defaults());
        BufferedImage vector = TestImages.rasterize(renderer.renderSvg(scene,
                OutputOptions.defaults()));

        assertEquals(128, TestImages.alpha(raster, 10, 10), 2);
        assertEquals(128, TestImages.alpha(vector, 10, 10), 2);
    }

    @Test
    void svgSizeLimitStopsSerialization() {
        OutputLimitException exception = assertThrows(OutputLimitException.class,
                () -> renderer.renderSvg(box(null),
                        OutputOptions.builder().maxSvgChars(50).build()));
        assertEquals(OutputLimitException.Limit.SVG_SIZE, exception.getLimit());
    }

    @Test
    void sourceSceneIsPaintedOncePerCallAndRecordingNeverAgain() {
        AtomicInteger paints = new AtomicInteger();
        DrawioScene counting = canvas -> {
            paints.incrementAndGet();
            box(null).paint(canvas);
        };

        CanvasRecording recording = renderer.record(counting, OutputOptions.defaults());
        renderer.renderImage(recording, OutputOptions.defaults());
        renderer.renderSvg(recording, OutputOptions.defaults());
        renderer.renderPng(recording, OutputOptions.defaults());

        assertEquals(1, paints.get());
    }

    @Test
    void parallelRendersOfOneRecordingAreIdentical() throws Exception {
        OutputOptions options = OutputOptions.builder().scale(2).build();
        CanvasRecording recording = renderer.record(richScene(), options);
        BufferedImage expected = renderer.renderImage(recording, options);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<BufferedImage>> futures = new ArrayList<Future<BufferedImage>>();
            for (int index = 0; index < 8; index++) {
                futures.add(pool.submit((Callable<BufferedImage>)
                        () -> renderer.renderImage(recording, options)));
            }
            for (Future<BufferedImage> future : futures) {
                assertEquals(0, TestImages.countDifferentPixels(expected, future.get(), 0));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static DrawioScene richScene() {
        return canvas -> {
            canvas.setStrokeColor("#000000");
            canvas.setFillColor("#dae8fc");
            canvas.setStrokeWidth(2);
            canvas.roundrect(10, 10, 120, 60, 10, 10);
            canvas.fillAndStroke();

            canvas.save();
            canvas.setFillColor("#ffe6cc");
            canvas.rotate(30, false, false, 220, 40);
            canvas.ellipse(180, 15, 80, 50);
            canvas.fillAndStroke();
            canvas.restore();

            canvas.setDashed(true);
            canvas.setDashPattern("6 3");
            canvas.begin();
            canvas.moveTo(130, 40);
            canvas.curveTo(150, 90, 170, 90, 190, 60);
            canvas.stroke();

            canvas.setDashed(false);
            canvas.setFillColor("#000000");
            canvas.begin();
            canvas.moveTo(190, 60);
            canvas.lineTo(180, 58);
            canvas.lineTo(186, 68);
            canvas.closePath();
            canvas.fillAndStroke();
        };
    }

    private static long pixelsPerMeter(byte[] png) throws Exception {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
            try {
                reader.setInput(input);
                IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(0)
                        .getAsTree("javax_imageio_png_1.0");
                Element physical = (Element) root.getElementsByTagName("pHYs").item(0);
                assertEquals("meter", physical.getAttribute("unitSpecifier"));
                return Long.parseLong(physical.getAttribute("pixelsPerUnitXAxis"));
            } finally {
                reader.dispose();
            }
        }
    }

    static void assertNoExternalReferences(Document document) {
        NodeList all = document.getElementsByTagNameNS("*", "*");
        for (int index = 0; index < all.getLength(); index++) {
            NamedNodeMap attributes = all.item(index).getAttributes();
            for (int attribute = 0; attribute < attributes.getLength(); attribute++) {
                Node node = attributes.item(attribute);
                String name = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
                String value = node.getNodeValue().trim();
                if ("href".equals(name)) {
                    assertTrue(value.startsWith("#") || value.startsWith("data:"),
                            "External href: " + value);
                }
                int url = value.indexOf("url(");
                while (url >= 0) {
                    String target = value.substring(url + 4).replace("'", "").replace("\"", "").trim();
                    assertTrue(target.startsWith("#"), "External url(): " + value);
                    url = value.indexOf("url(", url + 4);
                }
            }
        }
    }

    private static void assertRect(double x, double y, double width, double height,
                                   Rectangle2D actual) {
        assertEquals(x, actual.getX(), 1e-6, "x");
        assertEquals(y, actual.getY(), 1e-6, "y");
        assertEquals(width, actual.getWidth(), 1e-6, "width");
        assertEquals(height, actual.getHeight(), 1e-6, "height");
    }
}
