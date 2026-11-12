package com.aresstack.drawio;

import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

/**
 * Write a prepared scene in the requested output format.
 */
interface SceneOutput {

    <T> Rendered<T> write(DrawioScene scene, RenderSession session,
                          DrawioRenderer.OutputKind kind) throws RenderException;

    static SceneOutput java2d() {
        return new Java2DSceneOutput();
    }

    final class Rendered<T> {

        final T output;
        final int width;
        final int height;

        Rendered(T output, int width, int height) {
            this.output = output;
            this.width = width;
            this.height = height;
        }
    }
}
