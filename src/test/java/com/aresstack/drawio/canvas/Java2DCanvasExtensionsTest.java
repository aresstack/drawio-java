package com.aresstack.drawio.canvas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the canvas features added for issue #10 against mxSvgCanvas2D semantics.
 */
class Java2DCanvasExtensionsTest {

    @Test
    void paintsSouthGradientAcrossPathBounds() {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.setGradient("#ff0000", "#0000ff", 0, 0, 0, 0, null, 1, 1);
            fixture.canvas.rect(10, 10, 80, 80);
            fixture.canvas.fill();

            Color top = fixture.color(50, 11);
            Color bottom = fixture.color(50, 88);
            assertTrue(top.getRed() > 240 && top.getBlue() < 15, top.toString());
            assertTrue(bottom.getBlue() > 240 && bottom.getRed() < 15, bottom.toString());
            Color middle = fixture.color(50, 50);
            assertTrue(Math.abs(middle.getRed() - middle.getBlue()) < 20, middle.toString());
        }
    }

    @Test
    void mapsGradientDirectionsLikeTheSvgCanvas() {
        assertGradient("east", 11, 50, 88, 50);
        assertGradient("west", 88, 50, 11, 50);
        assertGradient("north", 50, 88, 50, 11);
        assertGradient("radial", 50, 50, 50, 11);
    }

    @Test
    void rotatesGradientWithTheShape() {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.rotate(90, false, false, 50, 50);
            fixture.canvas.setGradient("#ff0000", "#0000ff", 0, 0, 0, 0, "south", 1, 1);
            fixture.canvas.rect(10, 10, 80, 80);
            fixture.canvas.fill();

            // The unrotated top edge (red) is now on the right after a 90 degree turn.
            assertTrue(fixture.color(88, 50).getRed() > 240);
            assertTrue(fixture.color(11, 50).getBlue() > 240);
        }
    }

    @Test
    void appliesGradientStopAlphasAndClearsGradientOnSolidFill() {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.setGradient("#ff0000", "#0000ff", 0, 0, 0, 0, "south", 1, 0);
            fixture.canvas.rect(10, 10, 80, 80);
            fixture.canvas.fill();
            assertTrue(fixture.alpha(50, 88) < 15);

            fixture.canvas.setFillColor("#00ff00");
            fixture.canvas.rect(0, 0, 5, 5);
            fixture.canvas.fill();
            assertEquals(Color.GREEN.getRGB(), fixture.image.getRGB(2, 2));
        }
    }

    @Test
    void paintsShadowBehindFilledShapeWithScaledOffset() {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.scale(2);
            fixture.canvas.setShadow(true);
            fixture.canvas.setShadowColor("#000000");
            fixture.canvas.setShadowAlpha(1);
            fixture.canvas.setShadowOffset(2, 3);
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.rect(10, 10, 20, 20);
            fixture.canvas.fill();

            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(58, 58));
            // Offset (2,3) * scale 2 = (4,6) device pixels beyond the shape.
            assertEquals(Color.BLACK.getRGB(), fixture.image.getRGB(62, 64));
            assertEquals(0, fixture.image.getRGB(62, 18));
        }
    }

    @Test
    void doesNotPaintStrokeShadowOverTheFill() {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.setShadow(true);
            fixture.canvas.setShadowColor("#000000");
            fixture.canvas.setShadowOffset(5, 5);
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.setStrokeColor("#0000ff");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.rect(10, 10, 40, 40);
            fixture.canvas.fillAndStroke();

            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(15, 15));
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(30, 30));
            assertEquals(Color.BLACK.getRGB(), fixture.image.getRGB(53, 53));
        }
    }

    @Test
    void scalesStrokeWidthLikeUpstreamCanvasState() {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.setStrokeColor("#ff0000");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.scale(2);
            fixture.canvas.begin();
            fixture.canvas.moveTo(5, 25);
            fixture.canvas.lineTo(45, 25);
            fixture.canvas.stroke();

            // Width 2 scaled into the state (4) and painted at scale 2 = 8 px.
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(40, 53));
            assertEquals(0, fixture.image.getRGB(40, 55));
        }
    }

    @Test
    void keepsDashLengthsIndependentOfStrokeWidthWithFixDash() {
        try (Fixture fixture = new Fixture(100, 40)) {
            fixture.canvas.setStrokeColor("#0000ff");
            fixture.canvas.setStrokeWidth(4);
            fixture.canvas.setDashed(true);
            fixture.canvas.setFixDash(true);
            fixture.canvas.setDashPattern("6 6");
            fixture.canvas.begin();
            fixture.canvas.moveTo(10, 20);
            fixture.canvas.lineTo(90, 20);
            fixture.canvas.stroke();

            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(13, 20));
            assertEquals(0, fixture.image.getRGB(19, 20));
            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(25, 20));
        }
    }

    @Test
    void ignoresNonNumericDashTokensAndPaintsSolidLine() {
        try (Fixture fixture = new Fixture(100, 40)) {
            fixture.canvas.setStrokeColor("#0000ff");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.setDashed(true);
            fixture.canvas.setDashPattern("none");
            fixture.canvas.begin();
            fixture.canvas.moveTo(10, 20);
            fixture.canvas.lineTo(90, 20);
            fixture.canvas.stroke();

            for (int x = 12; x < 88; x += 3) {
                assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(x, 20), "x=" + x);
            }
        }
    }

    @Test
    void positionsCenteredPlainTextAroundTheAnchor() {
        try (Fixture fixture = new Fixture(200, 100)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(20);
            fixture.canvas.text(100, 50, 0, 0, "WWWW", "center", "middle",
                    false, "", null, false, 0, null);

            int[] box = fixture.inkBounds();
            double centerX = (box[0] + box[2]) / 2.0;
            double centerY = (box[1] + box[3]) / 2.0;
            assertTrue(Math.abs(centerX - 100) <= 3, "center x " + centerX);
            assertTrue(Math.abs(centerY - 50) <= 5, "center y " + centerY);
            assertTrue(box[2] - box[0] > 40, "text width");
        }
    }

    @Test
    void endsRightAlignedTextAtTheAnchor() {
        try (Fixture fixture = new Fixture(200, 100)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(16);
            fixture.canvas.text(150, 10, 0, 0, "Right", "right", "top",
                    false, "", null, false, 0, null);

            int[] box = fixture.inkBounds();
            assertTrue(box[2] <= 151 && box[2] >= 140, "right edge " + box[2]);
            assertTrue(box[1] >= 9, "top " + box[1]);
        }
    }

    @Test
    void breaksHtmlLabelsAtLineBreakTags() {
        int single;
        int twoLines;
        try (Fixture fixture = new Fixture(200, 100)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(14);
            fixture.canvas.text(100, 10, 120, 0, "Task", "center", "top",
                    true, "html", null, false, 0, null);
            int[] box = fixture.inkBounds();
            single = box[3] - box[1];
        }
        try (Fixture fixture = new Fixture(200, 100)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(14);
            fixture.canvas.text(100, 10, 120, 0, "Task<br>Name &amp; more", "center", "top",
                    true, "html", null, false, 0, null);
            int[] box = fixture.inkBounds();
            twoLines = box[3] - box[1];
        }
        assertTrue(twoLines > single + 10, single + " vs " + twoLines);
    }

    @Test
    void wrapsHtmlLabelsAtTheBoxWidth() {
        try (Fixture fixture = new Fixture(200, 200)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(12);
            fixture.canvas.text(100, 10, 60, 0, "alpha beta gamma delta epsilon", "center",
                    "top", true, "html", null, false, 0, null);

            int[] box = fixture.inkBounds();
            assertTrue(box[2] - box[0] <= 64, "wrapped width " + (box[2] - box[0]));
            assertTrue(box[3] - box[1] > 30, "several lines");
        }
    }

    @Test
    void rotatesTextAroundTheAnchor() {
        try (Fixture fixture = new Fixture(200, 200)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(16);
            fixture.canvas.text(100, 100, 0, 0, "Rotated label", "center", "middle",
                    false, "", null, false, 90, null);

            int[] box = fixture.inkBounds();
            assertTrue(box[3] - box[1] > box[2] - box[0], "text runs vertically");
        }
    }

    @Test
    void clipsTextToTheLabelBox() {
        try (Fixture fixture = new Fixture(200, 100)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontSize(16);
            fixture.canvas.text(100, 50, 40, 20, "A very long clipped label", "center", "middle",
                    false, "", null, true, 0, null);

            int[] box = fixture.inkBounds();
            assertTrue(box[0] >= 77 && box[2] <= 123, box[0] + ".." + box[2]);
        }
    }

    @Test
    void paintsTextBackgroundBehindTheLabel() {
        try (Fixture fixture = new Fixture(200, 100)) {
            fixture.canvas.setFontColor("#000000");
            fixture.canvas.setFontBackgroundColor("#ffff00");
            fixture.canvas.setFontSize(16);
            fixture.canvas.text(100, 50, 0, 0, "Label", "center", "middle",
                    false, "", null, false, 0, null);

            assertTrue(fixture.countColor(Color.YELLOW.getRGB()) > 100);
        }
    }

    @Test
    void drawsEmbeddedImagesScaledAndFlipped() throws IOException {
        String source = twoColorImage();
        try (Fixture fixture = new Fixture(60, 60)) {
            fixture.canvas.image(10, 10, 40, 20, source, false, false, false);
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(15, 20));
            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(45, 20));
        }
        try (Fixture fixture = new Fixture(60, 60)) {
            fixture.canvas.image(10, 10, 40, 20, source, false, true, false);
            assertEquals(Color.BLUE.getRGB(), fixture.image.getRGB(15, 20));
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(45, 20));
        }
    }

    @Test
    void preservesImageAspectRatioByCentering() throws IOException {
        try (Fixture fixture = new Fixture(60, 60)) {
            // 2x1 image into a 40x40 box: painted 40x20 centered vertically.
            fixture.canvas.image(10, 10, 40, 40, twoColorImage(), true, false, false);
            assertEquals(0, fixture.image.getRGB(15, 15));
            assertEquals(Color.RED.getRGB(), fixture.image.getRGB(15, 30));
            assertEquals(0, fixture.image.getRGB(15, 45));
        }
    }

    @Test
    void enforcesImageLimitsAndRejectsSvg() throws IOException {
        String source = twoColorImage();
        BufferedImage target = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try (Java2DCanvas limited = new Java2DCanvas(graphics, 1024, 1)) {
            assertThrows(IllegalArgumentException.class,
                    () -> limited.image(0, 0, 5, 5, source, true, false, false));
            assertThrows(IllegalArgumentException.class,
                    () -> limited.image(0, 0, 5, 5,
                            "data:image/svg+xml;base64,PHN2Zy8+", true, false, false));
            assertThrows(IllegalArgumentException.class,
                    () -> limited.image(0, 0, 5, 5, "data:text/plain;base64,QQ==", true, false, false));
        } finally {
            graphics.dispose();
        }
        assertNull(ImageSourcePolicy.dataUris(1024, 100).resolve("https://example.invalid/x.png"));
        assertThrows(IllegalArgumentException.class,
                () -> ImageSourcePolicy.dataUris(10, 100).resolve(source));
    }

    @Test
    void parsesCssColorSyntaxes() {
        assertEquals(new Color(255, 0, 0, 128), CanvasColors.parse("rgba(255, 0, 0, 0.5)"));
        assertEquals(new Color(0, 128, 255), CanvasColors.parse("rgb(0 128 255)"));
        assertEquals(new Color(0x11, 0x22, 0x33, 0x44), CanvasColors.parse("#11223344"));
        assertEquals(new Color(0xaa, 0xbb, 0xcc, 0xdd), CanvasColors.parse("#abcd"));
        assertEquals(new Color(0xdae8fc), CanvasColors.parse("light-dark(#DAE8FC, #1d293b)"));
        assertEquals(new Color(0x6495ed), CanvasColors.parse("CornflowerBlue"));
        assertNull(CanvasColors.parse("none"));
        assertThrows(IllegalArgumentException.class, () -> CanvasColors.parse("url(#x)"));
        assertThrows(IllegalArgumentException.class, () -> CanvasColors.parse("#12345"));
    }

    private static void assertGradient(String direction, int startX, int startY, int endX, int endY) {
        try (Fixture fixture = new Fixture(100, 100)) {
            fixture.canvas.setGradient("#ff0000", "#0000ff", 0, 0, 0, 0, direction, 1, 1);
            fixture.canvas.rect(10, 10, 80, 80);
            fixture.canvas.fill();

            Color start = fixture.color(startX, startY);
            Color end = fixture.color(endX, endY);
            assertTrue(start.getRed() > 220, direction + " start " + start);
            assertTrue(end.getBlue() > 150, direction + " end " + end);
        }
    }

    private static String twoColorImage() throws IOException {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static final class Fixture implements AutoCloseable {
        private final BufferedImage image;
        private final Graphics2D graphics;
        private final Java2DCanvas canvas;

        private Fixture(int width, int height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            graphics = image.createGraphics();
            canvas = new Java2DCanvas(graphics);
        }

        private Color color(int x, int y) {
            return new Color(image.getRGB(x, y), true);
        }

        private int alpha(int x, int y) {
            return image.getRGB(x, y) >>> 24;
        }

        private int countColor(int rgb) {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (image.getRGB(x, y) == rgb) {
                        count++;
                    }
                }
            }
            return count;
        }

        /** Return {minX, minY, maxX, maxY} of pixels with visible ink. */
        private int[] inkBounds() {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > 64) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            assertTrue(maxX >= 0, "no ink painted");
            return new int[] {minX, minY, maxX, maxY};
        }

        @Override
        public void close() {
            canvas.close();
            graphics.dispose();
        }
    }
}
