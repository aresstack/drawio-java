package com.aresstack.drawio.graal;

import java.util.Objects;

import org.graalvm.polyglot.HostAccess;

import com.aresstack.drawio.api.DiagramFormatException;
import com.aresstack.drawio.layout.FontDescriptor;
import com.aresstack.drawio.layout.LabelLayout;
import com.aresstack.drawio.layout.RunStyle;
import com.aresstack.drawio.layout.TextBounds;
import com.aresstack.drawio.layout.TextMetrics;
import com.aresstack.drawio.parse.XmlTreeJson;

/**
 * Expose explicitly approved capabilities to guest JavaScript.
 *
 * <p>Keep Java2D objects and filesystem access outside the guest boundary.
 */
public final class DrawioHost {

    private final ScriptResourceProvider resources;
    private final DrawioCanvasBridge canvas;
    private final TextMetrics textMetrics;
    private final DrawioBinaryCodec binaryCodec;

    public DrawioHost(ScriptResourceProvider resources) {
        this(resources, null, null);
    }

    public DrawioHost(ScriptResourceProvider resources, DrawioCanvasBridge canvas) {
        this(resources, canvas, null);
    }

    public DrawioHost(ScriptResourceProvider resources, DrawioCanvasBridge canvas,
                      TextMetrics textMetrics) {
        this(resources, canvas, textMetrics, new DrawioBinaryCodec());
    }

    /**
     * Create a host with an explicit binary codec budget for offline stencils.
     */
    public DrawioHost(ScriptResourceProvider resources, DrawioCanvasBridge canvas,
                      TextMetrics textMetrics, DrawioBinaryCodec binaryCodec) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.canvas = canvas;
        this.textMetrics = textMetrics;
        this.binaryCodec = Objects.requireNonNull(binaryCodec, "binaryCodec");
    }

    /**
     * Return the budgeted base64, raw-inflate and UTF-8 capability used by
     * {@code drawio-stencil-runtime.js}.
     */
    @HostAccess.Export
    public DrawioBinaryCodec binaryCodec() {
        return binaryCodec;
    }

    @HostAccess.Export
    public String measureText(String text, String cssFamily, double fontSize,
                              boolean bold, boolean italic) {
        if (textMetrics == null) {
            throw new IllegalStateException("Text measurement is unavailable");
        }

        TextBounds bounds = textMetrics.measure(
                text, new FontDescriptor(cssFamily, fontSize, bold, italic));
        return bounds.getWidth() + "," + bounds.getAscent() + ","
                + bounds.getDescent() + "," + bounds.getHeight();
    }

    /**
     * Measure a draw.io label with the shared {@link LabelLayout}.
     *
     * @param wrapWidth wrap width in pixels, or a value {@code <= 0} for none
     * @return {@code "width,height"} in unscaled pixels
     */
    @HostAccess.Export
    public String measureLabel(String value, boolean html, String cssFamily, double fontSize,
                               boolean bold, boolean italic, double wrapWidth) {
        if (textMetrics == null) {
            throw new IllegalStateException("Text measurement is unavailable");
        }
        RunStyle base = new RunStyle(cssFamily == null ? "sans-serif" : cssFamily,
                fontSize, bold, italic);
        LabelLayout layout = LabelLayout.layout(value, html, base, wrapWidth, textMetrics);
        return layout.getWidth() + "," + layout.getHeight();
    }

    /**
     * Parse untrusted XML for {@code DrawioXml.fromJson} in
     * {@code drawio-xml-dom.js}, for example an inline
     * {@code shape=stencil(...)} definition. Uses the strict parser: no DTDs,
     * no external entities, depth and size limits.
     *
     * @return the JSON tree produced by {@link XmlTreeJson}
     * @throws IllegalArgumentException if the XML is invalid or too large
     */
    @HostAccess.Export
    public String parseXml(String xml) {
        try {
            return XmlTreeJson.toJson(Objects.requireNonNull(xml, "xml"));
        } catch (DiagramFormatException exception) {
            throw new IllegalArgumentException("Invalid XML: " + exception.getMessage(), exception);
        }
    }

    @HostAccess.Export
    public DrawioCanvasBridge canvas() {
        if (canvas == null) {
            throw new IllegalStateException("No drawing canvas is configured for this script");
        }
        return canvas;
    }

    @HostAccess.Export
    public String loadResource(String resourceId) {
        return resources.loadText(Objects.requireNonNull(resourceId, "resourceId"));
    }
}
