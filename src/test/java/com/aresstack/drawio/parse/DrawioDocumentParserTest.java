package com.aresstack.drawio.parse;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.DiagramFormatException;
import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.DrawioDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawioDocumentParserTest {

    private static final String MODEL =
            "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>"
                    + "<mxCell id=\"shape\" value=\"Grüße\" vertex=\"1\" parent=\"1\"/>"
                    + "</root></mxGraphModel>";

    private final DrawioDocumentParser parser = new DrawioDocumentParser();

    @Test
    void readsDirectGraphModelAsSinglePage() throws Exception {
        DrawioDocument document = parser.parse(MODEL);

        assertEquals(1, document.getPages().size());
        assertEquals("Page 1", document.firstPage().getName());
        assertTrue(document.firstPage().getGraphModelXml().contains("Grüße"));
        assertEquals(0, document.firstPage().getIndex());
    }

    @Test
    void readsUncompressedNestedModelAndPreservesMetadata() throws Exception {
        String xml = "<mxfile><diagram id=\"alpha\" name=\"First\">"
                + MODEL + "</diagram></mxfile>";

        DiagramPage page = parser.parse(xml).firstPage();

        assertEquals("alpha", page.getId());
        assertEquals("First", page.getName());
        assertTrue(page.getGraphModelXml().contains("mxGraphModel"));
    }

    @Test
    void readsEscapedUncompressedTextContent() throws Exception {
        String escaped = MODEL.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        DrawioDocument document = parser.parse("<mxfile><diagram>"
                + escaped + "</diagram></mxfile>");

        assertEquals(1, document.getPages().size());
        assertTrue(document.firstPage().getGraphModelXml().contains("Grüße"));
    }

    @Test
    void decompressesOriginalDrawioRawDeflatePages() throws Exception {
        String document = "<mxfile compressed=\"true\"><diagram id=\"compressed\">"
                + compress(MODEL) + "</diagram></mxfile>";

        DiagramPage page = parser.parse(document).firstPage();

        assertEquals("compressed", page.getId());
        assertTrue(page.getGraphModelXml().contains("Grüße"));
    }

    @Test
    void selectsMultiplePagesByIndexAndName() throws Exception {
        String xml = "<mxfile><diagram id=\"one\" name=\"Overview\">"
                + MODEL + "</diagram><diagram id=\"two\" name=\"Details\">"
                + compress(MODEL) + "</diagram></mxfile>";

        DrawioDocument document = parser.parse(xml);

        assertEquals(2, document.getPages().size());
        assertEquals("one", document.firstPage().getId());
        assertEquals("two", document.pageByIndex(1).getId());
        assertEquals("two", document.pageByName("Details").getId());
        assertEquals("Details", document.pageByName("Details").getName());
        assertThrows(UnsupportedOperationException.class,
                () -> document.getPages().clear());
    }

    @Test
    void reportsUnknownAndAmbiguousPageSelection() throws Exception {
        DrawioDocument document = parser.parse("<mxfile>"
                + "<diagram name=\"Same\">" + MODEL + "</diagram>"
                + "<diagram name=\"Same\">" + MODEL + "</diagram></mxfile>");

        assertReason(DiagramFormatException.Reason.PAGE_NOT_FOUND,
                () -> document.pageByIndex(-1));
        assertReason(DiagramFormatException.Reason.PAGE_NOT_FOUND,
                () -> document.pageByIndex(2));
        assertReason(DiagramFormatException.Reason.PAGE_NOT_FOUND,
                () -> document.pageByName("Missing"));
        assertReason(DiagramFormatException.Reason.AMBIGUOUS_PAGE,
                () -> document.pageByName("Same"));
    }

    @Test
    void rejectsUnknownRootAndEmptyDocument() {
        assertReason(DiagramFormatException.Reason.UNSUPPORTED_FORMAT,
                () -> parser.parse("<random/>"));
        assertReason(DiagramFormatException.Reason.INVALID_DIAGRAM,
                () -> parser.parse("<mxfile/>"));
        assertReason(DiagramFormatException.Reason.INVALID_XML,
                () -> parser.parse(""));
        assertReason(DiagramFormatException.Reason.INVALID_XML,
                () -> parser.parse(null));
    }

    @Test
    void rejectsInvalidPageContentsAndCompression() {
        assertReason(DiagramFormatException.Reason.INVALID_DIAGRAM,
                () -> parser.parse("<mxfile><diagram/></mxfile>"));
        assertReason(DiagramFormatException.Reason.INVALID_COMPRESSION,
                () -> parser.parse("<mxfile><diagram>not-base64!</diagram></mxfile>"));
        assertReason(DiagramFormatException.Reason.INVALID_DIAGRAM,
                () -> parser.parse("<mxfile><diagram>&lt;wrong/&gt;</diagram></mxfile>"));
    }

    @Test
    void rejectsDoctypeEntitiesAndMalformedXml() {
        String malicious = "<!DOCTYPE foo [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
                + "<mxGraphModel><root><mxCell value=\"&xxe;\"/></root></mxGraphModel>";

        assertReason(DiagramFormatException.Reason.INVALID_XML,
                () -> parser.parse(malicious));
        assertReason(DiagramFormatException.Reason.INVALID_XML,
                () -> parser.parse("<mxGraphModel><root>"));
    }

    @Test
    void rejectsExcessiveInputAndNesting() {
        char[] characters = new char[17 * 1024 * 1024];
        Arrays.fill(characters, 'x');
        assertReason(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                () -> parser.parse(new String(characters)));

        StringBuilder nested = new StringBuilder("<mxGraphModel>");
        for (int depth = 0; depth < 130; depth++) {
            nested.append("<group>");
        }
        for (int depth = 0; depth < 130; depth++) {
            nested.append("</group>");
        }
        nested.append("</mxGraphModel>");
        assertReason(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                () -> parser.parse(nested.toString()));
    }

    @Test
    void rejectsDeflateBombBeforeMaterializingExpandedXml() throws Exception {
        char[] repeated = new char[17 * 1024 * 1024];
        Arrays.fill(repeated, 'x');
        String hugeXml = "<mxGraphModel><root><mxCell value=\""
                + new String(repeated) + "\"/></root></mxGraphModel>";
        String compressed = compress(hugeXml);

        assertTrue(compressed.length() < 50000, "Test fixture must compress efficiently");
        assertReason(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                () -> parser.parse("<mxfile><diagram>" + compressed + "</diagram></mxfile>"));
    }

    @Test
    void capsTheTotalInflatedBytesAcrossAllPages() throws Exception {
        char[] repeated = new char[4 * 1024 * 1024];
        Arrays.fill(repeated, 'x');

        String expandedXml = "<mxGraphModel><root><mxCell value=\""
                + new String(repeated) + "\"/></root></mxGraphModel>";
        String compressedPage = compress(expandedXml);
        String page = "<diagram>" + compressedPage + "</diagram>";

        assertTrue(compressedPage.length() < 10000,
                "Regression must exercise a high-compression-ratio document");
        DrawioDocument accepted = parser.parse("<mxfile>" + page + page + "</mxfile>");
        assertEquals(2, accepted.getPages().size());

        assertReason(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                () -> parser.parse("<mxfile>" + page + page + page + page + "</mxfile>"));
    }

    @Test
    void rejectsInvalidUtf8AfterPercentDecoding() throws Exception {
        assertReason(DiagramFormatException.Reason.INVALID_COMPRESSION,
                () -> parseCompressedPage(percentEncodedModel("%FF")));
        assertReason(DiagramFormatException.Reason.INVALID_COMPRESSION,
                () -> parseCompressedPage(percentEncodedModel("%C3%28")));
        assertReason(DiagramFormatException.Reason.INVALID_COMPRESSION,
                () -> parseCompressedPage(percentEncodedModel("%ED%A0%80")));
        assertReason(DiagramFormatException.Reason.INVALID_COMPRESSION,
                () -> parseCompressedPage(percentEncodedModel("%G1")));
    }

    @Test
    void decodesValidPercentEncodedUtf8WithoutReplacementCharacters() throws Exception {
        String page = percentEncodedModel("%C3%BC");
        DrawioDocument parsed = parseCompressedPage(page);

        assertTrue(parsed.firstPage().getGraphModelXml().contains("Grüße".substring(2, 3)));
        assertFalse(parsed.firstPage().getGraphModelXml().contains("\uFFFD"));
    }

    @Test
    void rejectsTooManyPages() {
        StringBuilder xml = new StringBuilder("<mxfile>");
        for (int page = 0; page < 101; page++) {
            xml.append("<diagram>").append(MODEL).append("</diagram>");
        }
        xml.append("</mxfile>");

        assertReason(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                () -> parser.parse(xml.toString()));
    }

    @Test
    void rejectsTruncatedCompressedInput() throws Exception {
        String encoded = compress(MODEL);
        String truncated = encoded.substring(0, encoded.length() / 2);

        assertReason(DiagramFormatException.Reason.INVALID_COMPRESSION,
                () -> parser.parse("<mxfile><diagram>" + truncated + "</diagram></mxfile>"));
    }

    private DrawioDocument parseCompressedPage(String percentEncodedXml) throws Exception {
        return parser.parse("<mxfile><diagram>"
                + compressEscaped(percentEncodedXml) + "</diagram></mxfile>");
    }

    private String percentEncodedModel(String escapedCharacter) throws Exception {
        return URLEncoder.encode("<mxGraphModel><root><mxCell value=\"Gr", "UTF-8")
                + escapedCharacter
                + URLEncoder.encode("ße\"/></root></mxGraphModel>", "UTF-8");
    }

    private static String compress(String xml) throws Exception {
        return compressEscaped(URLEncoder.encode(xml, StandardCharsets.UTF_8.name()));
    }

    private static String compressEscaped(String escaped) throws Exception {
        byte[] input = escaped.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];

        try {
            while (!deflater.finished()) {
                int size = deflater.deflate(buffer);
                assertTrue(size > 0, "Deflater must make progress");
                output.write(buffer, 0, size);
            }
        } finally {
            deflater.end();
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private void assertReason(DiagramFormatException.Reason expected,
                              CheckedAction action) {
        DiagramFormatException exception =
                assertThrows(DiagramFormatException.class, action::run);
        assertEquals(expected, exception.getReason());
        assertFalse(exception.getMessage().contains("Grüße"));
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
