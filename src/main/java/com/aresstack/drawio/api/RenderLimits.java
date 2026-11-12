package com.aresstack.drawio.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Bound the time and size of one render request.
 *
 * <p>The defaults are conservative values for interactive use. Every limit
 * is enforced in addition to the fixed safety limits of the document parser
 * (16 MiB source, 16 MiB total decompressed content, 100 pages).
 */
public final class RenderLimits {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    /** Largest accepted timeout; deadlines are based on {@code System.nanoTime()}. */
    public static final Duration MAX_TIMEOUT = Duration.ofDays(365);
    public static final int DEFAULT_MAX_INPUT_CHARACTERS = 16 * 1024 * 1024;
    public static final int DEFAULT_MAX_CELLS = 20000;
    public static final long DEFAULT_MAX_OUTPUT_PIXELS = 64L * 1024 * 1024;
    public static final int DEFAULT_MAX_OUTPUT_DIMENSION = 16384;
    public static final int DEFAULT_MAX_SVG_CHARACTERS = 32 * 1024 * 1024;

    private static final RenderLimits DEFAULTS = builder().build();

    private final Duration timeout;
    private final int maxInputCharacters;
    private final int maxCells;
    private final long maxOutputPixels;
    private final int maxOutputDimension;
    private final int maxSvgCharacters;

    private RenderLimits(Builder builder) {
        this.timeout = builder.timeout;
        this.maxInputCharacters = builder.maxInputCharacters;
        this.maxCells = builder.maxCells;
        this.maxOutputPixels = builder.maxOutputPixels;
        this.maxOutputDimension = builder.maxOutputDimension;
        this.maxSvgCharacters = builder.maxSvgCharacters;
    }

    public static RenderLimits defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .timeout(timeout)
                .maxInputCharacters(maxInputCharacters)
                .maxCells(maxCells)
                .maxOutputPixels(maxOutputPixels)
                .maxOutputDimension(maxOutputDimension)
                .maxSvgCharacters(maxSvgCharacters);
    }

    /** Wall-clock budget for the whole request, including parsing and output encoding. */
    public Duration getTimeout() {
        return timeout;
    }

    /** Maximum characters of draw.io source accepted before parsing. */
    public int getMaxInputCharacters() {
        return maxInputCharacters;
    }

    /** Maximum number of cells (vertices, edges, groups, layers) on the rendered page. */
    public int getMaxCells() {
        return maxCells;
    }

    /** Maximum width times height of a raster output, after scaling. */
    public long getMaxOutputPixels() {
        return maxOutputPixels;
    }

    /** Maximum width or height in pixels of any output, after scaling. */
    public int getMaxOutputDimension() {
        return maxOutputDimension;
    }

    /** Maximum characters of a generated SVG document. */
    public int getMaxSvgCharacters() {
        return maxSvgCharacters;
    }

    public static final class Builder {

        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxInputCharacters = DEFAULT_MAX_INPUT_CHARACTERS;
        private int maxCells = DEFAULT_MAX_CELLS;
        private long maxOutputPixels = DEFAULT_MAX_OUTPUT_PIXELS;
        private int maxOutputDimension = DEFAULT_MAX_OUTPUT_DIMENSION;
        private int maxSvgCharacters = DEFAULT_MAX_SVG_CHARACTERS;

        private Builder() {
        }

        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.toMillis() < 1) {
                throw new IllegalArgumentException("timeout must be at least one millisecond");
            }
            if (timeout.compareTo(MAX_TIMEOUT) > 0) {
                // Deadlines are System.nanoTime() based and must not overflow.
                throw new IllegalArgumentException("timeout must be at most " + MAX_TIMEOUT.toDays() + " days");
            }
            this.timeout = timeout;
            return this;
        }

        public Builder maxInputCharacters(int value) {
            if (value < 1 || value > DEFAULT_MAX_INPUT_CHARACTERS) {
                throw new IllegalArgumentException(
                        "maxInputCharacters must be between 1 and the parser limit");
            }
            this.maxInputCharacters = value;
            return this;
        }

        public Builder maxCells(int value) {
            this.maxCells = positive(value, "maxCells");
            return this;
        }

        public Builder maxOutputPixels(long value) {
            if (value < 1) {
                throw new IllegalArgumentException("maxOutputPixels must be positive");
            }
            this.maxOutputPixels = value;
            return this;
        }

        public Builder maxOutputDimension(int value) {
            this.maxOutputDimension = positive(value, "maxOutputDimension");
            return this;
        }

        public Builder maxSvgCharacters(int value) {
            this.maxSvgCharacters = positive(value, "maxSvgCharacters");
            return this;
        }

        public RenderLimits build() {
            return new RenderLimits(this);
        }

        private static int positive(int value, String name) {
            if (value < 1) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}
