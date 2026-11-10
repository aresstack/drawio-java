package com.aresstack.drawio.graal;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalJsRuntimeTest {

    private static final DrawioHost APPROVED_HOST =
            new DrawioHost(resourceId -> "greeting".equals(resourceId) ? "hello" : null);

    @Test
    void evaluatesJavaScriptWithOnlyApprovedHostMethods() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            ScriptExecutionResult result =
                    runtime.execute("greeting.js", "drawioHost.loadResource('greeting')", APPROVED_HOST);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("hello", result.getOutput());
        }
    }

    @Test
    void rejectsJavaClassLookupAndInheritedHostMethods() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            ScriptExecutionResult javaType =
                    runtime.execute("java-type.js", "Java.type('java.lang.System')", APPROVED_HOST);
            ScriptExecutionResult hostReflection =
                    runtime.execute("reflection.js", "typeof drawioHost.getClass", APPROVED_HOST);

            assertFalse(javaType.isSuccessful());
            assertTrue(hostReflection.isSuccessful(), hostReflection.getErrorMessage());
            assertEquals("undefined", hostReflection.getOutput());
        }
    }

    @Test
    void doesNotExposeNodeOrBrowserNetworkAccess() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            ScriptExecutionResult result = runtime.execute("capabilities.js",
                    "typeof require + ':' + typeof process + ':' + typeof fetch", APPROVED_HOST);

            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("undefined:undefined:undefined", result.getOutput());
        }
    }

    @Test
    void usesIndependentContextsForSeparateEvaluations() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            ScriptExecutionResult first = runtime.execute("first.js", "globalThis.counter = 42", APPROVED_HOST);
            ScriptExecutionResult second = runtime.execute("second.js", "typeof globalThis.counter", APPROVED_HOST);

            assertTrue(first.isSuccessful(), first.getErrorMessage());
            assertTrue(second.isSuccessful(), second.getErrorMessage());
            assertEquals("undefined", second.getOutput());
        }
    }

    @Test
    void loadsBundledJavaScriptWithoutGuestFileAccess() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            ScriptExecutionResult result = runtime.executeBundled("smoke.js", APPROVED_HOST);
            assertTrue(result.isSuccessful(), result.getErrorMessage());
            assertEquals("hello world", result.getOutput());
            assertThrows(IllegalArgumentException.class,
                    () -> runtime.executeBundled("../secret.js", APPROVED_HOST));
        }
    }

    @Test
    void reportsGuestErrorsWithoutAbortingTheRuntime() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            ScriptExecutionResult failure =
                    runtime.execute("broken.js", "throw new Error('failure')", APPROVED_HOST);
            ScriptExecutionResult next = runtime.execute("healthy.js", "2 + 3", APPROVED_HOST);

            assertFalse(failure.isSuccessful());
            assertTrue(next.isSuccessful(), next.getErrorMessage());
            assertEquals("5", next.getOutput());
        }
    }

    @Test
    void stopsInfiniteScriptsAfterTimeout() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(3))) {
            ScriptExecutionResult result = runtime.execute("loop.js", "while (true) {}", APPROVED_HOST);

            assertFalse(result.isSuccessful());
            assertTrue(result.getErrorMessage().contains("timed out"));
            assertThrows(IllegalStateException.class,
                    () -> runtime.execute("next.js", "true", APPROVED_HOST));
        }
    }

    @Test
    void rejectsExecutionsAfterClose() {
        GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15));
        runtime.close();

        assertThrows(IllegalStateException.class,
                () -> runtime.execute("late.js", "1 + 1", APPROVED_HOST));
    }
}
