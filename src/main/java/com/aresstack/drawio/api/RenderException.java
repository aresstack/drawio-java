package com.aresstack.drawio.api;

import java.util.Objects;

/**
 * Report why a diagram could not be rendered.
 *
 * <p>Messages never contain diagram text, labels or XML fragments. They may
 * contain a capability identifier such as {@code shape:mxgraph.bpmn.task2},
 * which names a draw.io feature, not user data.
 */
public final class RenderException extends Exception {

    private static final long serialVersionUID = 1L;

    private final RenderErrorCode code;
    private final String capabilityId;

    public RenderException(RenderErrorCode code, String message) {
        this(code, message, null, null);
    }

    public RenderException(RenderErrorCode code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public RenderException(RenderErrorCode code, String message, String capabilityId,
                           Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.capabilityId = capabilityId;
    }

    /**
     * Translate a parser failure without duplicating its message or reason.
     */
    public static RenderException fromFormatException(DiagramFormatException exception) {
        Objects.requireNonNull(exception, "exception");
        return new RenderException(codeFor(exception.getReason()), exception.getMessage(),
                exception);
    }

    public static RenderException unsupported(String capabilityId, String message) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        RenderErrorCode code = capabilityId.startsWith("shape:")
                || capabilityId.startsWith("stencil:")
                ? RenderErrorCode.UNSUPPORTED_STENCIL
                : RenderErrorCode.UNSUPPORTED_FEATURE;
        return new RenderException(code, message, capabilityId, null);
    }

    public RenderErrorCode getCode() {
        return code;
    }

    /**
     * Return the unsupported draw.io capability, or {@code null} when none applies.
     */
    public String getCapabilityId() {
        return capabilityId;
    }

    /**
     * Return the original parser reason, or {@code null} when the parser did not fail.
     */
    public DiagramFormatException.Reason getFormatReason() {
        Throwable cause = getCause();
        return cause instanceof DiagramFormatException
                ? ((DiagramFormatException) cause).getReason()
                : null;
    }

    private static RenderErrorCode codeFor(DiagramFormatException.Reason reason) {
        switch (reason) {
            case INVALID_XML:
                return RenderErrorCode.INVALID_XML;
            case UNSUPPORTED_FORMAT:
                return RenderErrorCode.UNSUPPORTED_FORMAT;
            case LIMIT_EXCEEDED:
                return RenderErrorCode.LIMIT_EXCEEDED;
            case PAGE_NOT_FOUND:
            case AMBIGUOUS_PAGE:
                return RenderErrorCode.PAGE_NOT_FOUND;
            case INVALID_DIAGRAM:
            case INVALID_COMPRESSION:
            default:
                return RenderErrorCode.INVALID_DIAGRAM;
        }
    }
}
