package com.aresstack.drawio.graal;

import java.util.Objects;

/**
 * Carry an execution result without exposing GraalJS types to its callers.
 *
 * <p>Adapt the result concept from mermaid-java's JsExecutionResult.
 * Keep this infrastructure result separate from the future rendering API.
 */
public final class ScriptExecutionResult {

    private final boolean successful;
    private final String output;
    private final String errorMessage;
    private final boolean timedOut;

    private ScriptExecutionResult(boolean successful, String output, String errorMessage, boolean timedOut) {
        this.successful = successful;
        this.output = output;
        this.errorMessage = errorMessage;
        this.timedOut = timedOut;
    }

    public static ScriptExecutionResult success(String output) {
        return new ScriptExecutionResult(true, output, null, false);
    }

    public static ScriptExecutionResult failure(String errorMessage) {
        String message = Objects.requireNonNull(errorMessage, "errorMessage");
        if (message.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        return new ScriptExecutionResult(false, null, message, false);
    }

    /** A failure because the execution exceeded its time limit. */
    public static ScriptExecutionResult timeout(String errorMessage) {
        ScriptExecutionResult failure = failure(errorMessage);
        return new ScriptExecutionResult(false, null, failure.errorMessage, true);
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /** Whether the execution was stopped because it exceeded its time limit. */
    public boolean isTimedOut() {
        return timedOut;
    }
}
