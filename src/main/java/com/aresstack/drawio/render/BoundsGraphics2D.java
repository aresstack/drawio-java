package com.aresstack.drawio.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;

import org.apache.batik.ext.awt.g2d.AbstractGraphics2D;
import org.apache.batik.ext.awt.g2d.GraphicContext;

/**
 * Measure the device-space area touched by Java2D drawing operations.
 *
 * <p>Nothing is rasterized. Every fill, stroke (widened by its actual
 * {@link java.awt.Stroke}), text run and image contributes its transformed
 * bounds, clipped to the current clip. Child graphics created with
 * {@link #create()} share the same accumulator, matching how
 * {@code Java2DCanvas} saves and restores state.
 */
final class BoundsGraphics2D extends AbstractGraphics2D {

    private static final Graphics2D METRICS;

    static {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        METRICS = image.createGraphics();
    }

    private final Accumulator accumulator;

    BoundsGraphics2D() {
        super(false);
        this.gc = new GraphicContext();
        this.accumulator = new Accumulator();
    }

    private BoundsGraphics2D(BoundsGraphics2D source) {
        super(source);
        this.accumulator = source.accumulator;
    }

    /**
     * Return the union of all painted areas in device space, or {@code null}
     * if nothing visible was painted.
     */
    Rectangle2D getPaintedBounds() {
        return accumulator.bounds == null ? null : (Rectangle2D) accumulator.bounds.clone();
    }

    @Override
    public Graphics create() {
        return new BoundsGraphics2D(this);
    }

    @Override
    public void dispose() {
        // Nothing to release.
    }

    @Override
    public void draw(Shape shape) {
        addUserShape(getStroke().createStrokedShape(shape));
    }

    @Override
    public void fill(Shape shape) {
        addUserShape(shape);
    }

    @Override
    public void drawString(String text, float x, float y) {
        if (text == null || text.isEmpty()) {
            return;
        }
        TextLayout layout = new TextLayout(text, getFont(), getFontRenderContext());
        addUserShape(layout.getOutline(AffineTransform.getTranslateInstance(x, y)));
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        if (iterator.getEndIndex() <= iterator.getBeginIndex()) {
            return;
        }
        TextLayout layout = new TextLayout(iterator, getFontRenderContext());
        addUserShape(layout.getOutline(AffineTransform.getTranslateInstance(x, y)));
    }

    @Override
    public boolean drawImage(Image image, int x, int y, ImageObserver observer) {
        return drawImage(image, x, y, image.getWidth(observer), image.getHeight(observer), observer);
    }

    @Override
    public boolean drawImage(Image image, int x, int y, int width, int height,
                             ImageObserver observer) {
        if (width > 0 && height > 0) {
            addUserShape(new Rectangle2D.Double(x, y, width, height));
        }
        return true;
    }

    @Override
    public void drawRenderedImage(RenderedImage image, AffineTransform transform) {
        Shape area = new Rectangle2D.Double(image.getMinX(), image.getMinY(),
                image.getWidth(), image.getHeight());
        addUserShape(transform == null ? area : transform.createTransformedShape(area));
    }

    @Override
    public void drawRenderableImage(RenderableImage image, AffineTransform transform) {
        Shape area = new Rectangle2D.Double(image.getMinX(), image.getMinY(),
                image.getWidth(), image.getHeight());
        addUserShape(transform == null ? area : transform.createTransformedShape(area));
    }

    @Override
    public GraphicsConfiguration getDeviceConfiguration() {
        return METRICS.getDeviceConfiguration();
    }

    @Override
    public FontMetrics getFontMetrics(Font font) {
        synchronized (METRICS) {
            return METRICS.getFontMetrics(font);
        }
    }

    @Override
    public void setXORMode(Color color) {
        // XOR painting is never used by Draw.io canvases; ignore.
    }

    @Override
    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        throw new UnsupportedOperationException("copyArea is not supported when measuring bounds");
    }

    private void addUserShape(Shape userShape) {
        Rectangle2D device = getTransform().createTransformedShape(userShape).getBounds2D();
        Shape clip = getClip();
        if (clip != null) {
            Rectangle2D deviceClip = getTransform().createTransformedShape(clip).getBounds2D();
            device = device.createIntersection(deviceClip);
        }
        // Zero-area geometry paints nothing. Stroking a path made only of
        // moveTo segments (for example an empty table line in Shapes.js)
        // yields an empty shape whose bounds sit at the origin.
        if (device.isEmpty()) {
            return;
        }
        if (!isFinite(device)) {
            throw new IllegalArgumentException("Painted geometry is not finite");
        }
        accumulator.add(device);
    }

    private static boolean isFinite(Rectangle2D rectangle) {
        return Double.isFinite(rectangle.getX()) && Double.isFinite(rectangle.getY())
                && Double.isFinite(rectangle.getWidth()) && Double.isFinite(rectangle.getHeight());
    }

    private static final class Accumulator {
        private Rectangle2D bounds;

        private void add(Rectangle2D area) {
            if (bounds == null) {
                bounds = new Rectangle2D.Double(area.getX(), area.getY(),
                        area.getWidth(), area.getHeight());
            } else {
                bounds.add(area);
            }
        }
    }
}
