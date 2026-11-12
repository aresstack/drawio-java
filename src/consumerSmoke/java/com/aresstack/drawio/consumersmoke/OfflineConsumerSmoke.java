package com.aresstack.drawio.consumersmoke;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.DrawioDocument;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.canvas.Java2DCanvas;
import com.aresstack.drawio.graal.DrawioHost;
import com.aresstack.drawio.graal.GraalJsRuntime;
import com.aresstack.drawio.graal.ScriptExecutionResult;
import com.aresstack.drawio.parse.DrawioDocumentParser;

/**
 * Minimal external consumer of the packaged drawio-java jar (#15).
 *
 * <p>Runs with only the jar and its runtime dependencies on the classpath,
 * without network access (dead SOCKS proxy), Node.js or a browser.</p>
 *
 * <p>The single argument says which artifacts are on the classpath:
 * {@code main-only} (just {@code drawio-java}) or {@code with-stencils}
 * ({@code drawio-java} plus the optional {@code drawio-java-stencils}). In
 * both runs the JavaScript shapes render; the XML stencil
 * {@code mxgraph.flowchart.decision} renders only with the stencil artifact
 * and fails as {@code UNSUPPORTED_STENCIL} without it.</p>
 */
public final class OfflineConsumerSmoke {

    private static final String[] BUNDLED_RESOURCES = {
            "drawio/drawio-headless-env.js",
            "drawio/drawio-graph.js",
            "drawio/drawio-xml-dom.js",
            "drawio/drawio-headless.js",
            "drawio/drawio-graph-ports.js",
            "drawio/drawio-shape-library.js",
            "drawio/drawio-canvas-adapter.js",
            "drawio/drawio-render.js",
            "drawio/drawio-stencil-runtime.js",
            "drawio/upstream/LICENSE"
    };

    private static final String STENCIL_DATA = "drawio/drawio-stencils.js";

    /** Bundles of the old stub-based tests; the production pipeline never loads them. */
    private static final String[] TEST_ONLY_RESOURCES = {
            "drawio/drawio-model.js",
            "drawio/drawio-shapes.js"
    };

    /** An XML stencil from flowchart.xml in drawio-java-stencils (a diamond). */
    private static final String STENCIL_DIAGRAM = "<mxGraphModel><root><mxCell id=\"0\"/>"
            + "<mxCell id=\"1\" parent=\"0\"/>"
            + "<mxCell id=\"decision\" value=\"\" style=\"shape=mxgraph.flowchart.decision;"
            + "fillColor=#ffffff;strokeColor=#000000;strokeWidth=2;\" vertex=\"1\" parent=\"1\">"
            + "<mxGeometry x=\"10\" y=\"10\" width=\"80\" height=\"80\" as=\"geometry\"/></mxCell>"
            + "</root></mxGraphModel>";

    private OfflineConsumerSmoke() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "main-only" : args[0];
        check("main-only".equals(mode) || "with-stencils".equals(mode), "unknown mode " + mode);
        boolean withStencils = "with-stencils".equals(mode);
        String javaVersion = System.getProperty("java.version");

        for (String resource : BUNDLED_RESOURCES) {
            URL url = GraalJsRuntime.class.getClassLoader().getResource(resource);
            check(url != null, "bundled resource missing from consumer classpath: " + resource);
            check("jar".equals(url.getProtocol()),
                    "bundled resource must come from the packaged jar, was " + url);
        }
        checkLicenseInJar();
        for (String resource : TEST_ONLY_RESOURCES) {
            check(GraalJsRuntime.class.getClassLoader().getResource(resource) == null,
                    "test-only bundle must not be shipped: " + resource);
        }
        URL stencilData = GraalJsRuntime.class.getClassLoader().getResource(STENCIL_DATA);
        if (withStencils) {
            check(stencilData != null, "drawio-java-stencils is on the classpath but " + STENCIL_DATA + " is not");
            check("jar".equals(stencilData.getProtocol()),
                    STENCIL_DATA + " must come from a packaged jar, was " + stencilData);
        } else {
            check(stencilData == null, "the main jar must not contain " + STENCIL_DATA + ", found " + stencilData);
        }

