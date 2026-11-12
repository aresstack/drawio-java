package com.aresstack.drawio.parse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.aresstack.drawio.api.DiagramFormatException;
import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.DrawioDocument;

/**
 * Read original draw.io XML and recover page models without UI or network access.
 */
public final class DrawioDocumentParser {

    private static final int MAX_SOURCE_CHARACTERS = 16 * 1024 * 1024;
    private static final int MAX_UNPACKED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_PAGES = 100;

    private final DrawioPercentDecoder percentDecoder = new DrawioPercentDecoder();

    public DrawioDocument parse(String xml) throws DiagramFormatException {
        if (xml == null) {
            throw formatError(DiagramFormatException.Reason.INVALID_XML,
                    "The draw.io source is missing");
        }
        if (xml.length() > MAX_SOURCE_CHARACTERS) {
            throw formatError(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                    "The draw.io source exceeds the input limit");
        }

        Document document = StrictXml.parse(xml);
        Element root = document.getDocumentElement();

        if ("mxGraphModel".equals(root.getTagName())) {
            List<DiagramPage> pages = new ArrayList<DiagramPage>();
            pages.add(new DiagramPage(0, "", "Page 1", StrictXml.serialize(root)));
            return new DrawioDocument(pages);
        }

        if (!"mxfile".equals(root.getTagName())) {
            throw formatError(DiagramFormatException.Reason.UNSUPPORTED_FORMAT,
                    "Expected mxGraphModel or mxfile");
        }
        return readMxfile(root);
    }

    private DrawioDocument readMxfile(Element root) throws DiagramFormatException {
        List<DiagramPage> pages = new ArrayList<DiagramPage>();
        DiagramReadBudget budget = new DiagramReadBudget(MAX_UNPACKED_BYTES);

        for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)
                    || !"diagram".equals(((Element) child).getTagName())) {
                continue;
            }
            if (pages.size() >= MAX_PAGES) {
                throw formatError(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                        "The draw.io document contains too many pages");
            }
            Element diagram = (Element) child;
            pages.add(readPage(diagram, pages.size(), budget));
        }

        if (pages.isEmpty()) {
            throw formatError(DiagramFormatException.Reason.INVALID_DIAGRAM,
                    "The draw.io document does not contain a page");
        }
        return new DrawioDocument(pages);
    }

    private DiagramPage readPage(Element diagram, int index, DiagramReadBudget budget)
            throws DiagramFormatException {
        Element nested = directGraphModel(diagram);
        String graphModel;
        int expandedBytes = 0;

        if (nested != null) {
            graphModel = StrictXml.serialize(nested);
        } else {
            String data = diagram.getTextContent();
            if (data == null || data.trim().isEmpty()) {
                throw formatError(DiagramFormatException.Reason.INVALID_DIAGRAM,
                        "The draw.io page does not contain diagram data");
            }

            String content = data.trim();
            String unpacked;
            if (content.startsWith("<")) {
                unpacked = content;
            } else {
                ExpandedPage expanded = unpack(content, budget.remainingBytes());
                unpacked = expanded.graphModelXml;
                expandedBytes = expanded.inflatedBytes;
            }
            Element model = StrictXml.parse(unpacked).getDocumentElement();
            if (!"mxGraphModel".equals(model.getTagName())) {
                throw formatError(DiagramFormatException.Reason.INVALID_DIAGRAM,
                        "The draw.io page does not contain an mxGraphModel");
            }
            graphModel = StrictXml.serialize(model);
        }

        budget.charge(expandedBytes, graphModel.length());

        String name = diagram.getAttribute("name");
        if (name.isEmpty()) {
            name = "Page " + (index + 1);
        }
        return new DiagramPage(index, diagram.getAttribute("id"), name, graphModel);
    }

    private Element directGraphModel(Element diagram) throws DiagramFormatException {
        Element found = null;
        for (Node child = diagram.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                Element element = (Element) child;
                if (!"mxGraphModel".equals(element.getTagName()) || found != null) {
                    throw formatError(DiagramFormatException.Reason.INVALID_DIAGRAM,
                            "The draw.io page contains unexpected nested elements");
                }
                found = element;
            }
        }
        return found;
    }

    private ExpandedPage unpack(String compressed, int remainingBytes)
            throws DiagramFormatException {
        if (compressed.length() > MAX_SOURCE_CHARACTERS) {
            throw formatError(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                    "The compressed diagram exceeds the input limit");
        }
        byte[] zipped;
        try {
            zipped = Base64.getDecoder().decode(compressed.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException exception) {
            throw new DiagramFormatException(DiagramFormatException.Reason.INVALID_COMPRESSION,
                    "Invalid Base64 data in draw.io page", exception);
        }

        byte[] expanded = inflate(zipped, remainingBytes);
        String xml = percentDecoder.decode(expanded);
        if (xml.length() > MAX_SOURCE_CHARACTERS) {
            throw formatError(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                    "The uncompressed diagram exceeds the input limit");
        }
        return new ExpandedPage(xml, expanded.length);
    }

    private byte[] inflate(byte[] compressed, int remainingBytes)
            throws DiagramFormatException {
        Inflater inflater = new Inflater(true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        try {
            // Inflater(true) documents an extra dummy input byte for raw DEFLATE.
            byte[] input = java.util.Arrays.copyOf(compressed, compressed.length + 1);
            inflater.setInput(input);
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count <= 0) {
                    throw formatError(DiagramFormatException.Reason.INVALID_COMPRESSION,
                            "Compressed draw.io page is incomplete or invalid");
                }
                if (output.size() > remainingBytes - count) {
                    throw formatError(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                            "Expanded draw.io page exceeds the output limit");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (DataFormatException exception) {
            throw new DiagramFormatException(DiagramFormatException.Reason.INVALID_COMPRESSION,
                    "Compressed draw.io page is invalid", exception);
        } finally {
            inflater.end();
        }
    }

    private DiagramFormatException formatError(DiagramFormatException.Reason reason,
                                               String message) {
        return new DiagramFormatException(reason, message);
    }

    private static final class ExpandedPage {
        private final String graphModelXml;
        private final int inflatedBytes;

        private ExpandedPage(String graphModelXml, int inflatedBytes) {
            this.graphModelXml = graphModelXml;
            this.inflatedBytes = inflatedBytes;
        }
    }
}
