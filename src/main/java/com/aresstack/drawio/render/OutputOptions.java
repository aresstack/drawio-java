package com.aresstack.drawio.render;

import java.awt.Color;

import com.aresstack.drawio.canvas.DataUriImages;
import com.aresstack.drawio.layout.TextMetrics;

/**
 * Configure size, resolution, background and limits for scene output.
 *
 * <p>Instances are immutable and thread-safe; create them with
 * {@link #builder()}. Draw.io diagram units are CSS pixels, so at the default
 * scale of 1 one diagram unit is one output pixel at 96 DPI.
 */
public final class OutputOptions {

    /** CSS reference resolution that Draw.io diagram units are defined in. */
    public static final double CSS_DPI = 96;

    public static final int DEFAULT_MAX_DIMENSION = 16_384;
    public static final long DEFAULT_MAX_PIXELS = 64L * 1024 * 1024;
    public static final long DEFAULT_MAX_SVG_CHARS = 64L * 1024 * 1024;

    private static final OutputOptions DEFAULTS = builder().build();

    private final double scale;
    private final int width;
    private final int padding;
    private final Color background;
    private final double dpi;
    private final BoundsMode boundsMode;
    private final boolean antialiasing;
    private final boolean svgTextAsShapes;
    private final int maxDimension;
    private final long maxPixels;
    private final int maxCommands;
    private final long maxSvgChars;
    private final int maxImageBytes;
    private final long maxImagePixels;
    private final TextMetrics textMetrics;

    private OutputOptions(Builder builder) {
        this.scale = builder.scale;
        this.width = builder.width;
        this.padding = builder.padding;
        this.background = builder.background;
        this.dpi = builder.dpi;
        this.boundsMode = builder.boundsMode;
        this.antialiasing = builder.antialiasing;
        this.svgTextAsShapes = builder.svgTextAsShapes;
        this.maxDimension = builder.maxDimension;
        this.maxPixels = builder.maxPixels;
        this.maxCommands = builder.maxCommands;
        this.maxSvgChars = builder.maxSvgChars;
        this.maxImageBytes = builder.maxImageBytes;
        this.maxImagePixels = builder.maxImagePixels;
        this.textMetrics = builder.textMetrics;
    }

