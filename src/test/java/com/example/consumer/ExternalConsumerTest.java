package com.example.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.PageSelector;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;

/**
 * A consumer outside the library's packages, using only the public API and
 * plain Java 8 syntax (no var, records or List.of).
 */
class ExternalConsumerTest {

    private static final String DIAGRAM = "<mxfile><diagram id=\"a\" name=\"Process\">"
            + "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
            + "</root></mxGraphModel></diagram></mxfile>";

    @Test
    void consumerSeesStableErrorCodesThroughTheFacade() throws RenderException {
        DrawioRenderer renderer = DrawioRenderer.builder().maxConcurrentRenders(1).build();
        try {
            String xml = DrawioRenderer.decodeUtf8(DIAGRAM.getBytes(StandardCharsets.UTF_8));
            final RenderOptions options = RenderOptions.builder()
                    .page(PageSelector.name("Process"))
                    .scale(2.0)
                    .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.FAIL)
                    .build();
            RenderException missingPage = assertThrows(RenderException.class,
                    () -> renderer.renderPng(xml, options.toBuilder()
                            .page(PageSelector.name("Other")).build()));
            assertEquals(RenderErrorCode.PAGE_NOT_FOUND, missingPage.getCode());

            RenderException invalid = assertThrows(RenderException.class,
                    () -> renderer.renderSvg("<mxGraphModel>"));
            assertEquals(RenderErrorCode.INVALID_XML, invalid.getCode());
        } finally {
            renderer.close();
        }
    }
}
