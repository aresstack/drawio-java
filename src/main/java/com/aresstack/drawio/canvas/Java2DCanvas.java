package com.aresstack.drawio.canvas;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.aresstack.drawio.layout.AwtTextMetrics;
import com.aresstack.drawio.layout.HtmlLabelParser;
import com.aresstack.drawio.layout.LabelLayout;
import com.aresstack.drawio.layout.RunStyle;
import com.aresstack.drawio.layout.TextMetrics;
import com.aresstack.drawio.layout.TextRun;

/**
 * Render Draw.io canvas operations through a caller-supplied Graphics2D backend.
 *
 * <p>Create and own a child graphics context. Leave the caller's Graphics2D
 * open, so either a raster graphics backend or a Batik SVG backend may be used.
 * Every painting operation ends in a plain {@code Graphics2D} call
 * ({@code fill}, {@code draw}, {@code drawString}, {@code drawImage}).
 *
 * <p>Semantics follow the SVG reference canvas {@code mxSvgCanvas2D} of the
 * pinned upstream version: gradients span the bounding box of each filled
 * path in the shape's unrotated coordinate system, shadows are offset copies
 * in the shadow color (blur is not rendered, see docs/CANVAS.md), dash
 * lengths scale with the stroke width unless {@code fixDash} is set, and
 * plain-text labels are positioned exactly like {@code plainText}.
 */
public final class Java2DCanvas implements DrawioCanvas {

    private static final float MIN_DASH_LENGTH = 0.01f;

