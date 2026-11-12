package com.aresstack.drawio.api;

import java.util.Objects;

/**
 * Describe a non-fatal rendering deviation, such as an unsupported feature
 * that the caller explicitly chose to tolerate.
 *
 * <p>The message never contains diagram text or XML.
 */
public final class RenderWarning {

    private static final int MAX_TEXT = 256;

    private final String capabilityId;
    private final String message;

    public RenderWarning(String capabilityId, String message) {
        this.capabilityId = bounded(Objects.requireNonNull(capabilityId, "capabilityId"));
        this.message = bounded(Objects.requireNonNull(message, "message"));
    }

    /**
     * Return the capability identifier, e.g. {@code shape:mxgraph.foo} or {@code canvas:gradient}.
     */
    public String getCapabilityId() {
        return capabilityId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RenderWarning)) {
            return false;
        }
        RenderWarning that = (RenderWarning) other;
        return capabilityId.equals(that.capabilityId) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return 31 * capabilityId.hashCode() + message.hashCode();
    }

    @Override
    public String toString() {
        return capabilityId + ": " + message;
    }

    private static String bounded(String value) {
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