        String fixture = readFixture("/consumer-smoke.drawio");
        DrawioDocument document = new DrawioDocumentParser().parse(fixture);
        DiagramPage page = document.firstPage();
        check(page.getGraphModelXml().contains("mxgraph.bpmn.gateway2"), "fixture page lost its BPMN gateway");

        DrawioHost host = new DrawioHost(resourceId -> {
            throw new IllegalArgumentException("no resources in smoke test");
        });
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(30))) {
            ScriptExecutionResult result = runtime.execute("consumer-smoke.js", "String(6 * 7)", host);
            check(result.isSuccessful(), "GraalJS evaluation failed: " + result.getErrorMessage());
            check("42".equals(result.getOutput()), "unexpected GraalJS output: " + result.getOutput());
        }

        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try (DrawioCanvas canvas = new Java2DCanvas(graphics)) {
            canvas.setFillColor("#ff0000");
            canvas.rect(5, 5, 10, 10);
            canvas.fill();
        } finally {
            graphics.dispose();
        }
        check(image.getRGB(10, 10) == 0xFFFF0000, "Java2D canvas did not paint");
        check(image.getRGB(1, 1) == 0, "Java2D canvas painted outside the rectangle");

        try (DrawioRenderer renderer = DrawioRenderer.create()) {
            // JavaScript shapes (mxBpmnShape2.js) need no stencil data.
            RenderResult<BufferedImage> gateways = renderer.renderImage(fixture);
            check(gateways.isComplete() && gateways.getWarnings().isEmpty(),
                    "BPMN gateway2 fixture rendered incompletely: " + gateways.getWarnings());
            check(opaquePixels(gateways.getOutput()) > 0, "BPMN gateway2 fixture rendered nothing");

            if (withStencils) {
                RenderResult<BufferedImage> decision = renderer.renderImage(STENCIL_DIAGRAM);
                check(decision.isComplete() && decision.getWarnings().isEmpty(),
                        "XML stencil rendered incompletely: " + decision.getWarnings());
                BufferedImage out = decision.getOutput();
                check(opaquePixels(out) > 0, "XML stencil rendered nothing");
                // A diamond leaves the corners of its bounds empty; a
                // rectangle fallback would paint them.
                check((out.getRGB(1, 1) >>> 24) == 0, "XML stencil painted the corner of its bounds");
                check(out.getRGB(out.getWidth() / 2, out.getHeight() / 2) == 0xFFFFFFFF,
                        "XML stencil is not filled white in its centre");
            } else {
                try {
                    renderer.renderImage(STENCIL_DIAGRAM);
                    throw new IllegalStateException("consumer smoke failed: XML stencil rendered without "
                            + "drawio-java-stencils");
                } catch (RenderException expected) {
                    check(expected.getCode() == RenderErrorCode.UNSUPPORTED_STENCIL,
                            "expected UNSUPPORTED_STENCIL, was " + expected.getCode() + ": "
                                    + expected.getMessage());
                    check("stencil:mxgraph.flowchart.decision".equals(expected.getCapabilityId()),
                            "unexpected capability " + expected.getCapabilityId());
                }
            }
        }

        System.out.println("drawio-java offline consumer smoke OK (" + mode + ") on Java " + javaVersion);
    }

    private static int opaquePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The packaged jar itself (not a dependency) must carry the MIT license and the notices. */
    private static void checkLicenseInJar() throws Exception {
        java.io.File jarFile = new java.io.File(
                GraalJsRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.zip.ZipEntry license = jar.getEntry("META-INF/LICENSE");
            check(license != null, "META-INF/LICENSE missing from " + jarFile.getName());
            try (InputStream in = jar.getInputStream(license)) {
                java.io.ByteArrayOutputStream text = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    text.write(buffer, 0, n);
                }
                check(new String(text.toByteArray(), "UTF-8").startsWith("MIT License"),
                        "META-INF/LICENSE is not the MIT license");
            }
            check(jar.getEntry("META-INF/THIRD-PARTY-NOTICES.md") != null,
                    "META-INF/THIRD-PARTY-NOTICES.md missing from " + jarFile.getName());
        }
    }

    private static String readFixture(String path) throws IOException {
        try (InputStream stream = OfflineConsumerSmoke.class.getResourceAsStream(path)) {
            check(stream != null, "fixture missing: " + path);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("consumer smoke failed: " + message);
        }
    }
}
