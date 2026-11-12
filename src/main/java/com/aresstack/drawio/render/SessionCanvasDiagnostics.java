package com.aresstack.drawio.render;

import java.util.Objects;

import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.canvas.CanvasDiagnostics;

/**
 * Forward canvas diagnostics to a {@link RenderSession}.
 *
 * <p>Under the session's fail policy the checked {@link RenderException} is
 * carried out of the canvas call stack (through GraalJS) inside an unchecked
 * {@link Abort}; the pipeline unwraps it with {@link Abort#getRenderException()}.
 */
public final class SessionCanvasDiagnostics implements CanvasDiagnostics {

    private final RenderSession session;

    public SessionCanvasDiagnostics(RenderSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override
    public void unsupported(String capabilityId, String message) {
        try {
            session.unsupported(capabilityId, message);
        } catch (RenderException exception) {
            throw new Abort(exception);
        }
    }

    @Override
    public void missingResource(String capabilityId, String message) {
        try {
            session.missingResource(capabilityId, message);
        } catch (RenderException exception) {
            throw new Abort(exception);
        }
    }

    /** Carry a fail-fast {@link RenderException} through unchecked call stacks. */
    public static final class Abort extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final RenderException renderException;

        Abort(RenderException renderException) {
            super(renderException.getMessage(), renderException);
            this.renderException = renderException;
        }

        public RenderException getRenderException() {
            return renderException;
        }
    }
}
