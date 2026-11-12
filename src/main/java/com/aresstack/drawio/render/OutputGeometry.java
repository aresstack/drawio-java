package com.aresstack.drawio.render;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Describe how scene coordinates map onto the output image.
 *
 * <p>{@code device = offset + scale * diagram}, where the offset is
 * {@code floor(padding - scale * sourceOrigin)} on each axis. Flooring at the
 * output scale follows Draw.io's export ({@code Graph.getSvg}, jgraph/drawio#4938):
 * content moves by whole output pixels, so strokes that are crisp in the scene
 * stay crisp, and a fractional origin clips less than one pixel. Raster and
 * SVG output use the identical mapping; SVG user units equal output pixels
 * and its {@code viewBox} is {@code 0 0 width height}.
 */
public final class OutputGeometry {

    private final Rectangle2D sourceBounds;
    private final double scale;
    private final int padding;
    private final int width;
    private final int height;
    private final double offsetX;
    private final double offsetY;

    OutputGeometry(Rectangle2D sourceBounds, double scale, int padding, int width, int height) {
        this.sourceBounds = (Rectangle2D) sourceBounds.clone();
        this.scale = scale;
        this.padding = padding;
        this.width = width;
        this.height = height;
        this.offsetX = pixelOffset(padding, sourceBounds.getX(), scale);
        this.offsetY = pixelOffset(padding, sourceBounds.getY(), scale);
    }

    /**
     * Floor {@code padding - scale * origin} to whole pixels after removing
     * floating-point noise, as Draw.io's {@code Graph.getSvg} does.
     */
    private static double pixelOffset(int padding, double origin, double scale) {
        double exact = padding - origin * scale;
        return Math.floor(Math.round(exact * 1e6) / 1e6);
    }

    /** Horizontal device offset in whole pixels. */
    public double getOffsetX() {
        return offsetX;
    }

    /** Vertical device offset in whole pixels. */
    public double getOffsetY() {
        return offsetY;
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
        AffineTransform transform = AffineTransform.getTranslateInstance(offsetX, offsetY);
        transform.scale(scale, scale);
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
