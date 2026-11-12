package com.aresstack.drawio.render;

/**
 * Report that rendering output would exceed a configured resource limit.
 *
 * <p>The message names only the limit and the numbers involved, never diagram
 * content. A facade maps this to its {@code LIMIT_EXCEEDED} error code.
 */
public final class OutputLimitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Identify which limit was exceeded. */
    public enum Limit {
        /** Too many recorded canvas commands. */
        COMMANDS,
        /** Output width or height in pixels too large. */
        DIMENSION,
        /** Output area in pixels too large. */
        PIXELS,
        /** Serialized SVG document too large. */
        SVG_SIZE
    }

    private final Limit limit;

    public OutputLimitException(Limit limit, String message) {
        super(message);
        this.limit = limit;
    }

    public Limit getLimit() {
        return limit;
    }
}
