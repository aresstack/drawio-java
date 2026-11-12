package com.aresstack.drawio;

import com.aresstack.drawio.graph.OriginalGraphPipeline;
import com.aresstack.drawio.render.DiagramPipeline;

/**
 * Select the production graph pipeline: the original draw.io/mxGraph
 * JavaScript stack running headless in GraalJS (#9).
 */
final class DefaultPipeline {

    private static final DiagramPipeline INSTANCE = new OriginalGraphPipeline();

    private DefaultPipeline() {
    }

    static DiagramPipeline create() {
        return INSTANCE;
    }
}
