package com.aresstack.drawio.consumersmoke;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.DrawioDocument;
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
 * without network access (dead SOCKS proxy), Node.js or a browser. When the
 * public rendering facade from #12 lands, extend this class to render the
 * fixture through it instead of the low-level building blocks.</p>
 */
public final class OfflineConsumerSmoke {

    private static final String[] BUNDLED_RESOURCES = {
            "drawio/drawio-shapes.js",
            "drawio/drawio-model.js",
            "drawio/drawio-canvas-adapter.js",
            "drawio/upstream/LICENSE"
    };

    private OfflineConsumerSmoke() {
    }

    public static void main(String[] args) throws Exception {
        String javaVersion = System.getProperty("java.version");

        for (String resource : BUNDLED_RESOURCES) {
            URL url = GraalJsRuntime.class.getClassLoader().getResource(resource);
            check(url != null, "bundled resource missing from consumer classpath: " + resource);
            check("jar".equals(url.getProtocol()),
                    "bundled resource must come from the packaged jar, was " + url);
        }
        checkLicenseInJar();

        DrawioDocument document = new DrawioDocumentParser().parse(readFixture("/consumer-smoke.drawio"));
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

        System.out.println("drawio-java offline consumer smoke OK on Java " + javaVersion);
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
