package com.aresstack.drawio.render;

import java.io.IOException;
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
        context.setImageHandler(new ImageHandlerBase64Encoder());
        context.setPrecision(4);

        SVGGraphics2D graphics = new SVGGraphics2D(context, options.isSvgTextAsShapes());
        try {
            graphics.setSVGCanvasSize(SceneRenderer.size(geometry));
            SceneRenderer.paint(graphics, recording, geometry, options, false);

            Element root = document.getDocumentElement();
            graphics.getRoot(root);
            decorateRoot(root, geometry);
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
