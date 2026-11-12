package com.aresstack.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.PageSelector;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.RendererVersion;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;
import com.aresstack.drawio.canvas.DrawioCanvas;
import com.aresstack.drawio.render.DiagramPipeline;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

class DrawioRendererTest {

    private static final String MODEL = "<mxGraphModel><root><mxCell id=\"0\"/>"
            + "<mxCell id=\"1\" parent=\"0\"/>"
            + "<mxCell id=\"v\" value=\"secret label\" vertex=\"1\" parent=\"1\"/>"
            + "</root></mxGraphModel>";

    private static final String MULTI_PAGE = "<mxfile>"
            + "<diagram id=\"p1\" name=\"First\">" + MODEL + "</diagram>"
            + "<diagram id=\"p2\" name=\"Second\">" + MODEL + "</diagram>"
            + "</mxfile>";

    private final List<DrawioRenderer> renderers = new ArrayList<DrawioRenderer>();

    @AfterEach
    void closeRenderers() {
        for (DrawioRenderer renderer : renderers) {
            renderer.close();
        }
    }

    @Test
    void rendersSelectedPageWithMetadata() throws RenderException {
        DrawioRenderer renderer = renderer(1, 4, (page, session) -> new TestScene(page.getId()));
        RenderResult<String> result = renderer.renderSvg(MULTI_PAGE,
                RenderOptions.builder().page(PageSelector.name("Second")).build());
        assertEquals("svg:p2", result.getOutput());
        assertEquals("p2", result.getPage().getId());
        assertEquals(1, result.getPage().getIndex());
        assertEquals(2, result.getPage().getPageCount());
        assertTrue(result.isComplete());
        assertTrue(result.getWarnings().isEmpty());
        assertEquals(RendererVersion.DRAWIO_UPSTREAM_COMMIT, result.getDrawioUpstreamCommit());
    }

    @Test
    void reportsParserFailuresWithStableCodes() {
        DrawioRenderer renderer = renderer(1, 4, (page, session) -> new TestScene("x"));
        assertCode(RenderErrorCode.INVALID_XML, () -> renderer.renderSvg("<mxGraphModel><root>"));
        assertCode(RenderErrorCode.UNSUPPORTED_FORMAT, () -> renderer.renderSvg("<svg/>"));
        assertCode(RenderErrorCode.PAGE_NOT_FOUND, () -> renderer.renderSvg(MULTI_PAGE,
                RenderOptions.builder().page(PageSelector.index(5)).build()));
    }

    @Test
    void rejectsOversizedInputAndTooManyCellsBeforePipelineRuns() {
        AtomicInteger calls = new AtomicInteger();
        DrawioRenderer renderer = renderer(1, 4, (page, session) -> {
            calls.incrementAndGet();
            return new TestScene("x");
        });
        assertCode(RenderErrorCode.LIMIT_EXCEEDED, () -> renderer.renderSvg(MODEL, limits(
                RenderLimits.builder().maxInputCharacters(10))));
        assertCode(RenderErrorCode.LIMIT_EXCEEDED, () -> renderer.renderSvg(MODEL, limits(
                RenderLimits.builder().maxCells(2))));
        assertEquals(0, calls.get());
    }

