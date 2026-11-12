package com.aresstack.drawio.graal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * Execute JavaScript in an isolated GraalJS context with restricted host access.
 *
 * <p>Use one runtime per rendering operation or batch. Never share a GraalJS
 * context across calls; each invocation receives a fresh context.
 */
public final class GraalJsRuntime implements AutoCloseable {

    private static final String LANGUAGE = "js";
    private static final int MAX_SOURCE_CHARACTERS = 32 * 1024 * 1024;
    private static final int MAX_RESULT_CHARACTERS = 4 * 1024 * 1024;
    private static final String SHARED_ENGINE_PROPERTY = "drawio.graaljs.sharedEngine";
    private static final long IDLE_THREAD_MILLIS = 5000;

    private final long timeoutMillis;
    private final Engine engine;
    private final ThreadPoolExecutor executor;
    private final AtomicReference<Context> activeContext = new AtomicReference<Context>();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Create a runtime whose contexts use the process-wide shared engine.
     * Set the system property {@code drawio.graaljs.sharedEngine=false} to
     * give every runtime its own engine instead (slower; for diagnostics).
     */
    public GraalJsRuntime(Duration timeout) {
        this(timeout, !"false".equalsIgnoreCase(System.getProperty(SHARED_ENGINE_PROPERTY)));
    }

    GraalJsRuntime(Duration timeout, boolean useSharedEngine) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero() || timeout.toMillis() < 1) {
            throw new IllegalArgumentException("timeout must be at least one millisecond");
        }
        timeoutMillis = timeout.toMillis();
        engine = useSharedEngine ? SharedEngine.get() : null;
        // One worker thread that ends when idle, so a runtime that is never
        // closed does not pin a thread for the rest of the JVM's life.
        executor = new ThreadPoolExecutor(1, 1, IDLE_THREAD_MILLIS, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());
        executor.allowCoreThreadTimeOut(true);
    }

    public ScriptExecutionResult execute(String sourceName, String script, DrawioHost host) {
        Objects.requireNonNull(sourceName, "sourceName");
        Objects.requireNonNull(script, "script");
        return execute(Collections.singletonList(ScriptSource.inline(sourceName, script)), host);
    }

    /**
     * Evaluate several trusted scripts, in order, inside one fresh context.
     *
     * <p>Use this to load pinned bundles followed by a small entry script.
     * The result is the value of the last script. Errors name the failing
     * source. The context is closed afterwards, as with {@link #execute}.
     */
    public ScriptExecutionResult execute(List<ScriptSource> sources, DrawioHost host) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(host, "host");
        ensureOpen();
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
        final List<ScriptSource> ordered = new ArrayList<ScriptSource>(sources);
        long totalCharacters = 0;
        for (ScriptSource source : ordered) {
            Objects.requireNonNull(source, "source");
            totalCharacters += source.getText().length();
        }
        if (totalCharacters > MAX_SOURCE_CHARACTERS) {
            return ScriptExecutionResult.failure("JavaScript source exceeds size limit");
        }

        Future<ScriptExecutionResult> evaluation = executor.submit(
                () -> evaluateInNewContext(ordered, host));
        return awaitResult(evaluation);
    }

    public ScriptExecutionResult executeBundled(String resourceName, DrawioHost host) {
        ensureOpen();
        return execute(Collections.singletonList(ScriptSource.bundled(resourceName)), host);
    }

    private ScriptExecutionResult awaitResult(Future<ScriptExecutionResult> evaluation) {
        try {
            return evaluation.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            stopActiveExecution();
            return ScriptExecutionResult.failure("JavaScript execution timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stopActiveExecution();
            return ScriptExecutionResult.failure("JavaScript execution interrupted");
        } catch (ExecutionException exception) {
            return ScriptExecutionResult.failure("JavaScript runtime failed: "
                    + exception.getCause().getClass().getSimpleName());
        }
    }

    private ScriptExecutionResult evaluateInNewContext(
            List<ScriptSource> sources, DrawioHost host) {
        if (closed.get()) {
            return ScriptExecutionResult.failure("JavaScript runtime has been closed");
        }

        Context context = null;
        try {
            context = createRestrictedContext();
            activeContext.set(context);
            if (closed.get() || Thread.currentThread().isInterrupted()) {
                return ScriptExecutionResult.failure("JavaScript runtime has been closed");
            }
            context.getBindings(LANGUAGE).putMember("drawioHost", host);
            Value result = null;
            for (ScriptSource source : sources) {
                result = context.eval(source.polyglotSource());
            }
            return convertResult(result);
        } catch (PolyglotException exception) {
            return ScriptExecutionResult.failure(formatGuestError(exception));
        } catch (Exception exception) {
            return ScriptExecutionResult.failure("JavaScript runtime failed: "
                    + exception.getClass().getSimpleName());
        } finally {
            if (context != null) {
                activeContext.compareAndSet(context, null);
                try {
                    context.close();
                } catch (IllegalStateException ignored) {
                    // Keep a forced timeout close from hiding the evaluation result.
                }
            }
        }
    }

    private Context createRestrictedContext() {
        Context.Builder builder = Context.newBuilder(LANGUAGE)
                .allowHostAccess(HostAccess.EXPLICIT)
                .allowHostClassLookup(className -> false)
                .allowIO(false)
                .allowCreateThread(false)
                .allowNativeAccess(false)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                // Defense in depth (#15). The js.* switches that would remove
                // eval/load/print globals are experimental in GraalJS 21.3 and
                // are deliberately not used; GraalJsSecurityTest pins what the
                // guest can and cannot reach instead.
                .allowExperimentalOptions(false)
                .out(SharedEngine.DISCARD_OUTPUT)
                .err(SharedEngine.DISCARD_OUTPUT);
        if (engine != null) {
            return builder.engine(engine).build();
        }
        return builder.option("engine.WarnInterpreterOnly", "false").build();
    }

    private ScriptExecutionResult convertResult(Value value) {
        if (value == null || value.isNull()) {
            return ScriptExecutionResult.success(null);
        }
        String output = value.isString() ? value.asString() : value.toString();
        if (output.length() > MAX_RESULT_CHARACTERS) {
            return ScriptExecutionResult.failure("JavaScript result exceeds size limit");
        }
        return ScriptExecutionResult.success(output);
    }

    private String formatGuestError(PolyglotException exception) {
        if (exception.isCancelled()) {
            return "JavaScript execution was cancelled";
        }
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "JavaScript execution failed";
        }
        org.graalvm.polyglot.SourceSection location = exception.getSourceLocation();
        if (location != null && location.getSource() != null) {
            message = location.getSource().getName() + ":" + location.getStartLine() + ": " + message;
        }
        return message.substring(0, Math.min(message.length(), 512));
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("JavaScript runtime is closed");
        }
    }

    private void stopActiveExecution() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        executor.shutdownNow();
        Context context = activeContext.get();
        if (context != null) {
            cancelContextWithoutBlockingCaller(context);
        }
    }

    private void cancelContextWithoutBlockingCaller(Context context) {
        Thread cancellation = new Thread(() -> {
            try {
                context.close(true);
            } catch (IllegalStateException ignored) {
                // Ignore an already closed context during cancellation.
            }
        }, "drawio-graaljs-context-cancel");
        cancellation.setDaemon(true);
        cancellation.start();
    }

    @Override
    public void close() {
        stopActiveExecution();
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "drawio-graaljs-runtime");
            thread.setDaemon(true);
            return thread;
        }
    }
}
