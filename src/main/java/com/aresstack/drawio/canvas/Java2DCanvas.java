package com.aresstack.drawio.canvas;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;

/**
 * Render Draw.io canvas operations through a caller-supplied Graphics2D backend.
 *
 * <p>Create and own a child graphics context. Leave the caller's Graphics2D
 * open, so either a raster graphics backend or a Batik SVG backend may be used.
 */
public final class Java2DCanvas implements DrawioCanvas {

    private final Deque<SavedGraphics> saved = new ArrayDeque<SavedGraphics>();

    private Graphics2D graphics;
    private CanvasState state;
    private Path2D.Double path;
    private boolean disposed;

    public Java2DCanvas(Graphics2D graphics) {
        Objects.requireNonNull(graphics, "graphics");
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
        state.transform.translate(dx, dy);
    }

    @Override
    public void scale(double factor) {
        ensureOpen();
        validateFinite(factor);
        if (factor <= 0) {
            throw new IllegalArgumentException("Scale must be positive");
        }
        state.transform.scale(factor, factor);
    }

    @Override
    public void rotate(double angleDegrees, boolean flipHorizontal, boolean flipVertical,
                       double centerX, double centerY) {
        ensureOpen();
        validateFinite(angleDegrees, centerX, centerY);
        state.transform.translate(centerX, centerY);
        state.transform.rotate(Math.toRadians(angleDegrees));
        state.transform.scale(flipHorizontal ? -1 : 1, flipVertical ? -1 : 1);
        state.transform.translate(-centerX, -centerY);
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
    public void setDashPattern(String pattern) {
        ensureOpen();
        Objects.requireNonNull(pattern, "pattern");
        String[] values = pattern.trim().split("[,\\s]+");
        if (values.length == 0 || values.length == 1 && values[0].isEmpty()) {
            throw new IllegalArgumentException("Dash pattern must not be empty");
        }

        float[] dash = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            dash[index] = Float.parseFloat(values[index]);
            if (!Float.isFinite(dash[index]) || dash[index] <= 0) {
                throw new IllegalArgumentException("Dash lengths must be positive");
            }
        }
        state.dashPattern = dash;
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
    public void begin() {
        ensureOpen();
        path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    }

    @Override
    public void moveTo(double x, double y) {
        validateFinite(x, y);
        requirePath().moveTo(x, y);
    }

    @Override
    public void lineTo(double x, double y) {
        validateFinite(x, y);
        requirePath().lineTo(x, y);
    }

    @Override
    public void quadTo(double controlX, double controlY, double x, double y) {
        validateFinite(controlX, controlY, x, y);
        requirePath().quadTo(controlX, controlY, x, y);
    }

    @Override
    public void curveTo(double firstControlX, double firstControlY,
                        double secondControlX, double secondControlY,
                        double x, double y) {
        validateFinite(firstControlX, firstControlY, secondControlX, secondControlY, x, y);
        requirePath().curveTo(firstControlX, firstControlY, secondControlX, secondControlY, x, y);
    }

    @Override
    public void closePath() {
        requirePath().closePath();
    }

    @Override
    public void rect(double x, double y, double width, double height) {
        validateGeometry(x, y, width, height);
        begin();
        path.append(new Rectangle2D.Double(x, y, width, height), false);
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
        path.append(new RoundRectangle2D.Double(
                x, y, width, height, 2 * radiusX, 2 * radiusY), false);
    }

    @Override
    public void ellipse(double x, double y, double width, double height) {
        validateGeometry(x, y, width, height);
        begin();
        path.append(new Ellipse2D.Double(x, y, width, height), false);
    }

    @Override
    public void clip() {
        graphics.setTransform(state.transform);
        graphics.clip(requirePath());
    }

    @Override
    public void fill() {
        drawFill(requirePath());
    }

    @Override
    public void stroke() {
        drawStroke(requirePath());
    }

    @Override
    public void fillAndStroke() {
        Shape shape = requirePath();
        drawFill(shape);
        drawStroke(shape);
    }

    @Override
    public void text(double x, double y, double width, double height, String text) {
        ensureOpen();
        throw new UnsupportedOperationException("Text layout belongs to the future text adapter (#9)");
    }

    @Override
    public void image(double x, double y, double width, double height, String resourceId) {
        ensureOpen();
        throw new UnsupportedOperationException("Image rendering belongs to the future image adapter (#10)");
    }

    private void drawFill(Shape shape) {
        if (state.fillColor == null) {
            return;
        }
        applyPaint(state.fillColor, state.alpha * state.fillAlpha);
        graphics.fill(shape);
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
        graphics.setTransform(state.transform);
        graphics.setPaint(color);
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    }

    private BasicStroke createStroke() {
        float[] dashPattern = state.dashed ? state.dashPattern.clone() : null;
        return new BasicStroke(state.strokeWidth, state.lineCap, state.lineJoin,
                state.miterLimit, dashPattern, 0);
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
        private final AffineTransform transform;
        private Color fillColor = Color.WHITE;
        private Color strokeColor = Color.BLACK;
        private float alpha = 1;
        private float fillAlpha = 1;
        private float strokeAlpha = 1;
        private float strokeWidth = 1;
        private boolean dashed;
        private float[] dashPattern = new float[] {3, 3};
        private int lineCap = BasicStroke.CAP_BUTT;
        private int lineJoin = BasicStroke.JOIN_MITER;
        private float miterLimit = 10;

        private CanvasState(AffineTransform transform) {
            this.transform = new AffineTransform(transform);
        }

        private CanvasState copy() {
            CanvasState copy = new CanvasState(transform);
            copy.fillColor = fillColor;
            copy.strokeColor = strokeColor;
            copy.alpha = alpha;
            copy.fillAlpha = fillAlpha;
            copy.strokeAlpha = strokeAlpha;
            copy.strokeWidth = strokeWidth;
            copy.dashed = dashed;
            copy.dashPattern = dashPattern.clone();
            copy.lineCap = lineCap;
            copy.lineJoin = lineJoin;
            copy.miterLimit = miterLimit;
            return copy;
        }
    }
}
