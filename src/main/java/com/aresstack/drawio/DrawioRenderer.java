package com.aresstack.drawio;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.aresstack.drawio.api.DiagramFormatException;
import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.DrawioDocument;
import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.RenderedPage;
import com.aresstack.drawio.parse.DrawioDocumentParser;
import com.aresstack.drawio.render.DiagramPipeline;
import com.aresstack.drawio.render.DrawioScene;
import com.aresstack.drawio.render.RenderSession;

/**
 * Render draw.io diagrams to {@link BufferedImage}, PNG or SVG.
 *
 * <p>This is the library's entry point. It parses the draw.io source,
 * selects a page, runs the original draw.io JavaScript in an isolated GraalJS
 * context and paints the result with Java2D. It never opens network
 * connections or files.
 *
 * <pre>{@code
 * try (DrawioRenderer renderer = DrawioRenderer.create()) {
 *     RenderResult<byte[]> png = renderer.renderPng(xml, RenderOptions.defaults());
 *     Files.write(target, png.getOutput());
 * } catch (RenderException e) {
 *     switch (e.getCode()) { ... }
 * }
 * }</pre>
 *
 * <p><b>Threading and isolation.</b> A renderer is thread-safe and meant to be
 * shared and long-lived. Each request runs on one of a fixed number of worker
 * threads and gets its own JavaScript context, which is discarded after the
 * request; no JavaScript state is shared between requests. Requests beyond
 * the worker count wait in a bounded queue; when that is full the request
 * fails fast with {@link RenderErrorCode#OVERLOADED}. The calling thread
 * blocks until the result is ready, the timeout from
 * {@link com.aresstack.drawio.api.RenderLimits#getTimeout()} expires
 * ({@link RenderErrorCode#TIMEOUT}), or the caller is interrupted
 * ({@link RenderErrorCode#CANCELLED}). On timeout or interruption the
 * JavaScript context is force-closed.
 *
 * <p><b>Lifecycle.</b> {@link #close()} cancels running requests, stops the
 * worker threads and makes further calls fail with {@link IllegalStateException}.
 * Worker threads are daemon threads, so an unclosed renderer does not keep the
 * JVM alive, but it should still be closed to release them.
 */
public final class DrawioRenderer implements AutoCloseable {

    public static final int DEFAULT_MAX_QUEUED_RENDERS = 64;

    private static final AtomicInteger RENDERER_IDS = new AtomicInteger();
    private static final long CLOSE_GRACE_MILLIS = 2000;

    private final DiagramPipeline pipeline;
    private final SceneOutput output;
    private final ThreadPoolExecutor workers;
    private final Set<RenderSession> activeSessions =
            Collections.newSetFromMap(new ConcurrentHashMap<RenderSession, Boolean>());
    private final AtomicBoolean closed = new AtomicBoolean();

