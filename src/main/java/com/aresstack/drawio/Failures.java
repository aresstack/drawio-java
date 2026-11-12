package com.aresstack.drawio;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.render.RenderSession;

/**
 * Translate unexpected failures into stable codes without leaking diagram data.
 *
 * <p>Messages of unexpected exceptions can contain labels or XML from the
 * diagram, so only the exception type is reported and the cause is dropped,
 * unless the system property {@code drawio.debug.causes=true} is set.
 */
final class Failures {

    private static final String OUTPUT_LIMIT_EXCEPTION =
            "com.aresstack.drawio.render.OutputLimitException";

    private Failures() {
    }

    static RenderException translate(Throwable failure, RenderSession session) {
        RenderErrorCode reason = session.cancelReason();
        if (reason == RenderErrorCode.TIMEOUT) {
            return new RenderException(RenderErrorCode.TIMEOUT, "Rendering exceeded the time limit");
        }
        if (reason != null) {
            return new RenderException(RenderErrorCode.CANCELLED, "Rendering was cancelled");
        }
        if (failure instanceof RenderException) {
            return (RenderException) failure;
        }
        Throwable cause = Boolean.getBoolean("drawio.debug.causes") ? failure : null;
        if (failure != null && OUTPUT_LIMIT_EXCEPTION.equals(failure.getClass().getName())) {
            return new RenderException(RenderErrorCode.LIMIT_EXCEEDED,
                    "Output exceeds the configured size limit", cause);
        }
        if (failure instanceof StackOverflowError) {
            return new RenderException(RenderErrorCode.LIMIT_EXCEEDED,
                    "Diagram is nested too deeply", cause);
        }
        String type = failure == null ? "unknown" : failure.getClass().getSimpleName();
        return new RenderException(RenderErrorCode.RENDER_FAILED,
                "Rendering failed: " + type, cause);
    }
}
