package com.aresstack.drawio.corpus;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.DrawioDocument;
import com.aresstack.drawio.parse.DrawioDocumentParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural regression over the corpus: the production parser yields the
 * pages, cells, shapes, labels and terminal references the manifest expects.
 * This runs independently of rendering, so model-level regressions are caught
 * even while visual parity is still pending (#13).
 */
class CorpusStructureTest {

    private static final Pattern SHAPE = Pattern.compile("(?:^|;)shape=([^;]+)");

    private final CorpusManifest manifest = CorpusManifest.load();
    private final DrawioDocumentParser parser = new DrawioDocumentParser();

    @TestFactory
    List<DynamicTest> parsedFixturesMatchManifest() {
        List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final CorpusManifest.Fixture fixture : manifest.fixtures) {
            tests.add(DynamicTest.dynamicTest(fixture.id, () -> verify(fixture)));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> compressedFixturesDecodeToTheSameModel() {
        List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final CorpusManifest.Fixture fixture : manifest.fixtures) {
            if (fixture.sameModelAs == null) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(fixture.id + " == " + fixture.sameModelAs, () -> {
                DrawioDocument compressed = parser.parse(fixture.read());
                DrawioDocument plain = parser.parse(manifest.fixture(fixture.sameModelAs).read());
                assertEquals(plain.getPages().size(), compressed.getPages().size());
                for (int i = 0; i < plain.getPages().size(); i++) {
                    assertEquals(plain.getPages().get(i).getGraphModelXml(),
                            compressed.getPages().get(i).getGraphModelXml(), "page " + i);
                }
            }));
        }
        return tests;
    }

    private void verify(CorpusManifest.Fixture fixture) throws Exception {
        DrawioDocument document = parser.parse(fixture.read());
        assertEquals(fixture.pages.size(), document.getPages().size(), "page count");
        for (CorpusManifest.Page expected : fixture.pages) {
            DiagramPage page = document.pageByIndex(expected.index);
            assertEquals(expected.name, page.getName(), "page name");
            if (!expected.name.isEmpty()) {
                assertEquals(expected.index, document.pageByName(expected.name).getIndex());
            }
            Document model = parseModel(page.getGraphModelXml());
            NodeList cells = model.getElementsByTagName("mxCell");

            int vertices = 0;
            int edges = 0;
            Set<String> ids = new HashSet<String>();
            Map<String, String> values = new HashMap<String, String>();
            Map<String, Integer> shapes = new HashMap<String, Integer>();
            List<Element> edgeCells = new ArrayList<Element>();
            for (int i = 0; i < cells.getLength(); i++) {
                Element cell = (Element) cells.item(i);
                String id = cell.getAttribute("id");
                assertTrue(ids.add(id), "duplicate cell id " + id);
                values.put(id, cell.getAttribute("value"));
                if ("1".equals(cell.getAttribute("vertex"))) {
                    vertices++;
                    Matcher matcher = SHAPE.matcher(cell.getAttribute("style"));
                    if (matcher.find()) {
                        Integer count = shapes.get(matcher.group(1));
                        shapes.put(matcher.group(1), count == null ? 1 : count + 1);
                    }
                }
                if ("1".equals(cell.getAttribute("edge"))) {
                    edges++;
                    edgeCells.add(cell);
                }
            }
            String where = expected.displayName();
            assertEquals(expected.vertices, vertices, where + " vertex count");
            assertEquals(expected.edges, edges, where + " edge count");
            for (CorpusManifest.Shape shape : expected.shapes) {
                assertEquals(Integer.valueOf(shape.count), shapes.get(shape.name), where + " shape " + shape.name);
            }
            for (CorpusManifest.Label label : expected.labels) {
                assertEquals(label.text, values.get(label.cellId), where + " label of " + label.cellId);
            }
            for (int i = 0; i < cells.getLength(); i++) {
                Element cell = (Element) cells.item(i);
                if (cell.hasAttribute("parent")) {
                    assertTrue(ids.contains(cell.getAttribute("parent")),
                            where + " parent of " + cell.getAttribute("id"));
                }
            }
            for (Element edge : edgeCells) {
                for (String terminal : new String[] {"source", "target"}) {
                    if (edge.hasAttribute(terminal)) {
                        assertTrue(ids.contains(edge.getAttribute(terminal)),
                                where + " " + terminal + " of edge " + edge.getAttribute("id"));
                    }
                }
            }
        }
    }

    private static Document parseModel(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
