package com.aresstack.drawio.render;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import com.aresstack.drawio.canvas.Java2DCanvas;

/**
 * Render a {@link DrawioScene} as BufferedImage, PNG or SVG through one Java2D path.
 *
 * <p>Every output format replays the same recorded canvas commands into a
 * {@link Java2DCanvas}; only the {@link Graphics2D} backend differs (a raster
 * {@link BufferedImage} or Batik's {@code SVGGraphics2D}). There is no second
 * drawing implementation for SVG.
 *
 * <p>Pass a {@link CanvasRecording} to avoid repainting the scene source (for
 * example re-running JavaScript); any other scene is recorded once per call.
 *
 * <p>Instances are stateless and thread-safe. No network, file or font
 * resources are loaded, and the SVG output contains no DOCTYPE or external
 * references. On Java 8 the Java2D rasterization step is serialized across
 * threads (see {@link #SERIALIZE_JAVA2D_PROPERTY}) because OpenJDK 8's Pisces
 * engine is not reliably deterministic under concurrency.
 */
public final class SceneRenderer {

    private static final String PNG_METADATA_FORMAT = "javax_imageio_png_1.0";

    /**
     * System property that forces ({@code true}) or disables ({@code false})
     * serialized Java2D geometry work. Defaults to {@code true} on Java 8.
     */
    public static final String SERIALIZE_JAVA2D_PROPERTY = "com.aresstack.drawio.serializeJava2D";

    private static final Object JAVA2D_LOCK = new Object();
    private static final boolean SERIALIZE_JAVA2D = serializeJava2D();