    private DrawioRenderer(Builder builder) {
        this.pipeline = builder.pipeline != null ? builder.pipeline : defaultPipeline();
        this.output = builder.output != null ? builder.output : SceneOutput.java2d();
        this.workers = new ThreadPoolExecutor(builder.maxConcurrentRenders,
                builder.maxConcurrentRenders, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(builder.maxQueuedRenders),
                new WorkerThreadFactory(RENDERER_IDS.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** Create a renderer with default settings. */
    public static DrawioRenderer create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public RenderResult<BufferedImage> renderImage(String drawioXml, RenderOptions options)
            throws RenderException {
        return render(drawioXml, options, OutputKind.IMAGE);
    }

    public RenderResult<byte[]> renderPng(String drawioXml, RenderOptions options)
            throws RenderException {
        return render(drawioXml, options, OutputKind.PNG);
    }

    public RenderResult<String> renderSvg(String drawioXml, RenderOptions options)
            throws RenderException {
        return render(drawioXml, options, OutputKind.SVG);
    }

    public RenderResult<BufferedImage> renderImage(String drawioXml) throws RenderException {
        return renderImage(drawioXml, RenderOptions.defaults());
    }

    public RenderResult<byte[]> renderPng(String drawioXml) throws RenderException {
        return renderPng(drawioXml, RenderOptions.defaults());
    }

    public RenderResult<String> renderSvg(String drawioXml) throws RenderException {
        return renderSvg(drawioXml, RenderOptions.defaults());
    }

    /**
     * Decode draw.io file bytes (UTF-8, optional BOM) strictly, for callers
     * that hold an attachment as bytes.
     */
    public static String decodeUtf8(byte[] drawioFile) throws RenderException {
        Objects.requireNonNull(drawioFile, "drawioFile");
        int offset = drawioFile.length >= 3 && (drawioFile[0] & 0xff) == 0xef
                && (drawioFile[1] & 0xff) == 0xbb && (drawioFile[2] & 0xff) == 0xbf ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(drawioFile, offset, drawioFile.length - offset))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new RenderException(RenderErrorCode.INVALID_XML,
                    "Diagram file is not valid UTF-8");
        }
    }

    /** Return {@code true} once {@link #close()} was called. */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Cancel running requests, stop all worker threads and wait briefly for
     * them to finish. Idempotent.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        workers.shutdownNow();
        for (RenderSession session : activeSessions) {
            session.cancel(RenderErrorCode.CANCELLED);
        }
        try {
            workers.awaitTermination(CLOSE_GRACE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> RenderResult<T> render(String drawioXml, RenderOptions options, OutputKind kind)
            throws RenderException {
        Objects.requireNonNull(drawioXml, "drawioXml");
        Objects.requireNonNull(options, "options");
        if (closed.get()) {
            throw new IllegalStateException("DrawioRenderer is closed");
        }
        long started = System.nanoTime();
        RenderSession session = new RenderSession(options);
        if (drawioXml.length() > options.getLimits().getMaxInputCharacters()) {
            throw new RenderException(RenderErrorCode.LIMIT_EXCEEDED,
                    "Diagram source exceeds the configured size limit");
        }

        Future<RenderResult<T>> future;
        activeSessions.add(session);
        try {
            try {
                future = workers.submit(new RenderTask<T>(drawioXml, session, kind, started));
            } catch (RejectedExecutionException exception) {
                if (closed.get()) {
                    throw new IllegalStateException("DrawioRenderer is closed");
                }
                throw new RenderException(RenderErrorCode.OVERLOADED,
                        "Too many render requests are waiting");
            }
            return await(future, session);
        } finally {
            activeSessions.remove(session);
        }
    }

    private <T> RenderResult<T> await(Future<RenderResult<T>> future, RenderSession session)
            throws RenderException {
        try {
            long remainingNanos = session.remaining().toNanos();
            return future.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            session.cancel(RenderErrorCode.TIMEOUT);
            future.cancel(true);
            throw new RenderException(RenderErrorCode.TIMEOUT, "Rendering exceeded the time limit");
        } catch (InterruptedException exception) {
            session.cancel(RenderErrorCode.CANCELLED);
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RenderException(RenderErrorCode.CANCELLED, "Rendering was interrupted");
        } catch (java.util.concurrent.CancellationException exception) {
            throw cancelledOrTimedOut(session);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RenderException) {
                throw (RenderException) cause;
            }
            throw Failures.translate(cause, session);
        }
    }

    private static RenderException cancelledOrTimedOut(RenderSession session) {
        return session.cancelReason() == RenderErrorCode.TIMEOUT
                ? new RenderException(RenderErrorCode.TIMEOUT, "Rendering exceeded the time limit")
                : new RenderException(RenderErrorCode.CANCELLED, "Rendering was cancelled");
    }

    private final class RenderTask<T> implements Callable<RenderResult<T>> {

        private final String drawioXml;
        private final RenderSession session;
        private final OutputKind kind;
        private final long started;

        RenderTask(String drawioXml, RenderSession session, OutputKind kind, long started) {
            this.drawioXml = drawioXml;
            this.session = session;
            this.kind = kind;
            this.started = started;
        }

        @Override
        public RenderResult<T> call() throws RenderException {
            DrawioScene scene = null;
            try {
                session.checkCancelled();
                DrawioDocument document = parse(drawioXml);
                DiagramPage page = session.options().getPage().select(document);
                CellBudget.check(page, session.limits().getMaxCells());
                session.checkCancelled();

                scene = pipeline.prepare(page, session);
                if (scene == null) {
                    throw new RenderException(RenderErrorCode.RENDER_FAILED,
                            "Graph pipeline produced no scene");
                }
                session.checkCancelled();

                SceneOutput.Rendered<T> rendered = output.write(scene, session, kind);
                session.checkCancelled();
                return new RenderResult<T>(rendered.output, rendered.width, rendered.height,
                        RenderedPage.of(page, document.getPages().size()), session.warnings(),
                        !session.isIncomplete(), Duration.ofNanos(System.nanoTime() - started));
            } catch (DiagramFormatException exception) {
                throw RenderException.fromFormatException(exception);
            } catch (RenderException exception) {
                if (session.cancelReason() != null) {
                    throw cancelledOrTimedOut(session);
                }
                throw exception;
            } catch (RuntimeException | StackOverflowError exception) {
                throw Failures.translate(exception, session);
            } finally {
                closeQuietly(scene);
            }
        }

        private DrawioDocument parse(String xml) throws DiagramFormatException {
            return new DrawioDocumentParser().parse(xml);
        }
    }

    private static void closeQuietly(DrawioScene scene) {
        if (scene instanceof AutoCloseable) {
            try {
                ((AutoCloseable) scene).close();
            } catch (Exception ignored) {
                // Cleanup must not replace the render outcome.
            }
        }
    }

    private static DiagramPipeline defaultPipeline() {
        return DefaultPipeline.create();
    }

    enum OutputKind { IMAGE, PNG, SVG }

    /**
     * Configure a {@link DrawioRenderer}.
     */
    public static final class Builder {

        private int maxConcurrentRenders = Math.max(1,
                Math.min(4, Runtime.getRuntime().availableProcessors()));
        private int maxQueuedRenders = DEFAULT_MAX_QUEUED_RENDERS;
        private DiagramPipeline pipeline;
        private SceneOutput output;

        private Builder() {
        }

        /**
         * Number of worker threads, i.e. renders that run at the same time.
         * Default: number of processors, at most 4. Each running render holds
         * one JavaScript context and its heap.
         */
        public Builder maxConcurrentRenders(int value) {
            if (value < 1 || value > 64) {
                throw new IllegalArgumentException("maxConcurrentRenders must be between 1 and 64");
            }
            this.maxConcurrentRenders = value;
            return this;
        }

        /** Number of requests that may wait for a worker before OVERLOADED. Default 64. */
        public Builder maxQueuedRenders(int value) {
            if (value < 1 || value > 100000) {
                throw new IllegalArgumentException("maxQueuedRenders must be between 1 and 100000");
            }
            this.maxQueuedRenders = value;
            return this;
        }

        /**
         * Replace the graph pipeline. Intended for tests and diagnostics; the
         * pipeline is an internal port, not a stable extension point.
         */
        public Builder pipeline(DiagramPipeline pipeline) {
            this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
            return this;
        }

        Builder output(SceneOutput output) {
            this.output = Objects.requireNonNull(output, "output");
            return this;
        }

        public DrawioRenderer build() {
            return new DrawioRenderer(this);
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {

        private final int rendererId;
        private final AtomicInteger threadIds = new AtomicInteger();

        WorkerThreadFactory(int rendererId) {
            this.rendererId = rendererId;
        }

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task,
                    "drawio-render-" + rendererId + "-" + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
