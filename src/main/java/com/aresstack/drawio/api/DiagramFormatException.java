package com.aresstack.drawio.api;

/**
 * Report a draw.io document error without exposing untrusted diagram contents.
 */
public final class DiagramFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        INVALID_XML,
        UNSUPPORTED_FORMAT,
        INVALID_DIAGRAM,
        INVALID_COMPRESSION,
        LIMIT_EXCEEDED,
        PAGE_NOT_FOUND,
        AMBIGUOUS_PAGE
    }

    private final Reason reason;

    public DiagramFormatException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DiagramFormatException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
