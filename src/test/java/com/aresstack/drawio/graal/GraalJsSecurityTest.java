package com.aresstack.drawio.graal;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the guest capabilities of the restricted GraalJS context (#15 security
 * check, see docs/SECURITY.md). If a GraalJS upgrade or a context-builder
 * change widens what JavaScript can reach, one of these assertions fails.
 */
class GraalJsSecurityTest {

    private static final DrawioHost HOST = new DrawioHost(resourceId -> null);

    @Test
    void polyglotBindingsAndOtherLanguagesAreUnreachable() {
        ScriptExecutionResult result = run("typeof Polyglot");
        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("undefined", result.getOutput());
    }

    @Test
    void scriptLoadingFromFilesAndUrlsIsBlocked() {
        assertFalse(run("load('file:///etc/hostname')").isSuccessful());
        assertFalse(run("load('https://example.invalid/x.js')").isSuccessful());
        assertFalse(run("loadWithNewGlobal({script: 'load(\"file:///etc/hostname\")', name: 'x'})")
                .isSuccessful());
    }

    @Test
    void hostClassesPackagesAndReflectionAreBlocked() {
        assertFalse(run("Java.type('java.lang.Runtime')").isSuccessful());
        assertFalse(run("Java.type('java.io.File')").isSuccessful());
        ScriptExecutionResult packages = run("String(Packages.java.lang.System.exit)");
        assertTrue(!packages.isSuccessful() || !packages.getOutput().contains("function"),
                "java.lang.System must not be reachable through Packages");
        ScriptExecutionResult reflection = run("typeof drawioHost.getClass + ':' + typeof drawioHost.hashCode");
        assertTrue(reflection.isSuccessful(), reflection.getErrorMessage());
        assertEquals("undefined:undefined", reflection.getOutput());
    }

    @Test
    void environmentProcessAndThreadsAreUnavailable() {
        ScriptExecutionResult result = run("typeof process + ':' + typeof require");
        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("undefined:undefined", result.getOutput());
        // The function object exists in GraalJS, but host class loading is denied.
        assertFalse(run("Java.addToClasspath('/tmp'); 'loaded'").isSuccessful());
        assertFalse(run("new (Java.type('java.lang.Thread'))(function(){}).start()").isSuccessful());
    }

    @Test
    void consoleAndPrintOutputIsDiscarded() {
        ScriptExecutionResult result = run("print('leak'); console.log('leak'); 'done'");
        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("done", result.getOutput());
    }

    private static ScriptExecutionResult run(String script) {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            return runtime.execute("security.js", script, HOST);
        }
    }
}
