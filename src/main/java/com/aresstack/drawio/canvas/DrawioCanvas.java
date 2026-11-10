package com.aresstack.drawio.canvas;

/**
 * Express the Draw.io canvas drawing language without depending on GraalJS.
 *
 * <p>Close the current path with {@link #closePath()} and release resources
 * with {@link #close()}. The JavaScript adapter will map Draw.io's path
 * operation named "close" to closePath.
 */
public interface DrawioCanvas extends AutoCloseable {

    void save();

    void restore();

    void translate(double dx, double dy);

    void scale(double factor);

    void rotate(double angleDegrees, boolean flipHorizontal, boolean flipVertical,
                double centerX, double centerY);

    void setAlpha(double alpha);

    void setFillAlpha(double alpha);

    void setStrokeAlpha(double alpha);

    void setFillColor(String color);

    void setStrokeColor(String color);

    void setStrokeWidth(double width);

    void setDashed(boolean dashed);

    void setDashPattern(String pattern);

    void setLineCap(String cap);

    void setLineJoin(String join);

    void setMiterLimit(double limit);

    void begin();

    void moveTo(double x, double y);

    void lineTo(double x, double y);

    void quadTo(double controlX, double controlY, double x, double y);

    void curveTo(double firstControlX, double firstControlY,
                 double secondControlX, double secondControlY,
                 double x, double y);

    void closePath();

    void rect(double x, double y, double width, double height);

    void roundrect(double x, double y, double width, double height,
                   double radiusX, double radiusY);

    void ellipse(double x, double y, double width, double height);

    void clip();

    void fill();

    void stroke();

    void fillAndStroke();

    /**
     * Draw the text using a future text-layout adapter.
     *
     * <p>Do not silently replace unsupported text layout with an inaccurate
     * simplified rendering.
     */
    void text(double x, double y, double width, double height, String text);

    /**
     * Paint a previously resolved image using a future image adapter.
     *
     * <p>Do not fetch external image URLs from the canvas.
     */
    void image(double x, double y, double width, double height, String resourceId);

    @Override
    void close();
}