    /**
     * Record a scene once so it can be rendered repeatedly without repainting
     * its source.
     */
    public CanvasRecording record(DrawioScene scene, OutputOptions options) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(options, "options");
        return CanvasRecorder.record(scene, options.getMaxCommands());
    }

    /**
     * Compute output size and coordinate mapping without rendering pixels.
     */
    public OutputGeometry layout(DrawioScene scene, OutputOptions options) {
        return layout(record(scene, options), options);
    }

    /**
     * Render the scene into a new ARGB image.
     */
    public BufferedImage renderImage(DrawioScene scene, OutputOptions options) {
        CanvasRecording recording = record(scene, options);
        OutputGeometry geometry = layout(recording, options);
        return rasterize(recording, geometry, options);
    }

    /**
     * Render the scene as PNG bytes, with the configured DPI in the pHYs chunk.
     */
    public byte[] renderPng(DrawioScene scene, OutputOptions options) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            writePng(scene, options, output);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    /**
     * Write the scene as PNG. The stream is flushed but not closed.
     */
    public void writePng(DrawioScene scene, OutputOptions options, OutputStream output)
            throws IOException {
        Objects.requireNonNull(output, "output");
        BufferedImage image = renderImage(scene, options);
        writePng(image, options.getDpi(), output);
    }

    /**
     * Render the scene as a standalone SVG document.
     */
    public String renderSvg(DrawioScene scene, OutputOptions options) {
        StringWriter writer = new StringWriter();
        try {
            writeSvg(scene, options, writer);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return writer.toString();
    }

    /**
     * Write the scene as a UTF-8 SVG document. The writer is flushed but not closed.
     */
    public void writeSvg(DrawioScene scene, OutputOptions options, Writer output)
            throws IOException {
        Objects.requireNonNull(output, "output");
        CanvasRecording recording = record(scene, options);
        OutputGeometry geometry = layout(recording, options);
        SvgOutput.write(recording, geometry, options, output);
    }

    /**
     * Render a recording into a new ARGB image using a geometry from
     * {@link #layout(DrawioScene, OutputOptions)} with the same options, which
     * avoids measuring the painted bounds a second time.
     */
    public BufferedImage renderImage(CanvasRecording recording, OutputGeometry geometry,
                                     OutputOptions options) {
        requireAll(recording, geometry, options);
        return rasterize(recording, geometry, options);
    }

    /**
     * Render a recording as PNG bytes using a precomputed geometry.
     */
    public byte[] renderPng(CanvasRecording recording, OutputGeometry geometry,
                            OutputOptions options) {
        BufferedImage image = renderImage(recording, geometry, options);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            writePng(image, options.getDpi(), output);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return output.toByteArray();
    }

    /**
     * Render a recording as an SVG document using a precomputed geometry.
     */
    public String renderSvg(CanvasRecording recording, OutputGeometry geometry,
                            OutputOptions options) {
        requireAll(recording, geometry, options);
        StringWriter writer = new StringWriter();
        try {
            SvgOutput.write(recording, geometry, options, writer);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return writer.toString();
    }

    private static void requireAll(CanvasRecording recording, OutputGeometry geometry,
                                   OutputOptions options) {
        Objects.requireNonNull(recording, "recording");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(options, "options");
    }

    static OutputGeometry layout(CanvasRecording recording, OutputOptions options) {
        Rectangle2D source = sourceBounds(recording, options);
        int padding = options.getPadding();
        double scale = options.getScale();
        long width;
        if (options.getWidth() > 0) {
            width = options.getWidth();
            double available = width - 2.0 * padding;
            if (available <= 0) {
                throw new IllegalArgumentException("Width " + width
                        + " leaves no room inside padding " + padding);
            }
            if (source.getWidth() > 0) {
                scale = available / source.getWidth();
            }
        } else {
            width = pixels(source.getWidth(), scale, padding);
        }
        long height = pixels(source.getHeight(), scale, padding);
        checkLimits(width, height, options);
        return new OutputGeometry(source, scale, padding, (int) width, (int) height);
    }

    static void paint(Graphics2D graphics, CanvasRecording recording,
                      OutputGeometry geometry, OutputOptions options, boolean raster) {
        if (SERIALIZE_JAVA2D) {
            synchronized (JAVA2D_LOCK) {
                paintUnlocked(graphics, recording, geometry, options, raster);
            }
        } else {
            paintUnlocked(graphics, recording, geometry, options, raster);
        }
    }

    private static void paintUnlocked(Graphics2D graphics, CanvasRecording recording,
                                      OutputGeometry geometry, OutputOptions options,
                                      boolean raster) {
        Object antialias = options.isAntialiasing()
                ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, options.isAntialiasing()
                ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        if (raster) {
            // Batik maps this hint to color-interpolation="linearRGB", which
            // would change how browsers blend SVG colors; keep sRGB for SVG.
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
        }

        if (options.getBackground() != null) {
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(options.getBackground());
            graphics.fillRect(0, 0, geometry.getWidth(), geometry.getHeight());
        }

        graphics.transform(geometry.toDeviceTransform());
        if (options.getBoundsMode() == BoundsMode.LOGICAL) {
            graphics.clip(geometry.getSourceBounds());
        }
        try (Java2DCanvas canvas = newCanvas(graphics, options)) {
            recording.paint(canvas);
        }
    }

    private static Java2DCanvas newCanvas(Graphics2D graphics, OutputOptions options) {
        if (options.getTextMetrics() == null) {
            return new Java2DCanvas(graphics, options.getMaxImageBytes(),
                    options.getMaxImagePixels());
        }
        return new Java2DCanvas(graphics, options.getMaxImageBytes(),
                options.getMaxImagePixels(), options.getTextMetrics());
    }

    static Dimension size(OutputGeometry geometry) {
        return new Dimension(geometry.getWidth(), geometry.getHeight());
    }

    private static BufferedImage rasterize(CanvasRecording recording, OutputGeometry geometry,
                                           OutputOptions options) {
        BufferedImage image = new BufferedImage(
                geometry.getWidth(), geometry.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            paint(graphics, recording, geometry, options, true);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Rectangle2D sourceBounds(CanvasRecording recording, OutputOptions options) {
        Rectangle2D logical = recording.getLogicalBounds();
        BoundsMode mode = options.getBoundsMode();
        if (mode == BoundsMode.LOGICAL) {
            if (logical == null) {
                throw new IllegalArgumentException(
                        "BoundsMode.LOGICAL requires a scene with logical bounds");
            }
            return logical;
        }
        Rectangle2D painted = paintedBounds(recording, options);
        if (mode == BoundsMode.PAINTED || logical == null) {
            return painted == null
                    ? (logical == null ? new Rectangle2D.Double() : logical) : painted;
        }
        if (painted == null) {
            return logical;
        }
        Rectangle2D union = (Rectangle2D) logical.clone();
        union.add(painted);
        return union;
    }

    /**
     * Measure what the recording actually paints, in diagram units.
     */
    static Rectangle2D paintedBounds(CanvasRecording recording, OutputOptions options) {
        if (SERIALIZE_JAVA2D) {
            synchronized (JAVA2D_LOCK) {
                return paintedBoundsUnlocked(recording, options);
            }
        }
        return paintedBoundsUnlocked(recording, options);
    }

    private static Rectangle2D paintedBoundsUnlocked(CanvasRecording recording,
                                                     OutputOptions options) {
        BoundsGraphics2D graphics = new BoundsGraphics2D();
        try (Java2DCanvas canvas = newCanvas(graphics, options)) {
            recording.paint(canvas);
        }
        return graphics.getPaintedBounds();
    }

    /**
     * OpenJDK 8's Pisces rendering engine occasionally produces different
     * anti-aliased pixels and stroke outlines when several threads rasterize
     * at once (observed about once per few hundred parallel renders; never on
     * Java 9+ with Marlin). Serialize Java2D geometry work there so output
     * stays deterministic; GraalJS and recording still run in parallel.
     */
    private static boolean serializeJava2D() {
        String configured = System.getProperty(SERIALIZE_JAVA2D_PROPERTY);
        if (configured != null) {
            return Boolean.parseBoolean(configured);
        }
        return System.getProperty("java.specification.version", "").startsWith("1.");
    }

    private static long pixels(double extent, double scale, int padding) {
        double scaled = Math.ceil(extent * scale - 1e-9);
        if (!Double.isFinite(scaled) || scaled > Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) scaled + 2L * padding);
    }

    private static void checkLimits(long width, long height, OutputOptions options) {
        if (width > options.getMaxDimension() || height > options.getMaxDimension()) {
            throw new OutputLimitException(OutputLimitException.Limit.DIMENSION, String.format(
                    Locale.ROOT, "Output %sx%s exceeds maximum dimension %d",
                    describe(width), describe(height), options.getMaxDimension()));
        }
        if (width * height > options.getMaxPixels()) {
            throw new OutputLimitException(OutputLimitException.Limit.PIXELS, String.format(
                    Locale.ROOT, "Output %dx%d exceeds maximum of %d pixels",
                    width, height, options.getMaxPixels()));
        }
    }

    private static String describe(long value) {
        return value == Long.MAX_VALUE ? "(overflow)" : Long.toString(value);
    }

    static void writePng(BufferedImage image, double dpi, OutputStream output) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) {
            throw new IOException("No PNG image writer available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromRenderedImage(image), parameters);
            addResolution(metadata, dpi);
            ImageOutputStream stream = new MemoryCacheImageOutputStream(output);
            try {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, metadata), parameters);
            } finally {
                stream.close();
            }
            output.flush();
        } finally {
            writer.dispose();
        }
    }

    private static void addResolution(IIOMetadata metadata, double dpi) throws IOException {
        long pixelsPerMeter = Math.round(dpi / 0.0254);
        IIOMetadataNode physical = new IIOMetadataNode("pHYs");
        physical.setAttribute("pixelsPerUnitXAxis", Long.toString(pixelsPerMeter));
        physical.setAttribute("pixelsPerUnitYAxis", Long.toString(pixelsPerMeter));
        physical.setAttribute("unitSpecifier", "meter");
        IIOMetadataNode root = new IIOMetadataNode(PNG_METADATA_FORMAT);
        root.appendChild(physical);
        try {
            metadata.mergeTree(PNG_METADATA_FORMAT, root);
        } catch (IIOInvalidTreeException exception) {
            throw new IOException("Cannot write PNG resolution metadata", exception);
        }
    }
}
