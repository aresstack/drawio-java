package com.aresstack.drawio.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Hold the output of one successful render and its diagnostics.
 *
 * @param <T> output type: {@code BufferedImage}, PNG {@code byte[]} or SVG {@code String}
 */
public final class RenderResult<T> {

    private final T output;
    private final int width;
    private final int height;
    private final RenderedPage page;
    private final List<RenderWarning> warnings;
    private final boolean complete;
    private final Duration duration;

    public RenderResult(T output, int width, int height, RenderedPage page,
                        List<RenderWarning> warnings, boolean complete, Duration duration) {
        this.output = Objects.requireNonNull(output, "output");
        this.width = width;
        this.height = height;
        this.page = Objects.requireNonNull(page, "page");
        this.warnings = Collections.unmodifiableList(
                new ArrayList<RenderWarning>(Objects.requireNonNull(warnings, "warnings")));
        this.complete = complete;
        this.duration = Objects.requireNonNull(duration, "duration");
    }

    /** Return the rendered output. Byte arrays are not copied; do not modify them. */
    public T getOutput() {
        return output;
    }

    /** Output width in pixels (SVG: the {@code width} attribute). */
    public int getWidth() {
        return width;
    }

    /** Output height in pixels (SVG: the {@code height} attribute). */
    public int getHeight() {
        return height;
    }

    public RenderedPage getPage() {
        return page;
    }

    /** Return warnings in first-seen order; empty when the render was clean. */
    public List<RenderWarning> getWarnings() {
        return warnings;
    }

    /**
     * Return {@code false} if the caller chose {@link UnsupportedFeaturePolicy#WARN}
     * and at least one shape, feature or resource was left out.
     */
    public boolean isComplete() {
        return complete;
    }

    /** Wall-clock time of the request, including queueing. */
    public Duration getDuration() {
        return duration;
    }

    /** Return the library and pinned draw.io versions that produced this output. */
    public String getRendererVersion() {
        return RendererVersion.LIBRARY_VERSION;
    }

    public String getDrawioUpstreamCommit() {
        return RendererVersion.DRAWIO_UPSTREAM_COMMIT;
    }
}
