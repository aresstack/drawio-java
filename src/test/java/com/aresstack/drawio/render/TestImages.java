package com.aresstack.drawio.render;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Shared helpers for comparing raster and vector output in tests.
 */
final class TestImages {

    private TestImages() {
    }

    /** Parse SVG text with a hardened, namespace-aware parser (DOCTYPE forbidden). */
    static Document parseSecure(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** Rasterize SVG with Batik at its intrinsic size, external resources disabled. */
    static BufferedImage rasterize(String svg) throws IOException, TranscoderException {
        return rasterize(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)), null, null);
    }

    static BufferedImage rasterize(InputStream svg, Float width, Float height)
            throws TranscoderException {
        CapturingTranscoder transcoder = new CapturingTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES,
                Boolean.FALSE);
        if (width != null) {
            transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, width);
        }
        if (height != null) {
            transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, height);
        }
        TranscoderInput input = new TranscoderInput(svg);
        input.setURI("about:blank");
        transcoder.transcode(input, null);
        return transcoder.image;
    }

    /** Count pixels whose color or alpha differs by more than the tolerance. */
    static int countDifferentPixels(BufferedImage expected, BufferedImage actual, int tolerance) {
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            throw new AssertionError("Size differs: " + expected.getWidth() + "x"
                    + expected.getHeight() + " vs " + actual.getWidth() + "x" + actual.getHeight());
        }
        int different = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (channelDistance(expected.getRGB(x, y), actual.getRGB(x, y)) > tolerance) {
                    different++;
                }
            }
        }
        return different;
    }

    static int channelDistance(int first, int second) {
        int max = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (first >>> shift) & 0xff;
            int b = (second >>> shift) & 0xff;
            max = Math.max(max, Math.abs(a - b));
        }
        return max;
    }

    static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static final class CapturingTranscoder extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage rendered, TranscoderOutput ignored) {
            image = rendered;
        }
    }
}
