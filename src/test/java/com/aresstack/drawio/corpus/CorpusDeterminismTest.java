package com.aresstack.drawio.corpus;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same input renders to the same output, sequentially and concurrently,
 * through one shared {@link DrawioRenderer} (#13). This holds for whatever
 * the renderer currently produces: identical pixels and SVG for successful
 * renders, the identical error code for failures. It therefore guards the
 * isolation of per-request GraalJS contexts independently of visual parity.
 */
class CorpusDeterminismTest {

    private static final int PARALLEL_RENDERS = 4;

    private static final CorpusManifest MANIFEST = CorpusManifest.load();
    private static DrawioRenderer renderer;
    private static ExecutorService callers;

    @BeforeAll
    static void setUp() {
        renderer = DrawioRenderer.builder().maxConcurrentRenders(PARALLEL_RENDERS).build();
        callers = Executors.newFixedThreadPool(PARALLEL_RENDERS);
    }

    @AfterAll
    static void tearDown() throws InterruptedException {
        callers.shutdownNow();
        callers.awaitTermination(10, TimeUnit.SECONDS);
        if (renderer != null) {
            renderer.close();
        }
    }

    @TestFactory
    List<DynamicTest> repeatedAndParallelRendersAreIdentical() {
        List<DynamicTest> tests = new ArrayList<DynamicTest>();
        for (final CorpusManifest.Page page : MANIFEST.pages()) {
            tests.add(DynamicTest.dynamicTest(page.displayName(), () -> verify(page)));
        }
        return tests;
    }

    private void verify(final CorpusManifest.Page page) throws Exception {
        final String xml = page.fixture.read();
        Callable<String> render = new Callable<String>() {
            @Override
            public String call() {
                return fingerprint(xml, page.index);
            }
        };
        String first = render.call();
        String second = render.call();
        assertEquals(first, second, page.displayName() + ": sequential renders differ");

        List<Future<String>> futures = new ArrayList<Future<String>>();
        for (int i = 0; i < PARALLEL_RENDERS; i++) {
            futures.add(callers.submit(render));
        }
        for (Future<String> future : futures) {
            String parallel = future.get(2, TimeUnit.MINUTES);
            assertTrue(!parallel.startsWith("error OVERLOADED"), "renderer rejected bounded parallel load");
            assertEquals(first, parallel, page.displayName() + ": parallel render differs");
        }
    }

    private static String fingerprint(String xml, int pageIndex) {
        try {
            RenderResult<BufferedImage> image = renderer.renderImage(xml, CorpusRenderTest.visualOptions(pageIndex));
            RenderResult<String> svg = renderer.renderSvg(xml, CorpusRenderTest.visualOptions(pageIndex));
            BufferedImage output = image.getOutput();
            int[] pixels = output.getRGB(0, 0, output.getWidth(), output.getHeight(), null, 0, output.getWidth());
            StringBuilder raw = new StringBuilder(pixels.length * 2);
            for (int pixel : pixels) {
                raw.append(Integer.toHexString(pixel)).append(',');
            }
            return "image " + output.getWidth() + "x" + output.getHeight() + " "
                    + CorpusManifest.sha256(raw.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    + " svg " + CorpusManifest.sha256(svg.getOutput().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    + " warnings " + image.getWarnings();
        } catch (RenderException exception) {
            return "error " + exception.getCode() + " " + exception.getCapabilityId();
        }
    }
}
