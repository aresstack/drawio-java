package com.aresstack.drawio.parse;

import java.util.Objects;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.aresstack.drawio.api.DiagramFormatException;

/**
 * Hand a strictly parsed XML tree to the JavaScript node model as JSON.
 *
 * <p>The XML is parsed and bounded by {@link StrictXml} (no DTDs, no external
 * entities, depth and element limits). The output is consumed by
 * {@code DrawioXml.fromJson} in {@code drawio-xml-dom.js}:
 * elements become {@code {"n": name, "a": [[name, value], ...], "c": [...]}},
 * character data becomes a JSON string. Whitespace-only text, comments and
 * processing instructions are dropped; the mxGraph codecs never read them.
 */
public final class XmlTreeJson {

    private static final int MAX_JSON_CHARACTERS = 48 * 1024 * 1024;

    private XmlTreeJson() {
    }

    /** Parse untrusted XML strictly and convert its document element. */
    public static String toJson(String xml) throws DiagramFormatException {
        return toJson(StrictXml.parse(xml).getDocumentElement());
    }

    /** Convert an already parsed element. */
    public static String toJson(Element element) throws DiagramFormatException {
        Objects.requireNonNull(element, "element");
        StringBuilder out = new StringBuilder(4096);
        writeElement(element, out);
        return out.toString();
    }

    private static void writeElement(Element element, StringBuilder out) throws DiagramFormatException {
        checkSize(out);
        out.append("{\"n\":");
        writeString(element.getNodeName(), out);
        NamedNodeMap attributes = element.getAttributes();
        if (attributes.getLength() > 0) {
            out.append(",\"a\":[");
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if (i > 0) {
                    out.append(',');
                }
                out.append('[');
                writeString(attribute.getNodeName(), out);
                out.append(',');
                writeString(attribute.getNodeValue(), out);
                out.append(']');
            }
            out.append(']');
        }
        boolean first = true;
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            short type = child.getNodeType();
            if (type == Node.ELEMENT_NODE) {
                first = openChildren(first, out);
                writeElement((Element) child, out);
            } else if ((type == Node.TEXT_NODE || type == Node.CDATA_SECTION_NODE)
                    && !child.getNodeValue().trim().isEmpty()) {
                first = openChildren(first, out);
                writeString(child.getNodeValue(), out);
            }
        }
        if (!first) {
            out.append(']');
        }
        out.append('}');
    }

    private static boolean openChildren(boolean first, StringBuilder out) {
        out.append(first ? ",\"c\":[" : ",");
        return false;
    }

    private static void checkSize(StringBuilder out) throws DiagramFormatException {
        if (out.length() > MAX_JSON_CHARACTERS) {
            throw new DiagramFormatException(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                    "The draw.io XML exceeds the configured complexity limit");
        }
    }

    static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
