package com.aresstack.drawio.render;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.batik.svggen.ImageHandlerBase64Encoder;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Serialize a recorded scene to SVG through Batik's {@link SVGGraphics2D}.
 *
 * <p>The DOM comes from the JDK, not from Batik's DOM implementation, and is
 * written with a secure identity transformer: no DOCTYPE, no external DTD, no
 * comments. Raster images are embedded as {@code data:} URIs by Batik's
 * base64 handler, so the document never references external resources.
 */
final class SvgOutput {

    static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    static final String XLINK_NAMESPACE = "http://www.w3.org/1999/xlink";

    private SvgOutput() {
    }

    static void write(CanvasRecording recording, OutputGeometry geometry,
                      OutputOptions options, Writer output) throws IOException {
        Document document = newDocument();
        SVGGeneratorContext context = SVGGeneratorContext.createDefault(document);
        context.setComment(null);
        context.setEmbeddedFontsOn(false);
        context.setImageHandler(new PngDataUriImageHandler());
        context.setPrecision(4);
        context.setExtensionHandler(new SvgGradients());

        SVGGraphics2D graphics = new OutlineTextSvgGraphics2D(context, options.isSvgTextAsShapes());
        try {
            graphics.setSVGCanvasSize(SceneRenderer.size(geometry));
            SceneRenderer.paint(graphics, recording, geometry, options, false);

            Element root = document.getDocumentElement();
            graphics.getRoot(root);
            decorateRoot(root, geometry);
            replaceLogicalFontFamilies(root);
            serialize(document, new LimitedWriter(output, options.getMaxSvgChars()));
            output.flush();
        } finally {
            graphics.dispose();
        }
    }

    private static void decorateRoot(Element root, OutputGeometry geometry) {
        String width = Integer.toString(geometry.getWidth());
        String height = Integer.toString(geometry.getHeight());
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", SVG_NAMESPACE);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xlink", XLINK_NAMESPACE);
        root.setAttributeNS(null, "version", "1.1");
        root.setAttributeNS(null, "width", width);
        root.setAttributeNS(null, "height", height);
        root.setAttributeNS(null, "viewBox", String.format(Locale.ROOT, "0 0 %s %s", width, height));
        root.removeAttributeNS(null, "contentScriptType");
        root.removeAttributeNS(null, "zoomAndPan");
        root.removeAttributeNS(null, "contentStyleType");
        root.removeAttributeNS(null, "preserveAspectRatio");
    }

    /**
     * Replace Java logical font names, which browsers do not know, with the
     * equivalent CSS generic families. Only relevant for {@code <text>}
     * output ({@code svgTextAsShapes(false)}).
     */
    private static void replaceLogicalFontFamilies(Element root) {
        org.w3c.dom.NodeList all = root.getElementsByTagNameNS("*", "*");
        replaceFontFamily(root);
        for (int index = 0; index < all.getLength(); index++) {
            replaceFontFamily((Element) all.item(index));
        }
    }

    private static void replaceFontFamily(Element element) {
        if (!element.hasAttributeNS(null, "font-family")) {
            return;
        }
        String family = element.getAttributeNS(null, "font-family").replace("'", "").trim();
        String generic;
        switch (family.toLowerCase(Locale.ROOT)) {
            case "dialog":
            case "sansserif":
                generic = "sans-serif";
                break;
            case "serif":
                generic = "serif";
                break;
            case "monospaced":
            case "dialoginput":
                generic = "monospace";
                break;
            default:
                return;
        }
        element.setAttributeNS(null, "font-family", generic);
    }

    private static Document newDocument() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DOMImplementation implementation = factory.newDocumentBuilder().getDOMImplementation();
            return implementation.createDocument(SVG_NAMESPACE, "svg", null);
        } catch (ParserConfigurationException exception) {
            throw new IOException("Cannot create SVG document", exception);
        }
    }

    private static void serialize(Document document, Writer output) throws IOException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
            trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
            transformer.transform(new DOMSource(document), new StreamResult(output));
        } catch (TransformerException exception) {
            if (exception.getCause() instanceof OutputLimitException) {
                throw (OutputLimitException) exception.getCause();
            }
            Throwable cause = exception.getException();
            if (cause instanceof OutputLimitException) {
                throw (OutputLimitException) cause;
            }
            throw new IOException("Cannot serialize SVG document", exception);
        }
    }

    private static void trySetAttribute(TransformerFactory factory, String name) {
        try {
            factory.setAttribute(name, "");
        } catch (IllegalArgumentException ignored) {
            // Not every JAXP implementation supports these attributes.
        }
    }

    /**
     * Draw outlined text with {@link java.awt.font.TextLayout} so underline
     * and strikethrough attributes are kept.
     *
     * <p>Batik's text-as-shapes path converts strings to glyph outlines,
     * which drop text decorations that the raster output paints.
     */
    static final class OutlineTextSvgGraphics2D extends SVGGraphics2D {

        OutlineTextSvgGraphics2D(SVGGeneratorContext context, boolean textAsShapes) {
            super(context, textAsShapes);
        }

        private OutlineTextSvgGraphics2D(OutlineTextSvgGraphics2D source) {
            super(source);
        }

        @Override
        public java.awt.Graphics create() {
            return new OutlineTextSvgGraphics2D(this);
        }

        @Override
        public void drawString(String text, float x, float y) {
            if (!textAsShapes || text == null || text.isEmpty()) {
                super.drawString(text, x, y);
                return;
            }
            java.awt.font.TextLayout layout =
                    new java.awt.font.TextLayout(text, getFont(), getFontRenderContext());
            fill(layout.getOutline(java.awt.geom.AffineTransform.getTranslateInstance(x, y)));
        }
    }

    /**
     * Embed raster images as {@code data:image/png;base64} URIs using the JDK
     * PNG writer.
     *
     * <p>Batik's own encoder looks up an "image/png" writer that only the
     * separate batik-codec jar registers, and fails with a
     * NullPointerException without it. Only the encoding step is replaced;
     * Batik still builds the {@code <image>} element and the base64 stream.
     */
    static final class PngDataUriImageHandler extends ImageHandlerBase64Encoder {
        @Override
        public void encodeImage(RenderedImage image, OutputStream output)
                throws SVGGraphics2DIOException {
            try {
                SceneRenderer.writePng(toBufferedImage(image), OutputOptions.CSS_DPI, output);
            } catch (IOException exception) {
                throw new SVGGraphics2DIOException(exception);
            }
        }

        private static BufferedImage toBufferedImage(RenderedImage image) {
            if (image instanceof BufferedImage) {
                return (BufferedImage) image;
            }
            BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = copy.createGraphics();
            try {
                graphics.drawRenderedImage(image, null);
            } finally {
                graphics.dispose();
            }
            return copy;
        }
    }

    /**
     * Stop serialization once the configured SVG size limit is exceeded.
     */
    private static final class LimitedWriter extends Writer {
        private final Writer delegate;
        private final long limit;
        private long written;

        private LimitedWriter(Writer delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            written += length;
            if (written > limit) {
                throw new OutputLimitException(OutputLimitException.Limit.SVG_SIZE,
                        "SVG output exceeds " + limit + " characters");
            }
            delegate.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
