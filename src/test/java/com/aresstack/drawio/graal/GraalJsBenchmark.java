package com.aresstack.drawio.graal;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aresstack.drawio.canvas.Java2DCanvas;

/**
 * Measure GraalJS context cost for a render-shaped workload (#14).
 *
 * <p>Each "request" creates a fresh {@link GraalJsRuntime} and context,
 * loads the pinned model, shape and canvas bundles, builds an original
 * {@code mxGraphModel} with N gateway cells and paints each one through the
 * original BPMN shape code into a Java2D image. This mirrors the per-request
 * work of the graph pipeline minus view/layout, which is not on main yet.
 *
 * <p>Run with {@code ./gradlew benchmark}. Not part of the unit test suite.
 */
public final class GraalJsBenchmark {

    private static final int CELLS = Integer.getInteger("bench.cells", 100);
    private static final int WARM_RUNS = Integer.getInteger("bench.runs", 20);
    private static final int THREADS = Integer.getInteger("bench.threads", 4);
    // Select modes with -Dbench.mode=shared or =unshared to measure a cold start of each.

    private GraalJsBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println(environment());
        if ("facade".equals(System.getProperty("bench.mode"))) {
            com.aresstack.drawio.FacadeBenchmark.run(CELLS, WARM_RUNS, THREADS);
            System.out.println(heapAndThreads());
            return;
        }
        String modes = System.getProperty("bench.mode", "unshared,shared");
        for (String name : modes.split(",")) {
            boolean shared = "shared".equals(name.trim());
            String mode = shared ? "shared-engine" : "engine-per-runtime";
            long first = timeOne(shared);
            List<Long> warm = new ArrayList<Long>();
            for (int i = 0; i < WARM_RUNS; i++) {
                warm.add(timeOne(shared));
            }
            System.out.printf(Locale.ROOT, "%-20s first=%5d ms  warm median=%5d ms  p90=%5d ms%n",
                    mode, first, percentile(warm, 50), percentile(warm, 90));
            System.out.printf(Locale.ROOT, "%-20s parallel %d threads x %d: %s%n", mode, THREADS,
                    WARM_RUNS / 2, parallel(shared));
        }
        System.out.println(heapAndThreads());
    }

    static long timeOne(boolean sharedEngine) throws Exception {
        long started = System.nanoTime();
        runRequest(sharedEngine);
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    static void runRequest(boolean sharedEngine) throws Exception {
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try (Java2DCanvas canvas = new Java2DCanvas(graphics);
             GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(60), sharedEngine)) {
            DrawioHost host = new DrawioHost(resourceId -> {
                throw new IllegalArgumentException("disabled");
            }, new DrawioCanvasBridge(canvas));
            ScriptExecutionResult result = runtime.execute(sources(), host);
            if (!result.isSuccessful() || !String.valueOf(CELLS).equals(result.getOutput())) {
                throw new IllegalStateException("benchmark script failed: " + result.getErrorMessage());
            }
            if (image.getRGB(50, 50) != Color.BLACK.getRGB()) {
                throw new IllegalStateException("benchmark painted nothing");
            }
        } finally {
            graphics.dispose();
        }
    }

    static List<ScriptSource> sources() throws IOException {
        String entry = "var model = new mxGraphModel();"
                + "var layer = model.getRoot().getChildAt(0);"
                + "var shapeConstructor = mxCellRenderer.shapes['mxgraph.bpmn.gateway2'];"
                + "var c = new DrawioJavaCanvas();"
                + "c.setFillColor('#ffffff'); c.setStrokeColor('#000000');"
                + "var painted = 0;"
                + "for (var i = 0; i < " + CELLS + "; i++) {"
                + "  var cell = new mxCell('g' + i, new mxGeometry(10 + (i % 10) * 98,"
                + "      10 + Math.floor(i / 10) * 98, 80, 80),"
                + "      'shape=mxgraph.bpmn.gateway2;gwType=parallel');"
                + "  cell.setVertex(true); model.add(layer, cell);"
                + "  var shape = new shapeConstructor(); shape.style = {gwType: 'parallel'};"
                + "  var b = model.getGeometry(model.getCell(cell.getId()));"
                + "  shape.paintVertexShape(c, b.x, b.y, b.width, b.height); painted++;"
                + "}"
                + "String(painted);";
        return Arrays.asList(
                ScriptSource.inline("test-bootstrap.js", testResource("test-bootstrap.js")),
                ScriptSource.inline("model-bootstrap.js", testResource("model-bootstrap.js")),
                ScriptSource.bundled("drawio-model.js"),
                ScriptSource.bundled("drawio-shapes.js"),
                ScriptSource.bundled("drawio-canvas-adapter.js"),
                ScriptSource.inline("benchmark-entry.js", entry));
    }

    private static String parallel(boolean shared) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            int total = THREADS * Math.max(1, WARM_RUNS / 2);
            long started = System.nanoTime();
            List<Future<Long>> futures = new ArrayList<Future<Long>>();
            for (int i = 0; i < total; i++) {
                futures.add(pool.submit(() -> timeOne(shared)));
            }
            List<Long> times = new ArrayList<Long>();
            for (Future<Long> future : futures) {
                times.add(future.get());
            }
            long wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return String.format(Locale.ROOT, "wall=%d ms, %.1f req/s, per-request median=%d ms",
                    wall, total * 1000.0 / wall, percentile(times, 50));
        } finally {
            pool.shutdownNow();
        }
    }

    static String heapAndThreads() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        return "heap used after GC=" + usedMb + " MiB, live threads="
                + ManagementFactory.getThreadMXBean().getThreadCount();
    }

    static String environment() {
        return "JDK " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name")
                + "), " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
                + ", " + Runtime.getRuntime().availableProcessors() + " CPUs, max heap "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB, cells=" + CELLS;
    }

    private static long percentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<Long>(values);
        java.util.Collections.sort(sorted);
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, index));
    }

    private static String testResource(String name) throws IOException {
        try (InputStream stream = GraalJsBenchmark.class.getResourceAsStream("/drawio/" + name)) {
            if (stream == null) {
                throw new IOException("missing test resource " + name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
