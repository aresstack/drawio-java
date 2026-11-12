package com.aresstack.drawio.canvas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Java2DCanvasTest {

    @Test
    void fillsRectangleWithoutPaintingOutsideItsBounds() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setFillColor("#f00");
            fixture.canvas.rect(10, 10, 20, 20);
            fixture.canvas.fill();

            assertEquals(Color.RED.getRGB(), fixture.pixel(15, 15));
            assertEquals(0, fixture.pixel(5, 5));
        }
    }

    @Test
    void doesNotFillWhenOnlyTheStrokeColorIsSpecified() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setStrokeColor("#ff0000");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.rect(10, 10, 30, 30);
            fixture.canvas.fillAndStroke();

            assertEquals(0, fixture.pixel(20, 20));
            assertEquals(Color.RED.getRGB(), fixture.pixel(10, 20));
        }
    }

    @Test
    void fillsAndStrokesTheSameShape() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.setStrokeColor("#000000");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.rect(10, 10, 30, 30);
            fixture.canvas.fillAndStroke();

            assertEquals(Color.RED.getRGB(), fixture.pixel(20, 20));
            assertEquals(Color.BLACK.getRGB(), fixture.pixel(10, 20));
        }
    }

    @Test
    void restoresNestedTransformAndColorState() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.save();
            fixture.canvas.translate(25, 0);
            fixture.canvas.setFillColor("#0000ff");
            fixture.canvas.rect(0, 0, 10, 10);
            fixture.canvas.fill();
            fixture.canvas.restore();
            fixture.canvas.rect(0, 0, 10, 10);
            fixture.canvas.fill();

            assertEquals(Color.RED.getRGB(), fixture.pixel(5, 5));
            assertEquals(Color.BLUE.getRGB(), fixture.pixel(30, 5));
            assertEquals(0, fixture.pixel(15, 5));
        }
    }

    @Test
    void appliesScalingToPreviouslyEstablishedTranslation() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.translate(10, 0);
            fixture.canvas.scale(2);
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.rect(0, 10, 5, 5);
            fixture.canvas.fill();

            assertEquals(Color.RED.getRGB(), fixture.pixel(21, 21));
            assertEquals(0, fixture.pixel(11, 21));
        }
    }

    @Test
    void appliesTranslationRegardlessOfScaleCallOrder() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.scale(2);
            fixture.canvas.translate(10, 0);
            fixture.canvas.setFillColor("#0000ff");
            fixture.canvas.rect(0, 10, 5, 5);
            fixture.canvas.fill();

            assertEquals(Color.BLUE.getRGB(), fixture.pixel(21, 21));
            assertEquals(0, fixture.pixel(11, 21));
        }
    }

    @Test
    void supportsQuadraticAndCubicPaths() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setStrokeColor("#ff0000");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.begin();
            fixture.canvas.moveTo(5, 40);
            fixture.canvas.quadTo(25, 5, 40, 40);
            fixture.canvas.curveTo(50, 60, 55, 10, 65, 40);
            fixture.canvas.stroke();

            assertTrue(fixture.countVisiblePixels() > 30);
        }
    }

    @Test
    void recordsTranslationWhenEachPathPointIsAdded() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setStrokeColor("#ff0000");
            fixture.canvas.begin();
            fixture.canvas.moveTo(5, 20);
            fixture.canvas.translate(20, 0);
            fixture.canvas.lineTo(15, 20);
            fixture.canvas.translate(-20, 0);
            fixture.canvas.stroke();

            assertEquals(Color.RED.getRGB(), fixture.pixel(30, 20));
            assertEquals(0, fixture.pixel(40, 20));
        }
    }

    @Test
    void closesPathsBeforeFillingThem() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.begin();
            fixture.canvas.moveTo(10, 10);
            fixture.canvas.lineTo(40, 10);
            fixture.canvas.lineTo(25, 40);
            fixture.canvas.closePath();
            fixture.canvas.setFillColor("#0000ff");
            fixture.canvas.fill();

            assertEquals(Color.BLUE.getRGB(), fixture.pixel(25, 20));
            assertEquals(0, fixture.pixel(5, 5));
        }
    }

    @Test
    void restoresClipAfterSavedDrawingState() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.save();
            fixture.canvas.rect(0, 0, 20, 20);
            fixture.canvas.clip();
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.rect(0, 0, 60, 60);
            fixture.canvas.fill();
            fixture.canvas.restore();

            fixture.canvas.setFillColor("#0000ff");
            fixture.canvas.rect(25, 25, 10, 10);
            fixture.canvas.fill();

            assertEquals(Color.RED.getRGB(), fixture.pixel(10, 10));
            assertEquals(0, fixture.pixel(23, 23));
            assertEquals(Color.BLUE.getRGB(), fixture.pixel(30, 30));
        }
    }

    @Test
    void multipliesCanvasAndFillOpacity() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setAlpha(0.5);
            fixture.canvas.setFillAlpha(0.5);
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.rect(0, 0, 30, 30);
            fixture.canvas.fill();

            int alpha = (fixture.pixel(10, 10) >>> 24) & 0xff;
            assertTrue(alpha >= 63 && alpha <= 65);
        }
    }

    @Test
    void drawsRoundedRectanglesAndEllipses() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.roundrect(0, 0, 30, 30, 12, 12);
            fixture.canvas.fill();
            fixture.canvas.setFillColor("#0000ff");
            fixture.canvas.ellipse(40, 0, 30, 30);
            fixture.canvas.fill();

            assertEquals(0, fixture.pixel(0, 0));
            assertEquals(Color.RED.getRGB(), fixture.pixel(15, 15));
            assertEquals(0, fixture.pixel(40, 0));
            assertEquals(Color.BLUE.getRGB(), fixture.pixel(55, 15));
        }
    }

    @Test
    void rotatesAndFlipsAroundSpecifiedCenter() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.rotate(90, false, false, 30, 30);
            fixture.canvas.setFillColor("#ff0000");
            fixture.canvas.rect(30, 10, 10, 10);
            fixture.canvas.fill();

            assertEquals(Color.RED.getRGB(), fixture.pixel(45, 35));
            assertEquals(0, fixture.pixel(35, 15));
        }
    }

    @Test
    void flipsShapesHorizontallyAroundSpecifiedCenter() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.rotate(0, true, false, 30, 30);
            fixture.canvas.setFillColor("#0000ff");
            fixture.canvas.rect(10, 20, 10, 10);
            fixture.canvas.fill();

            assertEquals(Color.BLUE.getRGB(), fixture.pixel(45, 25));
            assertEquals(0, fixture.pixel(15, 25));
        }
    }

    @Test
    void appliesSeparateStrokeAlphaWithoutChangingFillAlpha() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setStrokeColor("#ff0000");
            fixture.canvas.setStrokeAlpha(0.25);
            fixture.canvas.setStrokeWidth(4);
            fixture.canvas.begin();
            fixture.canvas.moveTo(10, 20);
            fixture.canvas.lineTo(40, 20);
            fixture.canvas.stroke();

            int alpha = (fixture.pixel(25, 20) >>> 24) & 0xff;
            assertTrue(alpha >= 63 && alpha <= 65);
        }
    }

    @Test
    void drawsDashedStrokesWithExplicitCapsAndJoins() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setStrokeColor("#0000ff");
            fixture.canvas.setDashed(true);
            fixture.canvas.setDashPattern("4 3");
            fixture.canvas.setLineCap("round");
            fixture.canvas.setLineJoin("bevel");
            fixture.canvas.setStrokeWidth(2);
            fixture.canvas.begin();
            fixture.canvas.moveTo(5, 15);
            fixture.canvas.lineTo(70, 15);
            fixture.canvas.stroke();

            assertTrue(fixture.countVisiblePixels() > 8);
            assertNotEquals(Color.BLUE.getRGB(), fixture.pixel(5, 60));
        }
    }

    @Test
    void scalesDashLengthsWithStrokeWidth() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setStrokeColor("#0000ff");
            fixture.canvas.setStrokeWidth(4);
            fixture.canvas.setDashed(true);
            fixture.canvas.setDashPattern("3 3");
            fixture.canvas.begin();
            fixture.canvas.moveTo(10, 20);
            fixture.canvas.lineTo(80, 20);
            fixture.canvas.stroke();

            assertEquals(Color.BLUE.getRGB(), fixture.pixel(18, 20));
            assertEquals(0, fixture.pixel(26, 20));
            assertEquals(Color.BLUE.getRGB(), fixture.pixel(36, 20));
        }
    }

    @Test
    void mapsNamedGreenToItsCssColorValue() {
        try (ImageFixture fixture = new ImageFixture()) {
            fixture.canvas.setFillColor("green");
            fixture.canvas.rect(0, 0, 10, 10);
            fixture.canvas.fill();
            fixture.canvas.setFillColor("#008000");
            fixture.canvas.rect(20, 0, 10, 10);
            fixture.canvas.fill();

            assertEquals(new Color(0x008000).getRGB(), fixture.pixel(5, 5));
            assertEquals(fixture.pixel(5, 5), fixture.pixel(25, 5));
        }
    }

    @Test
    void refusesInvalidGeometryAndUnknownStyles() {
        try (ImageFixture fixture = new ImageFixture()) {
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.canvas.setFillColor("unrecognized-shape-color"));
            assertThrows(IllegalArgumentException.class, () -> fixture.canvas.scale(0));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.canvas.setAlpha(2));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.canvas.rect(0, 0, Double.NaN, 20));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.canvas.setDashPattern("2 -1"));
            assertThrows(IllegalStateException.class, fixture.canvas::restore);
        }
    }

    @Test
    void refusesExternalImageReferences() {
        try (ImageFixture fixture = new ImageFixture()) {
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.canvas.image(0, 0, 10, 10, "https://example.invalid/a.png",
                            true, false, false));
        }
    }

    @Test
    void rejectsOperationsAfterDisposal() {
        ImageFixture fixture = new ImageFixture();
        fixture.close();

        assertThrows(IllegalStateException.class, fixture.canvas::begin);
        assertThrows(IllegalStateException.class, fixture.canvas::save);
    }

    private static final class ImageFixture implements AutoCloseable {
        private final BufferedImage image = new BufferedImage(90, 90, BufferedImage.TYPE_INT_ARGB);
        private final Graphics2D graphics = image.createGraphics();
        private final Java2DCanvas canvas = new Java2DCanvas(graphics);

        int pixel(int x, int y) {
            return image.getRGB(x, y);
        }

        int countVisiblePixels() {
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (((pixel(x, y) >>> 24) & 0xff) > 0) {
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
