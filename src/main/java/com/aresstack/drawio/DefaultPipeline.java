package com.aresstack.drawio;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.render.DiagramPipeline;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

/**
 * Select the production graph pipeline.
 *
 * <p>Until the original-mxGraph pipeline (#9) is integrated, every request
 * fails explicitly instead of painting an approximation.
 */
final class DefaultPipeline {

    private DefaultPipeline() {
    }

    static DiagramPipeline create() {
        return new DiagramPipeline() {
            @Override
            public DrawioScene prepare(DiagramPage page, RenderSession session)
                    throws RenderException {
                throw new RenderException(RenderErrorCode.RENDER_FAILED,
                        "The draw.io graph pipeline is not available in this build");
            }
        };
    }
}
