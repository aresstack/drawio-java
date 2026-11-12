package com.aresstack.drawio.api;

/**
 * Decide what happens when a diagram uses a shape, stencil or capability
 * that cannot be rendered faithfully.
 */
public enum UnsupportedFeaturePolicy {

    /** Fail the request with {@link RenderErrorCode#UNSUPPORTED_STENCIL} or
     * {@link RenderErrorCode#UNSUPPORTED_FEATURE}. This is the default. */
    FAIL,

    /** Render what is supported and report every omission as a {@link RenderWarning};
     * the result is then marked incomplete. */
    WARN
}
