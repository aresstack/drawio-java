package com.aresstack.drawio.render;

import java.awt.geom.Rectangle2D;

import com.aresstack.drawio.canvas.DrawioCanvas;

/**
 * Describe one drawable Draw.io page as a sequence of canvas commands.
 *
 * <p>A scene is the hand-off between the graph pipeline (which runs the
 * original Draw.io JavaScript) and the output writers (BufferedImage, PNG,
 * SVG). Implementations must be repaintable: {@link #paint(DrawioCanvas)} may
 * be called more than once, for example once for a raster and once for a
 * vector output, and must issue the same commands each time.
 *
 * <p>Coordinates are Draw.io diagram units (CSS pixels at scale 1). The output
 * writers choose the final scale, translation and padding; a scene must not
 * assume any particular target size.
 */
public interface DrawioScene {

    /**
     * Issue this scene's drawing commands on the given canvas.
     *
     * <p>Do not close the canvas; the caller owns it.
     *
     * @param canvas target canvas, never {@code null}
     */
    void paint(DrawioCanvas canvas);

    /**
     * Return the logical diagram bounds in diagram units, if known.
     *
     * <p>For a graph scene this is {@code mxGraphView.getGraphBounds()}. The
     * output writers union these bounds with the measured painted bounds by
     * default, so strokes, arrowheads and shadows that extend past the
     * logical bounds are not clipped.
     *
     * @return a copy of the logical bounds, or {@code null} if unknown
     */
    default Rectangle2D getLogicalBounds() {
        return null;
    }

    /**
     * Return the page background of the diagram ({@code mxGraphModel}
     * attribute {@code background}) as {@code #rrggbb}, if it is a color.
     *
     * <p>The facade paints it when the request sets no background, as
     * draw.io's export does.
     *
     * @return the page color, or {@code null} for none
     */
    default String getPageBackground() {
        return null;
    }
}
