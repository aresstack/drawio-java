package com.aresstack.drawio.parse;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.aresstack.drawio.api.DiagramFormatException;

/**
 * Parse untrusted XML with bounded structural complexity and disabled entities.
 */
final class StrictXml {

    private static final int MAX_XML_CHARS = 16 * 1024 * 1024;
    private static final int MAX_DEPTH = 128;
    private static final int MAX_ELEMENTS = 100000;

    private StrictXml() {
    }

    static Document parse(String xml) throws DiagramFormatException {
        if (xml == null || xml.trim().isEmpty()) {
            throw invalidXml();
        }
        if (xml.length() > MAX_XML_CHARS) {
            throw limitExceeded();
        }

        validateStructure(xml);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            secure(factory);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External XML resources are disabled");
            });
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception exception) {
            throw new DiagramFormatException(DiagramFormatException.Reason.INVALID_XML,
                    "The draw.io XML is not readable", exception);
        }
    }

    static String serialize(Element element) throws DiagramFormatException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");

            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(output));
            if (output.getBuffer().length() > MAX_XML_CHARS) {
                throw limitExceeded();
            }
            return output.toString();
        } catch (DiagramFormatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DiagramFormatException(DiagramFormatException.Reason.INVALID_XML,
                    "The draw.io page cannot be serialized", exception);
        }
    }

    private static void validateStructure(String xml) throws DiagramFormatException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External XML resources are disabled");
            });
            reader.setContentHandler(new BoundedStructureHandler());
            reader.setErrorHandler(new DefaultHandler());
            reader.parse(new InputSource(new StringReader(xml)));
        } catch (XmlLimitException exception) {
            throw limitExceeded();
        } catch (Exception exception) {
            throw new DiagramFormatException(DiagramFormatException.Reason.INVALID_XML,
                    "The draw.io XML is not readable", exception);
        }
    }

    private static void secure(DocumentBuilderFactory factory) throws Exception {
        factory.setNamespaceAware(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    }

    private static DiagramFormatException invalidXml() {
        return new DiagramFormatException(DiagramFormatException.Reason.INVALID_XML,
                "The draw.io XML is empty or malformed");
    }

    private static DiagramFormatException limitExceeded() {
        return new DiagramFormatException(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                "The draw.io XML exceeds the configured complexity limit");
    }

    private static final class BoundedStructureHandler extends DefaultHandler {
        private int depth;
        private int elements;

        @Override
        public void startElement(String uri, String localName, String qName,
                                 org.xml.sax.Attributes attributes) throws SAXException {
            depth++;
            elements++;
            if (depth > MAX_DEPTH || elements > MAX_ELEMENTS) {
                throw new XmlLimitException();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            depth--;
        }
    }

    private static final class XmlLimitException extends SAXException {
        private static final long serialVersionUID = 1L;
    }
}
