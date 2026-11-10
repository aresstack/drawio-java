package com.aresstack.drawio.graal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptExecutionResultTest {

    @Test
    void returnsOutputFromSuccessfulExecution() {
        ScriptExecutionResult result = ScriptExecutionResult.success("<svg/>");

        assertTrue(result.isSuccessful());
        assertEquals("<svg/>", result.getOutput());
        assertNull(result.getErrorMessage());
    }

    @Test
    void returnsFailureWithoutSuccessfulOutput() {
        ScriptExecutionResult result = ScriptExecutionResult.failure("Unsupported browser API");

        assertFalse(result.isSuccessful());
        assertNull(result.getOutput());
        assertEquals("Unsupported browser API", result.getErrorMessage());
    }

    @Test
    void rejectsMissingFailureReason() {
        assertThrows(NullPointerException.class, () -> ScriptExecutionResult.failure(null));
        assertThrows(IllegalArgumentException.class, () -> ScriptExecutionResult.failure("   "));
    }

    @Test
    void allowsEmptySuccessfulOutputWithoutInventingAnError() {
        ScriptExecutionResult result = ScriptExecutionResult.success("");

        assertTrue(result.isSuccessful());
        assertEquals("", result.getOutput());
        assertNull(result.getErrorMessage());
    }
}
