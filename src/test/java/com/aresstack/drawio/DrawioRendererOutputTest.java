package com.aresstack.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.render.DrawioScene;

/**
 * Exercise the facade with the real Java2D/SVG output writers.
 */
class DrawioRendererOutputTest {

    private static final String MODEL = "<mxGraphModel><root><mxCell id=\"0\"/>"
            + "<mxCell id=\"1\" parent=\"0\"/></root></mxGraphModel>";

    private final AtomicInteger paints = new AtomicInteger();
    private final DrawioRenderer renderer = DrawioRenderer.builder()
            .maxConcurrentRenders(2)
            .pipeline((page, session) -> (DrawioScene) canvas -> {
                paints.incrementAndGet();
                canvas.setFillColor("#ff0000");
                canvas.rect(0, 0, 100, 50);
                canvas.fill();
            })
            .build();

    @AfterEach
    void close() {
        renderer.close();
    }

    @Test
    void rendersImageWithScaleBackgroundAndPadding() throws RenderException {
        RenderResult<BufferedImage> result = renderer.renderImage(MODEL, RenderOptions.builder()
                .scale(2).padding(10).background("#0000ff").build());
        BufferedImage image = result.getOutput();
        assertEquals(220, image.getWidth());
        assertEquals(120, image.getHeight());
        assertEquals(220, result.getWidth());
        assertEquals(0xffff0000, image.getRGB(110, 60));
        assertEquals(0xff0000ff, image.getRGB(2, 2));
        assertEquals(1, paints.get(), "scene source must be painted once per render");
    }

    @Test
    void rendersPngAndSvgOfTheSameSize() throws RenderException, IOException {
        RenderResult<byte[]> png = renderer.renderPng(MODEL);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png.getOutput()));
        assertEquals(100, decoded.getWidth());
        assertEquals(50, decoded.getHeight());
        assertEquals(100, png.getWidth());

        RenderResult<String> svg = renderer.renderSvg(MODEL);
        assertTrue(svg.getOutput().contains("<svg"));
        assertTrue(!svg.getOutput().contains("<!DOCTYPE"));
        assertEquals(100, svg.getWidth());
        assertEquals(50, svg.getHeight());
    }

    @Test
    void fitsWidth() throws RenderException {
        RenderResult<BufferedImage> result = renderer.renderImage(MODEL,
                RenderOptions.builder().fitWidth(50).build());
        assertEquals(50, result.getWidth());
        assertEquals(25, result.getHeight());
    }

    @Test
    void outputLimitsMapToLimitExceeded() {
        RenderException exception = assertThrows(RenderException.class,
                () -> renderer.renderImage(MODEL, RenderOptions.builder().limits(
                        RenderLimits.builder().maxOutputDimension(60).build()).build()));
        assertEquals(RenderErrorCode.LIMIT_EXCEEDED, exception.getCode());
        exception = assertThrows(RenderException.class,
                () -> renderer.renderSvg(MODEL, RenderOptions.builder().limits(
                        RenderLimits.builder().maxSvgCharacters(50).build()).build()));
        assertEquals(RenderErrorCode.LIMIT_EXCEEDED, exception.getCode());
    }
}
