package com.aresstack.drawio.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Typed view of {@code src/test/resources/drawio/corpus/corpus.xml} (#13).
 *
 * <p>The manifest is the single source of truth for the reference corpus:
 * fixture files and their SHA-256, provenance and license, structural
 * expectations per page, the official draw.io viewer references and the
 * render status that controls whether a visual mismatch fails the build.
 */
final class CorpusManifest {

    static final String RESOURCE_ROOT = "/drawio/corpus/";

    /** How strictly the visual comparison of a fixture is enforced. */
    enum RenderStatus {
        /** Must render and match the reference within {@code minScore}; failures break the build. */
        MATCH,
        /** Rendered and scored for the report, but failures are tolerated (feature work in progress). */
        PENDING;

        static RenderStatus parse(String value) {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    static final class Shape {
        final String name;
        final int count;

        Shape(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    static final class Label {
        final String cellId;
        final String text;

        Label(String cellId, String text) {
            this.cellId = cellId;
            this.text = text;
        }
    }

    static final class Page {
        final Fixture fixture;
        final int index;
        final String name;
        final int vertices;
        final int edges;
        final double minScore;
        final String png;
        final String pngSha256;
        final String svg;
        final String svgSha256;
        final List<Shape> shapes;
        final List<Label> labels;

        Page(Fixture fixture, Element element) {
            this.fixture = fixture;
            this.index = Integer.parseInt(element.getAttribute("index"));
            this.name = element.getAttribute("name");
            this.vertices = Integer.parseInt(element.getAttribute("vertices"));
            this.edges = Integer.parseInt(element.getAttribute("edges"));
            this.minScore = Double.parseDouble(element.getAttribute("minScore"));
            this.png = element.getAttribute("png");
            this.pngSha256 = element.getAttribute("pngSha256");
            this.svg = element.getAttribute("svg");
            this.svgSha256 = element.getAttribute("svgSha256");
            List<Shape> shapeList = new ArrayList<Shape>();
            for (Element shape : children(element, "shape")) {
                shapeList.add(new Shape(shape.getAttribute("name"),
                        Integer.parseInt(shape.getAttribute("count"))));
            }
            List<Label> labelList = new ArrayList<Label>();
            for (Element label : children(element, "label")) {
                labelList.add(new Label(label.getAttribute("cell"), label.getAttribute("text")));
            }
            this.shapes = Collections.unmodifiableList(shapeList);
            this.labels = Collections.unmodifiableList(labelList);
        }

        String displayName() {
            return fixture.id + (fixture.pages.size() > 1 ? "#" + index : "");
        }
    }

    static final class Fixture {
        final String id;
        final String file;
        final String sha256;
        final List<String> features;
        final RenderStatus render;
        final String issue;
        final String sameModelAs;
        final List<Page> pages;

        Fixture(Element element) {
            this.id = element.getAttribute("id");
            this.file = element.getAttribute("file");
            this.sha256 = element.getAttribute("sha256");
            List<String> featureList = new ArrayList<String>();
            for (String feature : element.getAttribute("features").trim().split("\\s+")) {
                if (!feature.isEmpty()) {
                    featureList.add(feature);
                }
            }
            this.features = Collections.unmodifiableList(featureList);
            this.render = RenderStatus.parse(element.getAttribute("render"));
            this.issue = element.getAttribute("issue");
            this.sameModelAs = element.hasAttribute("sameModelAs") ? element.getAttribute("sameModelAs") : null;
            List<Page> pageList = new ArrayList<Page>();
            for (Element page : children(element, "page")) {
                pageList.add(new Page(this, page));
            }
            this.pages = Collections.unmodifiableList(pageList);
        }

        String read() throws IOException {
            return readText(file);
        }
    }

    static final class Negative {
        final String id;
        final String file;
        final String sha256;
        /** {@code parse}, {@code render} or {@code select:<page name>}. */
        final String stage;
        /** A {@code RenderErrorCode} / parser reason name, or {@code SAFE}. */
        final String expect;
        final RenderStatus render;
        final String issue;

        Negative(Element element) {
            this.id = element.getAttribute("id");
            this.file = element.getAttribute("file");
            this.sha256 = element.getAttribute("sha256");
            this.stage = element.getAttribute("stage");
            this.expect = element.getAttribute("expect");
            this.render = element.hasAttribute("render")
                    ? RenderStatus.parse(element.getAttribute("render")) : RenderStatus.MATCH;
            this.issue = element.getAttribute("issue");
        }

        String read() throws IOException {
            return readText(file);
        }
    }

    final Map<String, String> provenance;
    final List<Fixture> fixtures;
    final List<Negative> negatives;

    private CorpusManifest(Map<String, String> provenance, List<Fixture> fixtures, List<Negative> negatives) {
        this.provenance = Collections.unmodifiableMap(provenance);
        this.fixtures = Collections.unmodifiableList(fixtures);
        this.negatives = Collections.unmodifiableList(negatives);
    }

    static CorpusManifest load() {
        try (InputStream stream = CorpusManifest.class.getResourceAsStream(RESOURCE_ROOT + "corpus.xml")) {
            if (stream == null) {
                throw new IllegalStateException("corpus.xml missing from test resources");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            Document document = factory.newDocumentBuilder().parse(stream);
            Element root = document.getDocumentElement();

            Map<String, String> provenance = new LinkedHashMap<String, String>();
            for (int i = 0; i < root.getAttributes().getLength(); i++) {
                Node attribute = root.getAttributes().item(i);
                provenance.put(attribute.getNodeName(), attribute.getNodeValue());
            }
            List<Fixture> fixtures = new ArrayList<Fixture>();
            for (Element fixture : children(root, "fixture")) {
                fixtures.add(new Fixture(fixture));
            }
            List<Negative> negatives = new ArrayList<Negative>();
            for (Element negative : children(root, "negative")) {
                negatives.add(new Negative(negative));
            }
            return new CorpusManifest(provenance, fixtures, negatives);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load corpus manifest", exception);
        }
    }

    Fixture fixture(String id) {
        for (Fixture fixture : fixtures) {
            if (fixture.id.equals(id)) {
                return fixture;
            }
        }
        throw new IllegalArgumentException("Unknown fixture " + id);
    }

    List<Page> pages() {
        List<Page> pages = new ArrayList<Page>();
        for (Fixture fixture : fixtures) {
            pages.addAll(fixture.pages);
        }
        return pages;
    }

    /** Directory of the corpus on disk (tests run from the exploded resources directory). */
    static Path directory() {
        URL url = CorpusManifest.class.getResource(RESOURCE_ROOT + "corpus.xml");
        try {
            return Paths.get(url.toURI()).getParent();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static byte[] readBytes(String relativePath) throws IOException {
        try (InputStream stream = CorpusManifest.class.getResourceAsStream(RESOURCE_ROOT + relativePath)) {
            if (stream == null) {
                throw new IOException("Corpus file missing: " + relativePath);
            }
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    static String readText(String relativePath) throws IOException {
        return new String(readBytes(relativePath), java.nio.charset.StandardCharsets.UTF_8);
    }

    static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static List<Path> listFiles(Path directory) throws IOException {
        List<Path> files = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
            java.util.Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        Collections.sort(files);
        return files;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<Element>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && name.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }
}
