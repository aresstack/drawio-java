package com.aresstack.drawio;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.render.OutputOptions;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Java2DSceneOutputTest {

    @Test
    void resourcePolicyImageLimitsReachTheOutputCanvas() {
        RenderOptions options = RenderOptions.builder()
                .resourcePolicy(ResourcePolicy.dataUrisOnly().withLimits(16 * 1024 * 1024, 64L << 20))
                .build();

        OutputOptions output = Java2DSceneOutput.outputOptions(options);

        assertEquals(16 * 1024 * 1024, output.getMaxImageBytes());
        assertEquals(64L << 20, output.getMaxImagePixels());
    }
}
