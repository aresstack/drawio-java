package com.aresstack.drawio.render;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Describe how scene coordinates map onto the output image.
 *
 * <p>{@code device = padding + scale * (diagram - sourceOrigin)}. Raster and
 * SVG output use the identical mapping; SVG user units equal output pixels
 * and its {@code viewBox} is {@code 0 0 width height}.
 */
public final class OutputGeometry {

    private final Rectangle2D sourceBounds;
    private final double scale;
    private final int padding;
    private final int width;
    private final int height;

    OutputGeometry(Rectangle2D sourceBounds, double scale, int padding, int width, int height) {
        this.sourceBounds = (Rectangle2D) sourceBounds.clone();
        this.scale = scale;
        this.padding = padding;
        this.width = width;
        this.height = height;
    }

    /** Area of the scene in diagram units that is rendered. */
    public Rectangle2D getSourceBounds() {
        return (Rectangle2D) sourceBounds.clone();
    }

    public double getScale() {
        return scale;
    }

    public int getPadding() {
        return padding;
    }

    /** Output width in pixels. */
    public int getWidth() {
        return width;
    }

    /** Output height in pixels. */
    public int getHeight() {
        return height;
    }

    /** Return the diagram-to-output transform. */
    public AffineTransform toDeviceTransform() {
        AffineTransform transform = AffineTransform.getTranslateInstance(padding, padding);
        transform.scale(scale, scale);
        transform.translate(-sourceBounds.getX(), -sourceBounds.getY());
        return transform;
    }

    /** Map a diagram point to output pixel coordinates. */
    public Point2D toDevice(double x, double y) {
        return toDeviceTransform().transform(new Point2D.Double(x, y), null);
    }

    @Override
    public String toString() {
        return "OutputGeometry[" + width + "x" + height + ", scale=" + scale
                + ", padding=" + padding + ", source=" + sourceBounds + "]";
    }
}
