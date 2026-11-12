package com.aresstack.drawio.canvas;

/**
 * Receive reports about Draw.io canvas features that cannot be painted faithfully.
 *
 * <p>Capability identifiers follow the render API convention, e.g.
 * {@code canvas:fillStyle:hachure}, {@code canvas:text-direction:vertical-rl}
 * or {@code image:external}. Messages must not contain diagram text.
 *
 * <p>An implementation either throws (fail fast) or records the report and
 * returns, in which case the caller skips only the affected operation.
 */
public interface CanvasDiagnostics {

    /** Fail on the first report with an {@link UnsupportedOperationException}. */
    CanvasDiagnostics STRICT = new CanvasDiagnostics() {
        @Override
        public void unsupported(String capabilityId, String message) {
            throw new UnsupportedOperationException(capabilityId + ": " + message);
        }

        @Override
        public void missingResource(String capabilityId, String message) {
            throw new UnsupportedOperationException(capabilityId + ": " + message);
        }
    };

    /** Report a feature that is not implemented by this canvas. */
    void unsupported(String capabilityId, String message);

    /** Report a referenced resource that is unavailable or not permitted. */
    void missingResource(String capabilityId, String message);
}
