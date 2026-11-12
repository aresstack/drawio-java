package com.aresstack.drawio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;

/**
 * Measure full requests through the public facade and the production
 * pipeline (#9): parse, original mxGraph view, recording, PNG and SVG (#14).
 *
 * <p>Run with {@code ./gradlew benchmark -Dbench.mode=facade [-Dbench.cells=N]}.
 */
public final class FacadeBenchmark {

    private FacadeBenchmark() {
    }

    public static void run(int vertices, int runs, int threads) throws Exception {
        String xml = grid(vertices);
        try (DrawioRenderer renderer = DrawioRenderer.builder()
                .maxConcurrentRenders(threads).maxQueuedRenders(1000).build()) {
            long first = time(() -> renderer.renderPng(xml));
            List<Long> png = new ArrayList<Long>();
            List<Long> svg = new ArrayList<Long>();
            int pngBytes = 0;
            int svgChars = 0;
            for (int i = 0; i < runs; i++) {
                long started = System.nanoTime();
                RenderResult<byte[]> result = renderer.renderPng(xml);
                png.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                pngBytes = result.getOutput().length;
                started = System.nanoTime();
                RenderResult<String> vector = renderer.renderSvg(xml, RenderOptions.defaults());
                svg.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                svgChars = vector.getOutput().length();
            }
            System.out.printf(Locale.ROOT,
                    "facade " + System.getProperty("bench.diagram", "grid") + " %d vertices + %d edges: first=%d ms, PNG median=%d ms p90=%d ms (%d KiB),"
                            + " SVG median=%d ms (%d KiB)%n",
                    vertices, vertices - 1, first, percentile(png, 50), percentile(png, 90),
                    pngBytes / 1024, percentile(svg, 50), svgChars / 1024);

            ExecutorService callers = Executors.newFixedThreadPool(threads);
            try {
                int total = threads * Math.max(1, runs / 2);
                long started = System.nanoTime();
                List<Future<?>> futures = new ArrayList<Future<?>>();
                for (int i = 0; i < total; i++) {
                    futures.add(callers.submit(() -> renderer.renderPng(xml)));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
                long wall = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                System.out.printf(Locale.ROOT, "facade parallel %d workers x %d PNG: %.1f req/s%n",
                        threads, total, total * 1000.0 / wall);
            } finally {
                callers.shutdownNow();
            }
        }
    }

    /**
     * A grid of labelled vertices connected in a chain by orthogonal edges.
     * With {@code -Dbench.diagram=bpmn} every second vertex is an original
     * BPMN {@code gateway2} (alternating parallel/exclusive/complex).
     */
    static String grid(int vertices) {
        boolean bpmn = "bpmn".equals(System.getProperty("bench.diagram"));
        String[] gateways = {"parallel", "exclusive", "complex"};
        StringBuilder xml = new StringBuilder("<mxGraphModel><root><mxCell id=\"0\"/>"
                + "<mxCell id=\"1\" parent=\"0\"/>");
        for (int i = 0; i < vertices; i++) {
            xml.append("<mxCell id=\"v").append(i).append("\" value=\"Task ").append(i)
                    .append("\" style=\"").append(bpmn && i % 2 == 1
                            ? "shape=mxgraph.bpmn.gateway2;gwType=" + gateways[(i / 2) % 3]
                                    + ";verticalLabelPosition=bottom;verticalAlign=top;"
                            : "rounded=1;whiteSpace=wrap;html=1;")
                    .append("\" vertex=\"1\" parent=\"1\">")
                    .append("<mxGeometry x=\"").append(20 + (i % 10) * 160).append("\" y=\"")
                    .append(20 + (i / 10) * 120).append("\" width=\"120\" height=\"60\" as=\"geometry\"/>")
                    .append("</mxCell>");
            if (i > 0) {
                xml.append("<mxCell id=\"e").append(i).append("\" style=\"edgeStyle=orthogonalEdgeStyle;\"")
                        .append(" edge=\"1\" parent=\"1\" source=\"v").append(i - 1)
                        .append("\" target=\"v").append(i).append("\"><mxGeometry relative=\"1\"")
                        .append(" as=\"geometry\"/></mxCell>");
            }
        }
        return xml.append("</root></mxGraphModel>").toString();
    }

    private interface Call {
        Object run() throws Exception;
    }

    private static long time(Call call) throws Exception {
        long started = System.nanoTime();
        call.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static long percentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<Long>(values);
        Collections.sort(sorted);
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, index));
    }
}
