package com.aresstack.drawio.graal;

import java.util.Objects;

import org.graalvm.polyglot.HostAccess;

import com.aresstack.drawio.canvas.CanvasDiagnostics;
import com.aresstack.drawio.canvas.DataUriImages;
import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.canvas.ImageSourcePolicy;

/**
 * Forward permitted Draw.io canvas commands to the Java drawing port.
 *
 * <p>Expose only commands explicitly implemented by the drawing port.
 * Keep Graphics2D and all other Java host objects invisible to scripts.
 *
 * <p>The bridge is the policy boundary between guest JavaScript and the
 * canvas: image references are resolved through an {@link ImageSourcePolicy}
 * (default: no images) and unsupported features are reported to
 * {@link CanvasDiagnostics} (default: fail fast) instead of being painted
 * as a silent substitute.
 */
public final class DrawioCanvasBridge {

    private static final int MAX_TEXT_ARGUMENT = 64;

    private final DrawioCanvas canvas;
    private final ImageSourcePolicy images;
    private final CanvasDiagnostics diagnostics;

    public DrawioCanvasBridge(DrawioCanvas canvas) {
        this(canvas, ImageSourcePolicy.NONE, CanvasDiagnostics.STRICT);
    }

    public DrawioCanvasBridge(DrawioCanvas canvas, ImageSourcePolicy images,
                              CanvasDiagnostics diagnostics) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.images = Objects.requireNonNull(images, "images");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
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
        canvas.setAlpha(unitInterval(alpha));
    }

    @HostAccess.Export
    public void setFillAlpha(double alpha) {
        canvas.setFillAlpha(unitInterval(alpha));
    }

    @HostAccess.Export
    public void setStrokeAlpha(double alpha) {
        canvas.setStrokeAlpha(unitInterval(alpha));
    }

    @HostAccess.Export
    public void setFillColor(String color) {
        canvas.setFillColor(color("fillColor", color));
    }

    @HostAccess.Export
    public void setStrokeColor(String color) {
        canvas.setStrokeColor(color("strokeColor", color));
    }

    @HostAccess.Export
    public void setStrokeWidth(double width) {
        // SVG ignores an invalid stroke-width and uses its initial value 1.
        canvas.setStrokeWidth(Double.isNaN(width) || width < 0 ? 1 : width);
    }

    @HostAccess.Export
    public void setDashed(boolean dashed) {
        canvas.setDashed(dashed);
    }

    @HostAccess.Export
    public void setDashPattern(String pattern) {
        // SVG ignores a dash array with negative or non-numeric entries.
        if (pattern != null && !VALID_DASHES.matcher(pattern).matches()) {
            return;
        }
        canvas.setDashPattern(pattern);
    }

    @HostAccess.Export
    public void setLineCap(String cap) {
        if (cap != null && !LINE_CAPS.contains(cap.trim().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        canvas.setLineCap(cap);
    }

    @HostAccess.Export
    public void setLineJoin(String join) {
        if (join != null && !LINE_JOINS.contains(join.trim().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        canvas.setLineJoin(join);
    }

    @HostAccess.Export
    public void setMiterLimit(double limit) {
        if (Double.isNaN(limit) || limit < 1) {
            return;
        }
        canvas.setMiterLimit(limit);
    }

    @HostAccess.Export
    public void setFixDash(boolean fixDash) {
        canvas.setFixDash(fixDash);
    }

    @HostAccess.Export
    public void setGradient(String color1, String color2, double x, double y,
                            double width, double height, String direction,
                            double alpha1, double alpha2) {
        String start = color("gradient", color1);
        String end = color("gradient", color2);
        if (start == null || end == null) {
            canvas.setFillColor(start != null ? start : end);
            return;
        }
        String normalized = direction == null ? null : direction.trim().toLowerCase(java.util.Locale.ROOT);
        canvas.setGradient(start, end, x, y, width, height,
                normalized == null || !GRADIENT_DIRECTIONS.contains(normalized) ? "south" : normalized,
                unitInterval(alpha1), unitInterval(alpha2));
    }

    @HostAccess.Export
    public void setShadow(boolean enabled) {
        canvas.setShadow(enabled);
    }

    @HostAccess.Export
    public void setShadowStyle(String color, double alpha, double dx, double dy, double blur) {
        String shadow = color("shadowColor", color);
        canvas.setShadowColor(shadow != null ? shadow : "#000000");
        canvas.setShadowAlpha(Math.max(0, Math.min(1, alpha)));
        canvas.setShadowOffset(dx, dy);
        canvas.setShadowBlur(Math.max(0, blur));
    }

    @HostAccess.Export
    public void setFontColor(String color) {
        canvas.setFontColor(color("fontColor", color));
    }

    @HostAccess.Export
    public void setFontBackgroundColor(String color) {
        canvas.setFontBackgroundColor(color("labelBackgroundColor", color));
    }

    @HostAccess.Export
    public void setFontBorderColor(String color) {
        canvas.setFontBorderColor(color("labelBorderColor", color));
    }

    @HostAccess.Export
    public void setFontBorderWidth(double width) {
        canvas.setFontBorderWidth(width);
    }

    @HostAccess.Export
    public void setFontSize(double size) {
        canvas.setFontSize(size);
    }

    @HostAccess.Export
    public void setFontFamily(String family) {
        canvas.setFontFamily(family);
    }

    @HostAccess.Export
    public void setFontStyle(int style) {
        canvas.setFontStyle(style);
    }

    private static final java.util.regex.Pattern VALID_DASHES = java.util.regex.Pattern.compile(
            "\\s*(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[\\s,]+(?:\\d+(?:\\.\\d*)?|\\.\\d+))*\\s*");
    private static final java.util.Set<String> LINE_CAPS = new java.util.HashSet<String>(
            java.util.Arrays.asList("flat", "butt", "round", "square"));
    private static final java.util.Set<String> LINE_JOINS = new java.util.HashSet<String>(
            java.util.Arrays.asList("miter", "round", "bevel"));
    private static final java.util.Set<String> GRADIENT_DIRECTIONS = new java.util.HashSet<String>(
            java.util.Arrays.asList("south", "north", "east", "west", "radial"));

    /** Browsers clamp opacities; style values like opacity=150 must not fail the page. */
    private static double unitInterval(double value) {
        return Double.isNaN(value) ? 1 : Math.max(0, Math.min(1, value));
    }

    private static double nonNegative(double value) {
        return Double.isNaN(value) || value < 0 ? 0 : value;
    }

    /**
     * Validate a style color at the guest boundary. A color the canvas cannot
     * parse (for example {@code currentColor} or {@code hsl(...)}) is reported
     * as {@code canvas:color} through the session policy and painted as none.
     */
    private String color(String property, String value) {
        if (value == null) {
            return null;
        }
        try {
            com.aresstack.drawio.canvas.CanvasColors.parse(value);
            return value;
        } catch (IllegalArgumentException exception) {
            diagnostics.unsupported("canvas:color", "Unsupported " + property + " value");
            return null;
        }
    }

    /**
     * Report a canvas capability the adapter cannot map, e.g. a sketch fill style.
     */
    @HostAccess.Export
    public void unsupported(String capabilityId, String message) {
        diagnostics.unsupported(bounded(capabilityId), bounded(message));
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
        canvas.roundrect(x, y, width, height, nonNegative(radiusX), nonNegative(radiusY));
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
    public void text(double x, double y, double width, double height, String text,
                     String align, String valign, boolean wrap, String format,
                     String overflow, boolean clip, double rotation, String direction) {
        if (direction != null && direction.startsWith("vertical")) {
            diagnostics.unsupported("canvas:text-direction:" + bounded(direction),
                    "Vertical writing modes are not supported");
            return;
        }
        if (format != null && !format.isEmpty() && !"html".equals(format)) {
            diagnostics.unsupported("canvas:text-format:" + bounded(format),
                    "Unknown label format");
            return;
        }
        canvas.text(x, y, width, height, text, align, valign, wrap, format, overflow,
                clip, rotation, direction);
    }

    @HostAccess.Export
    public void image(double x, double y, double width, double height, String source,
                      boolean preserveAspect, boolean flipHorizontal, boolean flipVertical) {
        if (source == null || source.trim().isEmpty()) {
            diagnostics.missingResource("image:empty", "Image without source");
            return;
        }
        if (DataUriImages.isSvgDataUri(source)) {
            diagnostics.unsupported("image:svg", "Embedded SVG images are not supported");
            return;
        }
        String resolved;
        try {
            resolved = images.resolve(source);
        } catch (IllegalArgumentException exception) {
            diagnostics.missingResource(imageCapability(source),
                    "Image rejected: " + exception.getMessage());
            return;
        }
        if (resolved == null) {
            diagnostics.missingResource(imageCapability(source),
                    "Image is not available under the resource policy");
            return;
        }
        canvas.image(x, y, width, height, resolved, preserveAspect, flipHorizontal, flipVertical);
    }

    private static String imageCapability(String source) {
        return DataUriImages.isDataUri(source) ? "image:data-uri" : "image:external";
    }

    private static String bounded(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() > MAX_TEXT_ARGUMENT ? value.substring(0, MAX_TEXT_ARGUMENT) : value;
    }
}