    @Test
    void unsupportedFeatureFailsOrWarnsAccordingToPolicy() throws RenderException {
        DiagramPipeline pipeline = (page, session) -> {
            session.unsupported("shape:mxgraph.unknown", "Shape is not registered");
            return new TestScene("partial");
        };
        DrawioRenderer renderer = renderer(1, 4, pipeline);
        RenderException failure = assertCode(RenderErrorCode.UNSUPPORTED_STENCIL,
                () -> renderer.renderSvg(MODEL));
        assertEquals("shape:mxgraph.unknown", failure.getCapabilityId());

        RenderResult<String> partial = renderer.renderSvg(MODEL, RenderOptions.builder()
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN).build());
        assertFalse(partial.isComplete());
        assertEquals("shape:mxgraph.unknown", partial.getWarnings().get(0).getCapabilityId());
    }

    @Test
    void unexpectedFailureDoesNotLeakDiagramContent() {
        DrawioRenderer renderer = renderer(1, 4, (page, session) -> {
            throw new IllegalStateException("cannot handle secret label");
        });
        RenderException failure = assertCode(RenderErrorCode.RENDER_FAILED,
                () -> renderer.renderSvg(MODEL));
        assertFalse(failure.getMessage().contains("secret"));
        assertNull(failure.getCause());
    }

    @Test
    void timeoutRunsCancelHookClosesSceneAndFreesTheWorker() throws Exception {
        AtomicInteger hookRuns = new AtomicInteger();
        AtomicInteger closedScenes = new AtomicInteger();
        DiagramPipeline pipeline = (page, session) -> {
            if ("slow".equals(page.getName())) {
                CountDownLatch released = new CountDownLatch(1);
                session.onCancel(() -> {
                    hookRuns.incrementAndGet();
                    released.countDown();
                });
                try {
                    released.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                session.checkCancelled();
            }
            return new TestScene("ok", closedScenes);
        };
        DrawioRenderer renderer = renderer(1, 4, pipeline);
        long started = System.nanoTime();
        assertCode(RenderErrorCode.TIMEOUT, () -> renderer.renderSvg(
                "<mxfile><diagram id=\"s\" name=\"slow\">" + MODEL + "</diagram></mxfile>",
                limits(RenderLimits.builder().timeout(Duration.ofMillis(200)))));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000);
        assertEquals(1, hookRuns.get());

        assertEquals("svg:ok", renderer.renderSvg(MODEL).getOutput());
        assertEquals(1, closedScenes.get());
    }

    @Test
    void timeoutReturnsPromptlyEvenIfPipelineIgnoresCancellation() {
        DrawioRenderer renderer = renderer(1, 4, (page, session) -> {
            long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(700);
            while (System.nanoTime() < until) {
                Thread.yield();
            }
            return new TestScene("late");
        });
        long started = System.nanoTime();
        assertCode(RenderErrorCode.TIMEOUT, () -> renderer.renderSvg(MODEL,
                limits(RenderLimits.builder().timeout(Duration.ofMillis(100)))));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 600);
    }

    @Test
    void interruptedCallerGetsCancelled() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger hookRuns = new AtomicInteger();
        DrawioRenderer renderer = renderer(1, 4, (page, session) -> {
            session.onCancel(hookRuns::incrementAndGet);
            entered.countDown();
            while (session.cancelReason() == null) {
                Thread.yield();
            }
            session.checkCancelled();
            return new TestScene("never");
        });
        AtomicReference<RenderException> failure = new AtomicReference<RenderException>();
        AtomicReference<Boolean> stillInterrupted = new AtomicReference<Boolean>();
        Thread caller = new Thread(() -> {
            try {
                renderer.renderSvg(MODEL);
            } catch (RenderException exception) {
                failure.set(exception);
            }
            stillInterrupted.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(5000);
        assertEquals(RenderErrorCode.CANCELLED, failure.get().getCode());
        assertTrue(stillInterrupted.get());
        assertEquals(1, hookRuns.get());
    }

    @Test
    void fullQueueFailsFastWithOverloaded() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        DrawioRenderer renderer = renderer(1, 1, (page, session) -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new TestScene("x");
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> running = callers.submit(() -> renderer.renderSvg(MODEL));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> queued = callers.submit(() -> renderer.renderSvg(MODEL));
            waitUntil(() -> queuedTasks(renderer) == 1);
            assertCode(RenderErrorCode.OVERLOADED, () -> renderer.renderSvg(MODEL));
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            queued.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void parallelRendersAreIsolatedAndUseBoundedThreads() throws Exception {
        int maxWorkers = 3;
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        DrawioRenderer renderer = renderer(maxWorkers, 200, (page, session) -> {
            int now = concurrent.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(2);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
            }
            return new TestScene(page.getId());
        });
        ExecutorService callers = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<Future<String>>();
            for (int i = 0; i < 80; i++) {
                final String id = "page-" + i;
                results.add(callers.submit(() -> renderer.renderSvg(
                        "<mxfile><diagram id=\"" + id + "\" name=\"n\">" + MODEL
                                + "</diagram></mxfile>").getOutput()));
            }
            for (int i = 0; i < results.size(); i++) {
                assertEquals("svg:page-" + i, results.get(i).get(10, TimeUnit.SECONDS));
            }
        } finally {
            callers.shutdownNow();
        }
        assertTrue(peak.get() <= maxWorkers, "peak concurrency " + peak.get());
        assertTrue(workerThreads() <= maxWorkers * renderers.size());
    }

    @Test
    void closeCancelsRunningRenderAndStopsWorkers() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        DrawioRenderer renderer = renderer(2, 4, (page, session) -> {
            CountDownLatch cancelled = new CountDownLatch(1);
            session.onCancel(cancelled::countDown);
            entered.countDown();
            try {
                cancelled.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            session.checkCancelled();
            return new TestScene("never");
        });
        ExecutorService callers = Executors.newSingleThreadExecutor();
        try {
            Future<RenderException> outcome = callers.submit(() -> {
                try {
                    renderer.renderSvg(MODEL);
                    return null;
                } catch (RenderException exception) {
                    return exception;
                }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            renderer.close();
            assertEquals(RenderErrorCode.CANCELLED, outcome.get(5, TimeUnit.SECONDS).getCode());
        } finally {
            callers.shutdownNow();
        }
        assertTrue(renderer.isClosed());
        assertThrows(IllegalStateException.class, () -> renderer.renderSvg(MODEL));
        renderer.close();
        waitUntil(() -> workerThreads() == 0 || renderers.size() > 1);
    }

    @Test
    void repeatedRendersDoNotLeakThreadsOrScenes() throws RenderException {
        AtomicInteger closedScenes = new AtomicInteger();
        DrawioRenderer renderer = renderer(2, 4,
                (page, session) -> new TestScene("x", closedScenes));
        for (int i = 0; i < 200; i++) {
            renderer.renderPng(MODEL);
        }
        assertEquals(200, closedScenes.get());
        assertTrue(workerThreads() <= 2);
    }

    @Test
    void decodesUtf8WithBomAndRejectsMalformedBytes() throws RenderException {
        byte[] bom = new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '<', 'a', '/', '>'};
        assertEquals("<a/>", DrawioRenderer.decodeUtf8(bom));
        assertEquals("ä", DrawioRenderer.decodeUtf8("ä".getBytes(StandardCharsets.UTF_8)));
        assertCode(RenderErrorCode.INVALID_XML,
                () -> DrawioRenderer.decodeUtf8(new byte[] {(byte) 0xc3, (byte) 0x28}));
    }

    @Test
    void defaultPipelineFailsExplicitlyInsteadOfApproximating() {
        DrawioRenderer renderer = DrawioRenderer.builder().output(new FakeOutput()).build();
        renderers.add(renderer);
        assertCode(RenderErrorCode.RENDER_FAILED, () -> renderer.renderSvg(MODEL));
    }

    @Test
    void upstreamCommitConstantMatchesPinnedBundle() throws Exception {
        String manifest = new String(Files.readAllBytes(Paths.get("js-bundle/upstream.json")),
                StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"" + RendererVersion.DRAWIO_UPSTREAM_COMMIT + "\""));
    }

    private DrawioRenderer renderer(int workers, int queue, DiagramPipeline pipeline) {
        DrawioRenderer renderer = DrawioRenderer.builder()
                .maxConcurrentRenders(workers)
                .maxQueuedRenders(queue)
                .pipeline(pipeline)
                .output(new FakeOutput())
                .build();
        renderers.add(renderer);
        return renderer;
    }

    private static RenderOptions limits(RenderLimits.Builder limits) {
        return RenderOptions.builder().limits(limits.build()).build();
    }

    private static RenderException assertCode(RenderErrorCode code, ThrowingCall call) {
        RenderException exception = assertThrows(RenderException.class, call::run);
        assertEquals(code, exception.getCode(), exception.getMessage());
        return exception;
    }

    private static int queuedTasks(DrawioRenderer renderer) {
        try {
            java.lang.reflect.Field field = DrawioRenderer.class.getDeclaredField("workers");
            field.setAccessible(true);
            return ((java.util.concurrent.ThreadPoolExecutor) field.get(renderer)).getQueue().size();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int workerThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("drawio-render-")) {
                count++;
            }
        }
        return count;
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > until) {
                throw new AssertionError("condition not reached");
            }
            Thread.sleep(5);
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    private static final class TestScene implements DrawioScene, AutoCloseable {

        private final String marker;
        private final AtomicInteger closed;

        TestScene(String marker) {
            this(marker, new AtomicInteger());
        }

        TestScene(String marker, AtomicInteger closed) {
            this.marker = marker;
            this.closed = closed;
        }

        @Override
        public void paint(DrawioCanvas canvas) {
        }

        @Override
        public void close() {
            closed.incrementAndGet();
        }
    }

    private static final class FakeOutput implements SceneOutput {

        @Override
        @SuppressWarnings("unchecked")
        public <T> Rendered<T> write(DrawioScene scene, RenderSession session,
                                     DrawioRenderer.OutputKind kind) {
            String marker = ((TestScene) scene).marker;
            Object output = kind == DrawioRenderer.OutputKind.PNG
                    ? marker.getBytes(StandardCharsets.UTF_8)
                    : "svg:" + marker;
            return new Rendered<T>((T) output, 10, 20);
        }
    }

}
