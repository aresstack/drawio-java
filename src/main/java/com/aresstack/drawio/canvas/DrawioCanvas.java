package com.aresstack.drawio.canvas;

/**
 * Express the Draw.io canvas drawing language without depending on GraalJS.
 *
 * <p>The vocabulary mirrors the original {@code mxAbstractCanvas2D} API of the
 * pinned upstream version. Every method is {@code void} and takes only
 * immutable arguments (primitives and strings), so a command stream can be
 * recorded and replayed into several backends (raster and SVG).
 *
 * <p>Close the current path with {@link #closePath()} and release resources
 * with {@link #close()}. The JavaScript adapter maps Draw.io's path
 * operation named "close" to closePath.
 */
public interface DrawioCanvas extends AutoCloseable {

    /** Bold bit of {@link #setFontStyle(int)}, as {@code mxConstants.FONT_BOLD}. */
    int FONT_BOLD = 1;

    /** Italic bit of {@link #setFontStyle(int)}, as {@code mxConstants.FONT_ITALIC}. */
    int FONT_ITALIC = 2;

    /** Underline bit of {@link #setFontStyle(int)}, as {@code mxConstants.FONT_UNDERLINE}. */
    int FONT_UNDERLINE = 4;

    /** Strike-through bit of {@link #setFontStyle(int)}, as {@code mxConstants.FONT_STRIKETHROUGH}. */
    int FONT_STRIKETHROUGH = 8;

    void save();

    void restore();

    void translate(double dx, double dy);

    /**
     * Scale the coordinate system.
     *
     * <p>As in {@code mxAbstractCanvas2D.scale}, the current stroke width is
     * scaled as well; the painted width is {@code strokeWidth * scale}.
     */
    void scale(double factor);

    void rotate(double angleDegrees, boolean flipHorizontal, boolean flipVertical,
                double centerX, double centerY);

    void setAlpha(double alpha);

    void setFillAlpha(double alpha);

    void setStrokeAlpha(double alpha);

    /** Set a solid fill color and clear any gradient. */
    void setFillColor(String color);

    /**
     * Set a two-color gradient fill, as {@code mxAbstractCanvas2D.setGradient}.
     *
     * <p>As in the SVG reference canvas the gradient spans the bounding box of
     * each filled path (objectBoundingBox units); the explicit bounds are kept
     * for API fidelity. {@code direction} is {@code south} (default when
     * {@code null}), {@code north}, {@code east}, {@code west} or {@code radial}.
     */
    void setGradient(String color1, String color2, double x, double y,
                     double width, double height, String direction,
                     double alpha1, double alpha2);

    void setStrokeColor(String color);

    void setStrokeWidth(double width);

    void setDashed(boolean dashed);

    /**
     * Keep dash lengths independent of the stroke width ({@code fixDash=1}).
     */
    void setFixDash(boolean fixDash);

    void setDashPattern(String pattern);

    void setLineCap(String cap);

    void setLineJoin(String join);

    void setMiterLimit(double limit);

    /** Enable or disable the drop shadow for subsequent fill and stroke operations. */
    void setShadow(boolean enabled);

    void setShadowColor(String color);

    void setShadowAlpha(double alpha);

    void setShadowOffset(double dx, double dy);

    /** Set the shadow blur radius in diagram units. */
    void setShadowBlur(double blur);

    void setFontColor(String color);

    void setFontBackgroundColor(String color);

    void setFontBorderColor(String color);

    void setFontBorderWidth(double width);

    void setFontSize(double size);

    void setFontFamily(String family);

    /** Set the font style bit mask ({@link #FONT_BOLD}, {@link #FONT_ITALIC}, ...). */
    void setFontStyle(int style);

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
     * Draw a label, with the argument semantics of {@code mxSvgCanvas2D.text}.
     *
     * <p>{@code (x, y)} is the anchor point: {@code align} ({@code left},
     * {@code center}, {@code right}) and {@code valign} ({@code top},
     * {@code middle}, {@code bottom}) place the text relative to it, and
     * {@code width}/{@code height} describe the label box (0 if unknown).
     * {@code format} is {@code ""} for plain text or {@code "html"} for the
     * safe HTML label subset. {@code overflow} and {@code direction} may be
     * {@code null}. {@code rotation} is in degrees around the anchor point.
     */
    void text(double x, double y, double width, double height, String text,
              String align, String valign, boolean wrap, String format,
              String overflow, boolean clip, double rotation, String direction);

    /**
     * Paint an image, with the argument semantics of {@code mxSvgCanvas2D.image}.
     *
     * <p>{@code source} must be a {@code data:} URI; the canvas never fetches
     * external URLs. Callers resolve other references through an
     * {@link ImageSourcePolicy} first. {@code preserveAspect} centers the
     * image inside the box like SVG {@code xMidYMid meet}.
     */
    void image(double x, double y, double width, double height, String source,
               boolean preserveAspect, boolean flipHorizontal, boolean flipVertical);

    @Override
    void close();
}
