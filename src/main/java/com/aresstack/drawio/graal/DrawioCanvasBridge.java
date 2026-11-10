package com.aresstack.drawio.graal;

import java.util.Objects;

import org.graalvm.polyglot.HostAccess;

import com.aresstack.drawio.canvas.DrawioCanvas;

/**
 * Forward permitted Draw.io canvas commands to the Java drawing port.
 *
 * <p>Expose only commands explicitly implemented by the drawing port.
 * Keep Graphics2D and all other Java host objects invisible to scripts.
 */
public final class DrawioCanvasBridge {

    private final DrawioCanvas canvas;

    public DrawioCanvasBridge(DrawioCanvas canvas) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
    }

    @HostAccess.Export
    public void save() {
        canvas.save();
    }

    @HostAccess.Export
    public void restore() {
        canvas.restore();
    }

    @HostAccess.Export
    public void translate(double dx, double dy) {
        canvas.translate(dx, dy);
    }

    @HostAccess.Export
    public void scale(double factor) {
        canvas.scale(factor);
    }

    @HostAccess.Export
    public void rotate(double degrees, boolean flipHorizontal, boolean flipVertical,
                       double centerX, double centerY) {
        canvas.rotate(degrees, flipHorizontal, flipVertical, centerX, centerY);
    }

    @HostAccess.Export
    public void setAlpha(double alpha) {
        canvas.setAlpha(alpha);
    }

    @HostAccess.Export
    public void setFillAlpha(double alpha) {
        canvas.setFillAlpha(alpha);
    }

    @HostAccess.Export
    public void setStrokeAlpha(double alpha) {
        canvas.setStrokeAlpha(alpha);
    }

    @HostAccess.Export
    public void setFillColor(String color) {
        canvas.setFillColor(color);
    }

    @HostAccess.Export
    public void setStrokeColor(String color) {
        canvas.setStrokeColor(color);
    }

    @HostAccess.Export
    public void setStrokeWidth(double width) {
        canvas.setStrokeWidth(width);
    }

    @HostAccess.Export
    public void setDashed(boolean dashed) {
        canvas.setDashed(dashed);
    }

    @HostAccess.Export
    public void setDashPattern(String pattern) {
        canvas.setDashPattern(pattern);
    }

    @HostAccess.Export
    public void setLineCap(String cap) {
        canvas.setLineCap(cap);
    }

    @HostAccess.Export
    public void setLineJoin(String join) {
        canvas.setLineJoin(join);
    }

    @HostAccess.Export
    public void setMiterLimit(double limit) {
        canvas.setMiterLimit(limit);
    }

    @HostAccess.Export
    public void setShadow(boolean enabled) {
        if (enabled) {
            throw new UnsupportedOperationException("Shadows require a dedicated adapter (#10)");
        }
    }

    @HostAccess.Export
    public void begin() {
        canvas.begin();
    }

    @HostAccess.Export
    public void moveTo(double x, double y) {
        canvas.moveTo(x, y);
    }

    @HostAccess.Export
    public void lineTo(double x, double y) {
        canvas.lineTo(x, y);
    }

    @HostAccess.Export
    public void quadTo(double cx, double cy, double x, double y) {
        canvas.quadTo(cx, cy, x, y);
    }

    @HostAccess.Export
    public void curveTo(double firstCx, double firstCy, double secondCx,
                        double secondCy, double x, double y) {
        canvas.curveTo(firstCx, firstCy, secondCx, secondCy, x, y);
    }

    @HostAccess.Export
    public void closePath() {
        canvas.closePath();
    }

    @HostAccess.Export
    public void rect(double x, double y, double width, double height) {
        canvas.rect(x, y, width, height);
    }

    @HostAccess.Export
    public void roundrect(double x, double y, double width, double height,
                          double radiusX, double radiusY) {
        canvas.roundrect(x, y, width, height, radiusX, radiusY);
    }

    @HostAccess.Export
    public void ellipse(double x, double y, double width, double height) {
        canvas.ellipse(x, y, width, height);
    }

    @HostAccess.Export
    public void clip() {
        canvas.clip();
    }

    @HostAccess.Export
    public void fill() {
        canvas.fill();
    }

    @HostAccess.Export
    public void stroke() {
        canvas.stroke();
    }

    @HostAccess.Export
    public void fillAndStroke() {
        canvas.fillAndStroke();
    }

    @HostAccess.Export
    public void text(double x, double y, double width, double height, String text) {
        canvas.text(x, y, width, height, text);
    }

    @HostAccess.Export
    public void image(double x, double y, double width, double height, String resourceId) {
        canvas.image(x, y, width, height, resourceId);
    }
}
