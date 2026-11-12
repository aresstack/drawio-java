package com.aresstack.drawio;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

/**
 * Adapt the shared Java2D output writers (#11) to the facade.
 */
final class Java2DSceneOutput implements SceneOutput {

    @Override
    public <T> Rendered<T> write(DrawioScene scene, RenderSession session,
                                 DrawioRenderer.OutputKind kind) throws RenderException {
        throw new RenderException(RenderErrorCode.RENDER_FAILED,
                "Scene output is not available in this build");
    }
}
