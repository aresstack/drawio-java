package com.aresstack.drawio.render;

import com.aresstack.drawio.api.DiagramPage;
import com.aresstack.drawio.api.RenderException;

/**
 * Turn one parsed draw.io page into a repaintable {@link DrawioScene}.
 *
 * <p>This is the internal port between the public facade and the graph
 * implementation that runs the original draw.io JavaScript. It is public only
 * because the implementation lives in another package; it is not part of the
 * stable consumer API.
 *
 * <p>Contract:
 * <ul>
 *   <li>The facade calls {@link #prepare} on one of its worker threads; an
 *       implementation must not keep a GraalJS context reachable from another
 *       thread after {@code prepare} returns, unless the returned scene owns it
 *       and implements {@link AutoCloseable}.</li>
 *   <li>Long-running work must call {@link RenderSession#checkCancelled()} at
 *       safe points and register a cancellation hook via
 *       {@link RenderSession#onCancel(Runnable)} that stops the JavaScript
 *       context (e.g. {@code GraalJsRuntime::close}).</li>
 *   <li>Unsupported shapes, stencils or canvas features are reported through
 *       {@link RenderSession#unsupported(String, String)}; never paint a
 *       semantically different substitute silently.</li>
 *   <li>Implementations must be thread-safe: the facade may call
 *       {@code prepare} concurrently from several workers.</li>
 *   <li>Exception messages must not contain diagram text or XML.</li>
 * </ul>
 */
public interface DiagramPipeline {

    /**
     * Prepare a scene for the given page.
     *
     * @param page the page selected by the caller, never {@code null}
     * @param session limits, deadline, diagnostics and services for this request
     * @return a repaintable scene; the facade closes it after output if it is
     *         {@link AutoCloseable}
     * @throws RenderException with a stable {@link com.aresstack.drawio.api.RenderErrorCode}
     */
    DrawioScene prepare(DiagramPage page, RenderSession session) throws RenderException;
}
