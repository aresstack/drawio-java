package com.aresstack.drawio.api;

/**
 * Stable, machine-readable reasons why a render request failed.
 *
 * <p>New constants may be added in later releases; callers should treat
 * unknown values like {@link #RENDER_FAILED}.
 */
public enum RenderErrorCode {

    /** The input is not well-formed or violates the strict XML rules. */
    INVALID_XML,

    /** The input is XML but not a draw.io {@code mxfile} or {@code mxGraphModel}. */
    UNSUPPORTED_FORMAT,

    /** The draw.io structure, compression or cell references are inconsistent. */
    INVALID_DIAGRAM,

    /** The requested page does not exist or its name is ambiguous. */
    PAGE_NOT_FOUND,

    /** A shape or stencil used by the diagram cannot be rendered faithfully. */
    UNSUPPORTED_STENCIL,

    /** Another drawing, label or style capability cannot be rendered faithfully. */
    UNSUPPORTED_FEATURE,

    /** A referenced image or resource is not allowed by policy or not available. */
    MISSING_RESOURCE,

    /** Rendering exceeded the configured time limit and was aborted. */
    TIMEOUT,

    /** The caller interrupted the request or the renderer was closed while it ran. */
    CANCELLED,

    /** Input, decompression, graph, image or output size limits were exceeded. */
    LIMIT_EXCEEDED,

    /** The renderer rejected the request because its waiting queue is full. */
    OVERLOADED,

    /** Rendering failed for another internal reason. */
    RENDER_FAILED
}
