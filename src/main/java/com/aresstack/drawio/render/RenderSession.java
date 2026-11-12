package com.aresstack.drawio.render;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderWarning;
import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;
import com.aresstack.drawio.layout.AwtTextMetrics;
import com.aresstack.drawio.layout.TextMetrics;

/**
 * Carry the state of one render request through the internal pipeline:
 * deadline, cancellation, limits, services and collected warnings.
 *
 * <p>Thread-safe. One session belongs to exactly one request; never reuse it.
 */
public final class RenderSession {

    private static final int MAX_WARNINGS = 200;
    private static final TextMetrics DEFAULT_TEXT_METRICS = new AwtTextMetrics();

    private final RenderOptions options;
    private final TextMetrics textMetrics;
    private final long deadlineNanos;
    private final Object lock = new Object();
    private final List<Runnable> cancelHooks = new ArrayList<Runnable>();
    private final Set<RenderWarning> warnings = new LinkedHashSet<RenderWarning>();
    private boolean incomplete;
    private boolean warningsTruncated;
    private RenderErrorCode cancelReason;

    /**
     * Start a session whose deadline is the options' timeout from now.
     */
    public RenderSession(RenderOptions options) {
        this(options, DEFAULT_TEXT_METRICS);
    }

    public RenderSession(RenderOptions options, TextMetrics textMetrics) {
        this.options = Objects.requireNonNull(options, "options");
        this.textMetrics = Objects.requireNonNull(textMetrics, "textMetrics");
        this.deadlineNanos = System.nanoTime() + options.getLimits().getTimeout().toNanos();
    }

    public RenderOptions options() {
        return options;
    }

    public RenderLimits limits() {
        return options.getLimits();
    }

    public ResourcePolicy resourcePolicy() {
        return options.getResourcePolicy();
    }

    public TextMetrics textMetrics() {
        return textMetrics;
    }

    /**
     * Return the remaining time budget; at least one millisecond so it can be
     * passed to APIs that reject zero, even when the deadline has passed.
     */
    public Duration remaining() {
        long nanos = deadlineNanos - System.nanoTime();
        return Duration.ofMillis(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(nanos)));
    }

    public boolean isExpired() {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    /**
     * Throw if the request was cancelled, timed out, or the calling thread was interrupted.
     */
    public void checkCancelled() throws RenderException {
        RenderErrorCode reason;
        synchronized (lock) {
            reason = cancelReason;
        }
        if (reason == null && isExpired()) {
            reason = RenderErrorCode.TIMEOUT;
        }
        if (reason == null && Thread.currentThread().isInterrupted()) {
            reason = RenderErrorCode.CANCELLED;
        }
        if (reason == RenderErrorCode.TIMEOUT) {
            throw new RenderException(RenderErrorCode.TIMEOUT, "Rendering exceeded the time limit");
        }
        if (reason != null) {
            throw new RenderException(RenderErrorCode.CANCELLED, "Rendering was cancelled");
        }
    }

    /**
     * Register work that stops this request's resources, such as closing a
     * GraalJS context. Runs immediately if the session is already cancelled.
     * Hooks run at most once, on the cancelling thread, and must not block.
     */
    public void onCancel(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        boolean runNow;
        synchronized (lock) {
            runNow = cancelReason != null;
            if (!runNow) {
                cancelHooks.add(hook);
            }
        }
        if (runNow) {
            runQuietly(hook);
        }
    }

    /**
     * Cancel the request and run all registered hooks once. Called by the
     * facade on timeout, interruption or shutdown.
     *
     * @return {@code true} if this call cancelled the session
     */
    public boolean cancel(RenderErrorCode reason) {
        Objects.requireNonNull(reason, "reason");
        List<Runnable> hooks;
        synchronized (lock) {
            if (cancelReason != null) {
                return false;
            }
            cancelReason = reason;
            hooks = new ArrayList<Runnable>(cancelHooks);
            cancelHooks.clear();
        }
        for (Runnable hook : hooks) {
            runQuietly(hook);
        }
        return true;
    }

    /** Return the cancellation reason, or {@code null} while the request is active. */
    public RenderErrorCode cancelReason() {
        synchronized (lock) {
            return cancelReason;
        }
    }

    /**
     * Record a non-fatal deviation that does not change the diagram's meaning.
     */
    public void warn(String capabilityId, String message) {
        add(new RenderWarning(capabilityId, message), false);
    }

    /**
     * Report a shape, stencil or capability that cannot be rendered faithfully.
     *
     * <p>Under {@link UnsupportedFeaturePolicy#FAIL} this throws; under
     * {@link UnsupportedFeaturePolicy#WARN} it records a warning, marks the
     * result incomplete and returns, so the caller may skip the element.
     *
     * @param capabilityId e.g. {@code shape:mxgraph.foo}, {@code stencil:...},
     *                     {@code canvas:gradient}, {@code label:html}
     * @param message a short description without diagram text
     */
    public void unsupported(String capabilityId, String message) throws RenderException {
        if (options.getUnsupportedFeaturePolicy() == UnsupportedFeaturePolicy.FAIL) {
            throw RenderException.unsupported(capabilityId, message);
        }
        add(new RenderWarning(capabilityId, message), true);
    }

    /**
     * Report a referenced resource that the policy does not allow or that is unavailable.
     * Behaves like {@link #unsupported} but fails with {@link RenderErrorCode#MISSING_RESOURCE}.
     */
    public void missingResource(String capabilityId, String message) throws RenderException {
        if (options.getUnsupportedFeaturePolicy() == UnsupportedFeaturePolicy.FAIL) {
            throw new RenderException(RenderErrorCode.MISSING_RESOURCE, message, capabilityId, null);
        }
        add(new RenderWarning(capabilityId, message), true);
    }

    /** Return a snapshot of the warnings recorded so far, in first-seen order. */
    public List<RenderWarning> warnings() {
        synchronized (lock) {
            List<RenderWarning> copy = new ArrayList<RenderWarning>(warnings);
            if (warningsTruncated) {
                copy.add(new RenderWarning("diagnostics:truncated",
                        "Further warnings were omitted"));
            }
            return Collections.unmodifiableList(copy);
        }
    }

    /** Return {@code true} if any unsupported feature or resource was skipped. */
    public boolean isIncomplete() {
        synchronized (lock) {
            return incomplete;
        }
    }

    private void add(RenderWarning warning, boolean marksIncomplete) {
        synchronized (lock) {
            if (marksIncomplete) {
                incomplete = true;
            }
            if (warnings.size() < MAX_WARNINGS) {
                warnings.add(warning);
            } else if (!warnings.contains(warning)) {
                warningsTruncated = true;
            }
        }
    }

    private static void runQuietly(Runnable hook) {
        try {
            hook.run();
        } catch (RuntimeException ignored) {
            // A failing cleanup hook must not prevent the others from running.
        }
    }
}
