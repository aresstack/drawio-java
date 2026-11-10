package com.aresstack.drawio.graal;

import java.util.Objects;

/**
 * Carry an execution result without exposing GraalJS types to its callers.
 *
 * <p>Adapt the result concept from mermaid-java's JsExecutionResult.
 * Keep this type internal until the rendering API is designed.
 */
final class ScriptExecutionResult {

    private final boolean successful;
    private final String output;
    private final String errorMessage;

    private ScriptExecutionResult(boolean successful, String output, String errorMessage) {
        this.successful = successful;
        this.output = output;
        this.errorMessage = errorMessage;
    }

    public static ScriptExecutionResult success(String output) {
        return new ScriptExecutionResult(true, output, null);
    }

    public static ScriptExecutionResult failure(String errorMessage) {
        String message = Objects.requireNonNull(errorMessage, "errorMessage");
        if (message.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        return new ScriptExecutionResult(false, null, message);
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
}
