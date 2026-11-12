package com.aresstack.drawio.render;

/**
 * Choose which area of a scene becomes the output image.
 */
public enum BoundsMode {

    /**
     * Use the union of the scene's logical bounds and the measured painted
     * bounds. Strokes, arrowheads, shadows and labels that extend past the
     * logical graph bounds stay visible. Falls back to {@link #PAINTED} when
     * the scene has no logical bounds. This is the default.
     */
    UNION,

    /**
     * Use only the measured painted bounds (the tightest box around
     * everything actually drawn).
     */
    PAINTED,

    /**
     * Use only the scene's logical bounds, as Draw.io's own export does.
     * Paint outside them is clipped. Requires logical bounds.
     */
    LOGICAL
}