    private static final double LINE_HEIGHT = 1.2;
    private static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, true);

    private final Deque<SavedGraphics> saved = new ArrayDeque<SavedGraphics>();
    private static final TextMetrics DEFAULT_TEXT_METRICS = new AwtTextMetrics();

    private final int maxImageBytes;
    private final long maxImagePixels;
    private final TextMetrics textMetrics;

    private Graphics2D graphics;
    private CanvasState state;
    private Path2D.Double path;
    private boolean disposed;

    private String cachedImageSource;
    private BufferedImage cachedImage;

    public Java2DCanvas(Graphics2D graphics) {
        this(graphics, DataUriImages.DEFAULT_MAX_BYTES, DataUriImages.DEFAULT_MAX_PIXELS);
    }

    /**
     * Create a canvas with explicit limits for embedded {@code data:} images.
     */
    public Java2DCanvas(Graphics2D graphics, int maxImageBytes, long maxImagePixels) {
        this(graphics, maxImageBytes, maxImagePixels, DEFAULT_TEXT_METRICS);
    }

    /**
     * Create a canvas with image limits and the text metrics used to lay out labels.
     *
     * <p>Use the same {@link TextMetrics} as the label measurement of the graph
     * pipeline, so painted lines match the computed label bounds.
     */
    public Java2DCanvas(Graphics2D graphics, int maxImageBytes, long maxImagePixels,
                        TextMetrics textMetrics) {
        Objects.requireNonNull(graphics, "graphics");
        this.textMetrics = Objects.requireNonNull(textMetrics, "textMetrics");
        if (maxImageBytes < 1 || maxImagePixels < 1) {
            throw new IllegalArgumentException("Image limits must be positive");
        }
        this.maxImageBytes = maxImageBytes;
        this.maxImagePixels = maxImagePixels;
        this.graphics = (Graphics2D) graphics.create();
        this.state = new CanvasState(this.graphics.getTransform());
    }

    @Override
    public void save() {
        ensureOpen();
        saved.push(new SavedGraphics(graphics, state));
        graphics = (Graphics2D) graphics.create();
        state = state.copy();
    }

    @Override
    public void restore() {
        ensureOpen();
        if (saved.isEmpty()) {
            throw new IllegalStateException("Cannot restore canvas without a prior save");
        }
        graphics.dispose();
        SavedGraphics previous = saved.pop();
        graphics = previous.graphics;
        state = previous.state;
    }

    @Override
    public void translate(double dx, double dy) {
        ensureOpen();
        validateFinite(dx, dy);
        state.translationX += dx;
        state.translationY += dy;
    }

    @Override
    public void scale(double factor) {
        ensureOpen();
        validateFinite(factor);
        if (factor <= 0) {
            throw new IllegalArgumentException("Scale must be positive");
        }
        double nextScale = state.scaleFactor * factor;
        double nextStrokeWidth = state.strokeWidth * factor;
        validateFinite(nextScale, nextStrokeWidth);
        if (nextStrokeWidth > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Scaled stroke width is not finite");
        }
        state.scaleFactor = nextScale;
        state.strokeWidth = (float) nextStrokeWidth;
    }

    @Override
    public void rotate(double angleDegrees, boolean flipHorizontal, boolean flipVertical,
                       double centerX, double centerY) {
        ensureOpen();
        validateFinite(angleDegrees, centerX, centerY);
        state.orientation.translate(centerX, centerY);
        state.orientation.rotate(Math.toRadians(angleDegrees));
        state.orientation.scale(flipHorizontal ? -1 : 1, flipVertical ? -1 : 1);
        state.orientation.translate(-centerX, -centerY);
    }

    @Override
    public void setAlpha(double alpha) {
        ensureOpen();
        state.alpha = validatedAlpha(alpha);
    }

    @Override
    public void setFillAlpha(double alpha) {
        ensureOpen();
        state.fillAlpha = validatedAlpha(alpha);
    }

    @Override
    public void setStrokeAlpha(double alpha) {
        ensureOpen();
        state.strokeAlpha = validatedAlpha(alpha);
    }

    @Override
    public void setFillColor(String color) {
        ensureOpen();
        state.fillColor = CanvasColors.parse(color);
        state.gradientColor = null;
    }

    @Override
    public void setGradient(String color1, String color2, double x, double y,
                            double width, double height, String direction,
                            double alpha1, double alpha2) {
        ensureOpen();
        validateFinite(x, y, width, height);
        // CSS "transparent" is a zero-alpha stop in a gradient, not "no color".
        Color start = gradientStop(color1);
        Color end = gradientStop(color2);
        float startAlpha = validatedAlpha(alpha1);
        float endAlpha = validatedAlpha(alpha2);
        String normalized = direction == null ? "south" : normalize(direction);
        switch (normalized) {
            case "south":
            case "north":
            case "east":
            case "west":
            case "radial":
                break;
            default:
                throw new IllegalArgumentException("Unknown gradient direction: " + direction);
        }
        state.fillColor = start;
        state.gradientColor = end;
        state.gradientFillAlpha = startAlpha;
        state.gradientAlpha = endAlpha;
        state.gradientDirection = normalized;
    }

    @Override
    public void setStrokeColor(String color) {
        ensureOpen();
        state.strokeColor = CanvasColors.parse(color);
    }

    @Override
    public void setStrokeWidth(double width) {
        ensureOpen();
        validateFinite(width);
        if (width < 0 || width > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid stroke width");
        }
        state.strokeWidth = (float) width;
    }

    @Override
    public void setDashed(boolean dashed) {
        ensureOpen();
        state.dashed = dashed;
    }

    @Override
    public void setFixDash(boolean fixDash) {
        ensureOpen();
        state.fixDash = fixDash;
    }

    /**
     * Set the dash pattern.
     *
     * <p>Like {@code mxSvgCanvas2D.createDashPattern}, ignore tokens that are
     * not numbers (e.g. {@code none} in stencils); a pattern without numbers
     * paints a solid line. Reject non-positive lengths.
     */
    @Override
    public void setDashPattern(String pattern) {
        ensureOpen();
        Objects.requireNonNull(pattern, "pattern");
        String[] values = pattern.trim().split("[,\\s]+");
        if (values.length == 0 || values.length == 1 && values[0].isEmpty()) {
            throw new IllegalArgumentException("Dash pattern must not be empty");
        }
        if (values.length > 64) {
            throw new IllegalArgumentException("Dash pattern exceeds size limit");
        }

        float[] dash = new float[values.length];
        int count = 0;
        for (String value : values) {
            float length;
            try {
                length = Float.parseFloat(value);
            } catch (NumberFormatException exception) {
                continue;
            }
            if (!Float.isFinite(length) || length < 0) {
                throw new IllegalArgumentException("Dash lengths must not be negative");
            }
            dash[count++] = length;
        }
        // Zero-length dashes are valid (dashPattern=0 4 with round caps draws
        // dots). An all-zero pattern is solid, as SVG treats stroke-dasharray.
        boolean visibleGap = false;
        for (int index = 0; index < count; index++) {
            visibleGap |= dash[index] > 0;
        }
        state.dashPattern = count == 0 || !visibleGap ? null : java.util.Arrays.copyOf(dash, count);
    }

    @Override
    public void setLineCap(String cap) {
        ensureOpen();
        switch (normalize(cap)) {
            case "flat":
            case "butt":
                state.lineCap = BasicStroke.CAP_BUTT;
                return;
            case "round":
                state.lineCap = BasicStroke.CAP_ROUND;
                return;
            case "square":
                state.lineCap = BasicStroke.CAP_SQUARE;
                return;
            default:
                throw new IllegalArgumentException("Unknown line cap: " + cap);
        }
    }

    @Override
    public void setLineJoin(String join) {
        ensureOpen();
        switch (normalize(join)) {
            case "miter":
                state.lineJoin = BasicStroke.JOIN_MITER;
                return;
            case "round":
                state.lineJoin = BasicStroke.JOIN_ROUND;
                return;
            case "bevel":
                state.lineJoin = BasicStroke.JOIN_BEVEL;
                return;
            default:
                throw new IllegalArgumentException("Unknown line join: " + join);
        }
    }

    @Override
    public void setMiterLimit(double limit) {
        ensureOpen();
        validateFinite(limit);
        if (limit < 1 || limit > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Miter limit must be >= 1");
        }
        state.miterLimit = (float) limit;
    }

    @Override
    public void setShadow(boolean enabled) {
        ensureOpen();
        state.shadow = enabled;
    }

    @Override
    public void setShadowColor(String color) {
        ensureOpen();
        state.shadowColor = CanvasColors.parse(color);
    }

    @Override
    public void setShadowAlpha(double alpha) {
        ensureOpen();
        state.shadowAlpha = validatedAlpha(alpha);
    }

    @Override
    public void setShadowOffset(double dx, double dy) {
        ensureOpen();
        validateFinite(dx, dy);
        state.shadowDx = dx;
        state.shadowDy = dy;
    }

    @Override
    public void setShadowBlur(double blur) {
        ensureOpen();
        validateFinite(blur);
        if (blur < 0) {
            throw new IllegalArgumentException("Shadow blur must not be negative");
        }
        state.shadowBlur = blur;
    }

    @Override
    public void setFontColor(String color) {
        ensureOpen();
        state.fontColor = CanvasColors.parse(color);
    }

    @Override
    public void setFontBackgroundColor(String color) {
        ensureOpen();
        state.fontBackgroundColor = CanvasColors.parse(color);
    }

    @Override
    public void setFontBorderColor(String color) {
        ensureOpen();
        state.fontBorderColor = CanvasColors.parse(color);
    }

    @Override
    public void setFontBorderWidth(double width) {
        ensureOpen();
        state.fontBorderWidth = Double.isFinite(width) && width >= 0 ? width : 1;
    }

    @Override
    public void setFontSize(double size) {
        ensureOpen();
        validateFinite(size);
        // draw.io hides labels with fontSize=0: such text is not painted.
        // Oversized values are bounded like the label measurement does.
        state.fontSize = Math.min(Math.max(size, 0), 1024);
    }

    @Override
    public void setFontFamily(String family) {
        ensureOpen();
        Objects.requireNonNull(family, "family");
        if (family.length() > 256) {
            throw new IllegalArgumentException("Font family list exceeds size limit");
        }
        state.fontFamily = family;
    }

    @Override
    public void setFontStyle(int style) {
        ensureOpen();
        state.fontStyle = Math.max(0, style);
    }

    @Override
    public void begin() {
        ensureOpen();
        path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    }

    @Override
    public void moveTo(double x, double y) {
        validateFinite(x, y);
        double[] point = canvasCoordinates(x, y);
        requirePath().moveTo(point[0], point[1]);
    }

    @Override
    public void lineTo(double x, double y) {
        validateFinite(x, y);
        double[] point = canvasCoordinates(x, y);
        requirePath().lineTo(point[0], point[1]);
    }

    @Override
    public void quadTo(double controlX, double controlY, double x, double y) {
        validateFinite(controlX, controlY, x, y);
        double[] points = canvasCoordinates(controlX, controlY, x, y);
        requirePath().quadTo(points[0], points[1], points[2], points[3]);
    }

    @Override
    public void curveTo(double firstControlX, double firstControlY,
                        double secondControlX, double secondControlY,
                        double x, double y) {
        validateFinite(firstControlX, firstControlY, secondControlX, secondControlY, x, y);
        double[] points = canvasCoordinates(
                firstControlX, firstControlY, secondControlX, secondControlY, x, y);
        requirePath().curveTo(points[0], points[1], points[2],
                points[3], points[4], points[5]);
    }

    @Override
    public void closePath() {
        requirePath().closePath();
    }

    @Override
    public void rect(double x, double y, double width, double height) {
        validateGeometry(x, y, width, height);
        begin();
        path.append(state.canvasTransform().createTransformedShape(
                new Rectangle2D.Double(x, y, width, height)), false);
    }

    @Override
    public void roundrect(double x, double y, double width, double height,
                          double radiusX, double radiusY) {
        validateGeometry(x, y, width, height);
        validateFinite(radiusX, radiusY);
        if (radiusX < 0 || radiusY < 0) {
            throw new IllegalArgumentException("Corner radii must be nonnegative");
        }
        begin();
        path.append(state.canvasTransform().createTransformedShape(
                new RoundRectangle2D.Double(
                        x, y, width, height, 2 * radiusX, 2 * radiusY)), false);
    }

    @Override
    public void ellipse(double x, double y, double width, double height) {
        validateGeometry(x, y, width, height);
        begin();
        path.append(state.canvasTransform().createTransformedShape(
                new Ellipse2D.Double(x, y, width, height)), false);
    }

    @Override
    public void clip() {
        graphics.setTransform(state.paintTransform());
        graphics.clip(requirePath());
    }

    @Override
    public void fill() {
        Shape shape = requirePath();
        if (state.shadow) {
            drawShadow(state.fillColor != null ? shape : null, null);
        }
        drawFill(shape);
    }

    @Override
    public void stroke() {
        Shape shape = requirePath();
        if (state.shadow) {
            drawShadow(null, state.strokeColor != null ? shape : null);
        }
        drawStroke(shape);
    }

    @Override
    public void fillAndStroke() {
        Shape shape = requirePath();
        if (state.shadow) {
            drawShadow(state.fillColor != null ? shape : null,
                    state.strokeColor != null ? shape : null);
        }
        drawFill(shape);
        drawStroke(shape);
    }

    @Override
    public void text(double x, double y, double width, double height, String text,
                     String align, String valign, boolean wrap, String format,
                     String overflow, boolean clip, double rotation, String direction) {
        ensureOpen();
        validateFinite(x, y, width, height, rotation);
        Objects.requireNonNull(text, "text");
        if (state.fontSize <= 0 || state.fontColor == null && state.fontBackgroundColor == null
                && state.fontBorderColor == null) {
            return;
        }

        boolean html = "html".equals(format);
        if (text.length() > HtmlLabelParser.MAX_LABEL_CHARACTERS) {
            throw new IllegalArgumentException("Label exceeds the size limit");
        }
        double size = state.fontSize;
        RunStyle base = new RunStyle(state.fontFamily, size,
                (state.fontStyle & FONT_BOLD) != 0, (state.fontStyle & FONT_ITALIC) != 0)
                .withUnderline((state.fontStyle & FONT_UNDERLINE) != 0)
                .withStrikeThrough((state.fontStyle & FONT_STRIKETHROUGH) != 0);
        // RunStyle bounds the size like the headless label measurement does,
        // so painting stays consistent with the bounds the graph computed.
        size = base.getSize();
        // The same layout measures labels in the headless view (DrawioHost.measureLabel),
        // so painted lines match the bounds the graph computed. Plain text never wraps,
        // as in mxSvgCanvas2D.plainText.
        LabelLayout layout = LabelLayout.layout(text, html, base,
                html && wrap ? width : 0, textMetrics);
        List<LabelLayout.Line> lines = layout.getLines();

        double plainLineHeight = Math.round(size * LINE_HEIGHT);
        double textHeight = html ? layout.getHeight()
                : size + (lines.size() - 1) * plainLineHeight;
        boolean fill = "fill".equals(overflow);
        double top = y;
        if ("middle".equals(valign)) {
            top -= fill ? height / 2 : textHeight / 2;
        } else if ("bottom".equals(valign)) {
            top -= fill ? height : textHeight + (html ? 0 : 1);
        }

        Graphics2D textGraphics = (Graphics2D) graphics.create();
        try {
            AffineTransform transform = state.paintTransform();
            transform.concatenate(state.canvasTransform());
            if (rotation != 0) {
                transform.rotate(Math.toRadians(rotation), x, y);
            }
            textGraphics.setTransform(transform);
            textGraphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, state.alpha));
            // Keep an explicit caller choice (e.g. antialiasing disabled for output).
            if (textGraphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING)
                    == RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT) {
                textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            if (textGraphics.getRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS)
                    == RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT) {
                textGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }

            if (clip && width > 0 && height > 0) {
                // As mxSvgCanvas2D.plainText: the clip box is not shifted for overflow=fill.
                double clipX = alignedLeft(x, width, align);
                double clipY = fill ? y : verticalBoxTop(y, height, valign);
                double margin = 2 / state.scaleFactor;
                textGraphics.clip(new Rectangle2D.Double(clipX - margin, clipY - margin,
                        width + 2 * margin, height + 2 * margin));
            }

            paintTextBackground(textGraphics, x, y, width, height, align, valign, overflow,
                    top, textHeight, layout.getWidth());

            if (state.fontColor == null) {
                return;
            }
            double lineTop = top;
            for (LabelLayout.Line line : lines) {
                double lineHeight = html ? line.getHeight() : plainLineHeight;
                double baseline;
                if (html) {
                    Font lineFont = CanvasFonts.resolve(state.fontFamily, state.fontStyle,
                            line.getMaxFontSize());
                    LineMetrics metrics = lineFont.getLineMetrics("Ag", TEXT_CONTEXT);
                    baseline = lineTop + (lineHeight - metrics.getAscent() - metrics.getDescent()) / 2
                            + metrics.getAscent();
                } else {
                    baseline = lineTop + size - 1;
                }
                // Position runs with the advances of the fonts they are painted
                // with (fractional metrics of this context); the layout only
                // decides the line breaks.
                List<TextRun> runs = line.getRuns();
                Font[] fonts = new Font[runs.size()];
                double[] advances = new double[runs.size()];
                double lineWidth = 0;
                for (int index = 0; index < runs.size(); index++) {
                    TextRun run = runs.get(index);
                    fonts[index] = CanvasFonts.resolve(run.getStyle().getFamily(),
                            fontStyleBits(run.getStyle()), run.getStyle().getSize());
                    advances[index] = run.getText().isEmpty() ? 0
                            : fonts[index].getStringBounds(run.getText(),
                                    textGraphics.getFontRenderContext()).getWidth();
                    lineWidth += advances[index];
                }
                double left = alignedLeft(x, lineWidth, align);
                for (int index = 0; index < runs.size(); index++) {
                    TextRun run = runs.get(index);
                    if (!run.getText().trim().isEmpty()) {
                        Color color = runColor(run.getStyle().getColor());
                        if (color != null) {
                            textGraphics.setPaint(color);
                            textGraphics.setFont(fonts[index]);
                            textGraphics.drawString(run.getText(), (float) left, (float) baseline);
                        }
                    }
                    left += advances[index];
                }
                lineTop += lineHeight;
            }
        } finally {
            textGraphics.dispose();
        }
    }

    private Color runColor(String color) {
        if (color == null) {
            return state.fontColor;
        }
        try {
            return CanvasColors.parse(color);
        } catch (IllegalArgumentException exception) {
            // Inline label colors are untrusted data; an unknown one keeps the label color.
            return state.fontColor;
        }
    }

    private static int fontStyleBits(RunStyle style) {
        return (style.isBold() ? FONT_BOLD : 0) | (style.isItalic() ? FONT_ITALIC : 0)
                | (style.isUnderline() ? FONT_UNDERLINE : 0)
                | (style.isStrikeThrough() ? FONT_STRIKETHROUGH : 0);
    }

    @Override
    public void image(double x, double y, double width, double height, String source,
                      boolean preserveAspect, boolean flipHorizontal, boolean flipVertical) {
        ensureOpen();
        validateGeometry(x, y, width, height);
        Objects.requireNonNull(source, "source");
        if (width == 0 || height == 0) {
            return;
        }
        BufferedImage image = decodeImage(source);

        double drawX = x;
        double drawY = y;
        double drawWidth = width;
        double drawHeight = height;
        if (preserveAspect) {
            double scale = Math.min(width / image.getWidth(), height / image.getHeight());
            drawWidth = image.getWidth() * scale;
            drawHeight = image.getHeight() * scale;
            drawX = x + (width - drawWidth) / 2;
            drawY = y + (height - drawHeight) / 2;
        }

        AffineTransform placement = state.paintTransform();
        placement.concatenate(state.canvasTransform());
        placement.translate(drawX + (flipHorizontal ? drawWidth : 0),
                drawY + (flipVertical ? drawHeight : 0));
        placement.scale((flipHorizontal ? -1 : 1) * drawWidth / image.getWidth(),
                (flipVertical ? -1 : 1) * drawHeight / image.getHeight());

        graphics.setTransform(new AffineTransform());
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, state.alpha));
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, placement, null);
        graphics.setTransform(state.paintTransform());
    }

    private BufferedImage decodeImage(String source) {
        if (source.equals(cachedImageSource)) {
            return cachedImage;
        }
        BufferedImage image = DataUriImages.decode(source, maxImageBytes, maxImagePixels);
        cachedImageSource = source;
        cachedImage = image;
        return image;
    }

    private void paintTextBackground(Graphics2D target, double x, double y, double width,
                                     double height, String align, String valign, String overflow,
                                     double top, double textHeight, double textWidth) {
        if (state.fontBackgroundColor == null && state.fontBorderColor == null) {
            return;
        }
        Rectangle2D box;
        if ("fill".equals(overflow) || "width".equals(overflow)) {
            double boxHeight = "fill".equals(overflow) ? height : textHeight;
            double boxTop = "fill".equals(overflow) ? verticalBoxTop(y, height, valign) : top;
            box = new Rectangle2D.Double(alignedLeft(x, width, align) + 1, boxTop,
                    Math.max(0, width - 2), boxHeight + 2);
        } else {
            box = new Rectangle2D.Double(alignedLeft(x, textWidth, align) - 1, top - 1,
                    textWidth + 2, textHeight + 2);
        }
        if (state.fontBackgroundColor != null) {
            target.setPaint(state.fontBackgroundColor);
            target.fill(box);
        }
        if (state.fontBorderColor != null) {
            target.setPaint(state.fontBorderColor);
            target.setStroke(new BasicStroke((float) state.fontBorderWidth));
            target.draw(box);
        }
    }

    private static Color gradientStop(String color) {
        if (color != null && "transparent".equalsIgnoreCase(color.trim())) {
            return new Color(0, 0, 0, 0);
        }
        return CanvasColors.parse(color);
    }

    private static double alignedLeft(double x, double width, String align) {
        if ("center".equals(align)) {
            return x - width / 2;
        }
        if ("right".equals(align)) {
            return x - width;
        }
        return x;
    }

    private static double verticalBoxTop(double y, double height, String valign) {
        if ("middle".equals(valign)) {
            return y - height / 2;
        }
        if ("bottom".equals(valign)) {
            return y - height;
        }
        return y;
    }

    private void drawShadow(Shape filled, Shape stroked) {
        if (state.shadowColor == null || (filled == null && stroked == null)) {
            return;
        }
        Area area = new Area();
        if (filled != null) {
            area.add(new Area(filled));
        }
        if (stroked != null) {
            area.add(new Area(createStroke().createStrokedShape(stroked)));
        }
        AffineTransform offset = AffineTransform.getTranslateInstance(
                state.shadowDx * state.scaleFactor, state.shadowDy * state.scaleFactor);
        float alpha = state.alpha * state.shadowAlpha
                * (filled != null ? state.fillAlpha : state.strokeAlpha);
        applyPaint(state.shadowColor, alpha);
        graphics.fill(offset.createTransformedShape(area));
    }

    private void drawFill(Shape shape) {
        if (state.fillColor == null) {
            return;
        }
        float alpha = state.alpha * state.fillAlpha;
        if (state.gradientColor == null) {
            applyPaint(state.fillColor, alpha);
            graphics.fill(shape);
            return;
        }

        // Like SVG objectBoundingBox gradients: span the path bounds in the
        // shape's unrotated system, then rotate the painted result with it.
        AffineTransform orientation = state.scaledOrientation();
        Shape unrotated;
        try {
            unrotated = orientation.createInverse().createTransformedShape(shape);
        } catch (NoninvertibleTransformException exception) {
            throw new IllegalStateException("Canvas orientation is not invertible", exception);
        }
        AffineTransform paintTransform = state.paintTransform();
        paintTransform.concatenate(orientation);
        graphics.setTransform(paintTransform);
        graphics.setPaint(gradientPaint(unrotated.getBounds2D()));
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        graphics.fill(unrotated);
        graphics.setTransform(state.paintTransform());
    }

    private Paint gradientPaint(Rectangle2D bounds) {
        Color start = withAlpha(state.fillColor, state.gradientFillAlpha);
        Color end = withAlpha(state.gradientColor, state.gradientAlpha);
        float[] fractions = {0f, 1f};
        Color[] colors = {start, end};
        if (bounds.getWidth() <= 0 && bounds.getHeight() <= 0) {
            return start;
        }
        if ("radial".equals(state.gradientDirection)) {
            Rectangle2D square = bounds.getWidth() > 0 && bounds.getHeight() > 0 ? bounds
                    : new Rectangle2D.Double(bounds.getX(), bounds.getY(),
                    Math.max(bounds.getWidth(), 1e-6), Math.max(bounds.getHeight(), 1e-6));
            return new RadialGradientPaint(square, fractions, colors,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE);
        }
        double minX = bounds.getMinX();
        double minY = bounds.getMinY();
        double maxX = bounds.getMaxX();
        double maxY = bounds.getMaxY();
        Point2D from;
        Point2D to;
        switch (state.gradientDirection) {
            case "east":
                from = new Point2D.Double(minX, minY);
                to = new Point2D.Double(maxX, minY);
                break;
            case "west":
                from = new Point2D.Double(maxX, minY);
                to = new Point2D.Double(minX, minY);
                break;
            case "north":
                from = new Point2D.Double(minX, maxY);
                to = new Point2D.Double(minX, minY);
                break;
            default:
                from = new Point2D.Double(minX, minY);
                to = new Point2D.Double(minX, maxY);
                break;
        }
        if (from.distance(to) < 1e-9) {
            return start;
        }
        return new LinearGradientPaint(from, to, fractions, colors,
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }

    private static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.round(color.getAlpha() * alpha));
    }

    private void drawStroke(Shape shape) {
        if (state.strokeColor == null) {
            return;
        }
        applyPaint(state.strokeColor, state.alpha * state.strokeAlpha);
        graphics.setStroke(createStroke());
        graphics.draw(shape);
    }

    private void applyPaint(Color color, float alpha) {
        graphics.setTransform(state.paintTransform());
        graphics.setPaint(color);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    }

    private BasicStroke createStroke() {
        double scaledWidth = state.strokeWidth * state.scaleFactor;
        if (!Double.isFinite(scaledWidth) || scaledWidth > Float.MAX_VALUE) {
            throw new IllegalArgumentException("Scaled stroke width is not finite");
        }

        float width = (float) scaledWidth;
        float[] dashPattern = state.dashed && state.dashPattern != null
                ? state.dashPattern.clone() : null;
        if (dashPattern != null) {
            float dashScale = state.fixDash ? (float) state.scaleFactor : Math.max(width, 0.01f);
            for (int index = 0; index < dashPattern.length; index++) {
                // Java 8's stroker drops zero-length dashes, so the round-cap
                // dots of "0 4" would vanish; a minimal length draws them on
                // every JDK and stays invisible with butt caps.
                dashPattern[index] = Math.max(dashPattern[index] * dashScale, MIN_DASH_LENGTH);
            }
        }
        return new BasicStroke(width, state.lineCap, state.lineJoin,
                state.miterLimit, dashPattern, 0);
    }

    private double[] canvasCoordinates(double... coordinates) {
        double[] transformed = coordinates.clone();
        state.canvasTransform().transform(transformed, 0, transformed, 0,
                transformed.length / 2);
        return transformed;
    }

    private Path2D.Double requirePath() {
        ensureOpen();
        if (path == null) {
            throw new IllegalStateException("Begin a path before drawing it");
        }
        return path;
    }

    private void validateGeometry(double x, double y, double width, double height) {
        validateFinite(x, y, width, height);
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Shape size must not be negative");
        }
    }

    private void validateFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Canvas coordinates must be finite");
            }
        }
    }

    private float validatedAlpha(double alpha) {
        validateFinite(alpha);
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }
        return (float) alpha;
    }

    private String normalize(String value) {
        return Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
    }

    private void ensureOpen() {
        if (disposed) {
            throw new IllegalStateException("Canvas has already been closed");
        }
    }

    @Override
    public void close() {
        if (disposed) {
            return;
        }
        disposed = true;
        graphics.dispose();
        while (!saved.isEmpty()) {
            saved.pop().graphics.dispose();
        }
        path = null;
        cachedImage = null;
        cachedImageSource = null;
    }

    private static final class SavedGraphics {
        private final Graphics2D graphics;
        private final CanvasState state;

        private SavedGraphics(Graphics2D graphics, CanvasState state) {
            this.graphics = graphics;
            this.state = state;
        }
    }

    private static final class CanvasState {
        private final AffineTransform initialTransform;
        private final AffineTransform orientation = new AffineTransform();
        private double translationX;
        private double translationY;
        private double scaleFactor = 1;
        private Color fillColor;
        private Color strokeColor;
        private float alpha = 1;
        private float fillAlpha = 1;
        private float strokeAlpha = 1;
        private float strokeWidth = 1;
        private boolean dashed;
        private float[] dashPattern = new float[] {3, 3};
        private int lineCap = BasicStroke.CAP_BUTT;
        private int lineJoin = BasicStroke.JOIN_MITER;
        private float miterLimit = 10;
        private boolean fixDash;
        private Color gradientColor;
        private float gradientFillAlpha = 1;
        private float gradientAlpha = 1;
        private String gradientDirection = "south";
        private boolean shadow;
        private Color shadowColor = new Color(0x808080);
        private float shadowAlpha = 1;
        private double shadowDx = 2;
        private double shadowDy = 3;
        private double shadowBlur = 2;
        private Color fontColor = Color.BLACK;
        private Color fontBackgroundColor;
        private Color fontBorderColor;
        private double fontBorderWidth = 1;
        private double fontSize = 11;
        private String fontFamily = "Arial,Helvetica";
        private int fontStyle;

        private CanvasState(AffineTransform transform) {
            this.initialTransform = new AffineTransform(transform);
        }

        private AffineTransform canvasTransform() {
            AffineTransform transform = new AffineTransform();
            transform.scale(scaleFactor, scaleFactor);
            transform.concatenate(orientation);
            transform.translate(translationX, translationY);
            return transform;
        }

        /**
         * Return the orientation in scaled canvas space: canvasTransform equals
         * scaledOrientation * scale * translation.
         */
        private AffineTransform scaledOrientation() {
            AffineTransform transform = AffineTransform.getScaleInstance(scaleFactor, scaleFactor);
            transform.concatenate(orientation);
            transform.scale(1 / scaleFactor, 1 / scaleFactor);
            return transform;
        }

        private AffineTransform paintTransform() {
            return new AffineTransform(initialTransform);
        }

        private CanvasState copy() {
            CanvasState copy = new CanvasState(initialTransform);
            copy.orientation.setTransform(orientation);
            copy.translationX = translationX;
            copy.translationY = translationY;
            copy.scaleFactor = scaleFactor;
            copy.fillColor = fillColor;
            copy.strokeColor = strokeColor;
            copy.alpha = alpha;
            copy.fillAlpha = fillAlpha;
            copy.strokeAlpha = strokeAlpha;
            copy.strokeWidth = strokeWidth;
            copy.dashed = dashed;
            copy.dashPattern = dashPattern == null ? null : dashPattern.clone();
            copy.lineCap = lineCap;
            copy.lineJoin = lineJoin;
            copy.miterLimit = miterLimit;
            copy.fixDash = fixDash;
            copy.gradientColor = gradientColor;
            copy.gradientFillAlpha = gradientFillAlpha;
            copy.gradientAlpha = gradientAlpha;
            copy.gradientDirection = gradientDirection;
            copy.shadow = shadow;
            copy.shadowColor = shadowColor;
            copy.shadowAlpha = shadowAlpha;
            copy.shadowDx = shadowDx;
            copy.shadowDy = shadowDy;
            copy.shadowBlur = shadowBlur;
            copy.fontColor = fontColor;
            copy.fontBackgroundColor = fontBackgroundColor;
            copy.fontBorderColor = fontBorderColor;
            copy.fontBorderWidth = fontBorderWidth;
            copy.fontSize = fontSize;
            copy.fontFamily = fontFamily;
            copy.fontStyle = fontStyle;
            return copy;
        }
    }
}