    /** Return the default options: scale 1, no padding, transparent background. */
    public static OutputOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Return a builder initialized with these options. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.scale = scale;
        builder.width = width;
        builder.padding = padding;
        builder.background = background;
        builder.dpi = dpi;
        builder.boundsMode = boundsMode;
        builder.antialiasing = antialiasing;
        builder.svgTextAsShapes = svgTextAsShapes;
        builder.maxDimension = maxDimension;
        builder.maxPixels = maxPixels;
        builder.maxCommands = maxCommands;
        builder.maxSvgChars = maxSvgChars;
        builder.maxImageBytes = maxImageBytes;
        builder.maxImagePixels = maxImagePixels;
        builder.textMetrics = textMetrics;
        return builder;
    }

    /** Scale from diagram units to output pixels; ignored when a width is set. */
    public double getScale() {
        return scale;
    }

    /** Requested total output width in pixels including padding, or 0. */
    public int getWidth() {
        return width;
    }

    /** Unscaled padding in output pixels on every side (Draw.io "border"). */
    public int getPadding() {
        return padding;
    }

    /** Background color, or {@code null} for a transparent background. */
    public Color getBackground() {
        return background;
    }

    /** Resolution written into PNG metadata. */
    public double getDpi() {
        return dpi;
    }

    public BoundsMode getBoundsMode() {
        return boundsMode;
    }

    public boolean isAntialiasing() {
        return antialiasing;
    }

    /**
     * Whether SVG text is emitted as outlines instead of {@code <text>}
     * (default {@code false}, like Draw.io's own SVG export). {@code <text>}
     * stays selectable and searchable but depends on the viewer's fonts, and
     * underline/strikethrough are not emitted; outlines look exactly like the
     * raster output on every viewer and keep text decorations.
     */
    public boolean isSvgTextAsShapes() {
        return svgTextAsShapes;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    public int getMaxCommands() {
        return maxCommands;
    }

    public long getMaxSvgChars() {
        return maxSvgChars;
    }

    /** Maximum decoded size of one embedded {@code data:} image, in bytes. */
    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    /** Maximum pixel count of one embedded {@code data:} image. */
    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    /**
     * Text metrics the output canvas wraps and places labels with, or
     * {@code null} for the canvas default. Must be the same metrics the
     * pipeline measured with, so painted text matches the measured bounds.
     */
    public TextMetrics getTextMetrics() {
        return textMetrics;
    }

    /**
     * Build {@link OutputOptions}. Builders are not thread-safe.
     */
    public static final class Builder {
        private double scale = 1;
        private int width;
        private int padding;
        private Color background;
        private double dpi = CSS_DPI;
        private BoundsMode boundsMode = BoundsMode.UNION;
        private boolean antialiasing = true;
        private boolean svgTextAsShapes;
        private int maxDimension = DEFAULT_MAX_DIMENSION;
        private long maxPixels = DEFAULT_MAX_PIXELS;
        private int maxCommands = CanvasRecorder.DEFAULT_MAX_COMMANDS;
        private long maxSvgChars = DEFAULT_MAX_SVG_CHARS;
        private int maxImageBytes = DataUriImages.DEFAULT_MAX_BYTES;
        private long maxImagePixels = DataUriImages.DEFAULT_MAX_PIXELS;
        private TextMetrics textMetrics;

        private Builder() {
        }

        /** Scale diagram units to output pixels (Draw.io zoom). Clears {@link #width}. */
        public Builder scale(double value) {
            if (!Double.isFinite(value) || value <= 0 || value > 1000) {
                throw new IllegalArgumentException("Scale must be in (0, 1000]");
            }
            this.scale = value;
            this.width = 0;
            return this;
        }

        /**
         * Fit the output to this total width in pixels (padding included),
         * keeping the aspect ratio. Overrides {@link #scale(double)}.
         */
        public Builder width(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Width must be positive");
            }
            this.width = value;
            return this;
        }

        /** Add unscaled padding in output pixels on every side. */
        public Builder padding(int value) {
            if (value < 0 || value > 10_000) {
                throw new IllegalArgumentException("Padding must be in [0, 10000]");
            }
            this.padding = value;
            return this;
        }

        /** Set a background color; {@code null} keeps the background transparent. */
        public Builder background(Color value) {
            this.background = value;
            return this;
        }

        /** Set the resolution written into PNG metadata without changing pixel size. */
        public Builder dpi(double value) {
            if (!Double.isFinite(value) || value <= 0 || value > 100_000) {
                throw new IllegalArgumentException("DPI must be in (0, 100000]");
            }
            this.dpi = value;
            return this;
        }

        /**
         * Render at the given resolution: scale = dpi / 96 and the PNG carries
         * that DPI, so the physical print size stays the diagram size.
         */
        public Builder resolution(double value) {
            dpi(value);
            return scale(value / CSS_DPI);
        }

        public Builder boundsMode(BoundsMode value) {
            if (value == null) {
                throw new IllegalArgumentException("Bounds mode must not be null");
            }
            this.boundsMode = value;
            return this;
        }

        public Builder antialiasing(boolean value) {
            this.antialiasing = value;
            return this;
        }

        public Builder svgTextAsShapes(boolean value) {
            this.svgTextAsShapes = value;
            return this;
        }

        /** Limit output width and height in pixels. */
        public Builder maxDimension(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Maximum dimension must be positive");
            }
            this.maxDimension = value;
            return this;
        }

        /** Limit output area (width times height) in pixels. */
        public Builder maxPixels(long value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Maximum pixels must be positive");
            }
            this.maxPixels = value;
            return this;
        }

        /** Limit recorded canvas commands per scene. */
        public Builder maxCommands(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Maximum commands must be positive");
            }
            this.maxCommands = value;
            return this;
        }

        /** Limit serialized SVG size in characters. */
        public Builder maxSvgChars(long value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Maximum SVG size must be positive");
            }
            this.maxSvgChars = value;
            return this;
        }

        /**
         * Limit embedded {@code data:} images (decoded bytes and pixels per
         * image), e.g. from the caller's resource policy.
         */
        public Builder imageLimits(int maxBytes, long maxPixels) {
            if (maxBytes <= 0 || maxPixels <= 0) {
                throw new IllegalArgumentException("Image limits must be positive");
            }
            this.maxImageBytes = maxBytes;
            this.maxImagePixels = maxPixels;
            return this;
        }

        /**
         * Use these text metrics in the output canvas; {@code null} restores
         * the canvas default. Implementations must be thread-safe.
         */
        public Builder textMetrics(TextMetrics value) {
            this.textMetrics = value;
            return this;
        }

        public OutputOptions build() {
            return new OutputOptions(this);
        }
    }
}
