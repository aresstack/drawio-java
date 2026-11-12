package com.aresstack.drawio;

import java.awt.Color;
import java.awt.image.BufferedImage;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.render.CanvasRecording;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.OutputGeometry;
import com.aresstack.drawio.render.OutputLimitException;
import com.aresstack.drawio.render.OutputOptions;
import com.aresstack.drawio.render.RenderSession;
import com.aresstack.drawio.render.SceneRenderer;

/**
 * Adapt the shared Java2D output writers (#11) to the facade.
 *
 * <p>The scene is recorded once; raster and vector output replay the same
 * recording, so the pipeline's JavaScript never runs twice.
 */
final class Java2DSceneOutput implements SceneOutput {

    private final SceneRenderer renderer = new SceneRenderer();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Rendered<T> write(DrawioScene scene, RenderSession session,
                                 DrawioRenderer.OutputKind kind) throws RenderException {
        OutputOptions options = outputOptions(session.options()).toBuilder()
                .textMetrics(session.textMetrics())
                .build();
        try {
            CanvasRecording recording = renderer.record(scene, options);
            session.checkCancelled();
            switch (kind) {
                case IMAGE: {
                    BufferedImage image = renderer.renderImage(recording, options);
                    return new Rendered<T>((T) image, image.getWidth(), image.getHeight());
                }
                case PNG: {
                    OutputGeometry geometry = renderer.layout(recording, options);
                    byte[] png = renderer.renderPng(recording, geometry, options);
                    return new Rendered<T>((T) png, geometry.getWidth(), geometry.getHeight());
                }
                case SVG:
                default: {
                    OutputGeometry geometry = renderer.layout(recording, options);
                    String svg = renderer.renderSvg(recording, geometry, options);
                    return new Rendered<T>((T) svg, geometry.getWidth(), geometry.getHeight());
                }
            }
        } catch (OutputLimitException exception) {
            throw new RenderException(RenderErrorCode.LIMIT_EXCEEDED,
                    "Output exceeds the configured " + exception.getLimit().name().toLowerCase()
                            + " limit");
        }
    }

    static OutputOptions outputOptions(RenderOptions options) {
        RenderLimits limits = options.getLimits();
        OutputOptions.Builder builder = OutputOptions.builder()
                .scale(options.getScale())
                .padding(options.getPadding())
                .background(options.getBackground() == null
                        ? null : Color.decode(options.getBackground()))
                .maxDimension(limits.getMaxOutputDimension())
                .maxPixels(limits.getMaxOutputPixels())
                .maxSvgChars(limits.getMaxSvgCharacters())
                .imageLimits(options.getResourcePolicy().getMaxResourceBytes(),
                        options.getResourcePolicy().getMaxImagePixels());
        if (options.getFitWidth() > 0) {
            builder.width(options.getFitWidth());
        }
        return builder.build();
    }
}
