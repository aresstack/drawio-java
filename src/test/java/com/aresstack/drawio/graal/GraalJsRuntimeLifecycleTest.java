package com.aresstack.drawio.graal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Pin down isolation and resource behavior of the shared polyglot engine (#12/#14).
 */
class GraalJsRuntimeLifecycleTest {

    private static final DrawioHost HOST = new DrawioHost(resourceId -> {
        throw new IllegalArgumentException("disabled");
    });

    @Test
    void globalsDoNotLeakBetweenRequestsOnTheSharedEngine() {
        try (GraalJsRuntime first = new GraalJsRuntime(Duration.ofSeconds(10), true)) {
            ScriptExecutionResult result = first.execute("a.js",
                    "globalThis.leak = 'secret'; Object.prototype.polluted = 1; 'set'", HOST);
            assertTrue(result.isSuccessful(), result.getErrorMessage());
        }
        try (GraalJsRuntime second = new GraalJsRuntime(Duration.ofSeconds(10), true)) {
            ScriptExecutionResult result = second.execute("b.js",
                    "typeof leak + ',' + typeof ({}).polluted", HOST);
            assertEquals("undefined,undefined", result.getOutput());
        }
    }

    @Test
    void bundledSourcesAreLoadedOnceAndReused() {
        assertTrue(ScriptSource.bundled("drawio-canvas-adapter.js")
                == ScriptSource.bundled("drawio-canvas-adapter.js"));
        assertFalse(ScriptSource.inline("x.js", "1") == ScriptSource.inline("x.js", "1"));
    }

    @Test
    void timeoutOnSharedEngineDoesNotBreakLaterRequests() {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofMillis(200), true)) {
            ScriptExecutionResult result = runtime.execute("loop.js", "while (true) {}", HOST);
            assertFalse(result.isSuccessful());
        }
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(10), true)) {
            assertEquals("2", runtime.execute("ok.js", "String(1 + 1)", HOST).getOutput());
        }
    }

    @Test
    void repeatedAndParallelRuntimesLeaveNoThreadsBehind() throws Exception {
        for (int i = 0; i < 50; i++) {
            try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(10), true)) {
                assertTrue(runtime.execute("n.js", "var a = []; for (var j = 0; j < 1000; j++)"
                        + " a.push({j: j}); String(a.length)", HOST).isSuccessful());
            }
        }
        ExecutorService callers = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<Future<String>>();
            for (int i = 0; i < 40; i++) {
                final int value = i;
                results.add(callers.submit(() -> {
                    try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(10), true)) {
                        return runtime.execute("p.js", "var x = " + value + "; String(x * 2)", HOST)
                                .getOutput();
                    }
                }));
            }
            for (int i = 0; i < results.size(); i++) {
                assertEquals(String.valueOf(i * 2), results.get(i).get(30, TimeUnit.SECONDS));
            }
        } finally {
            callers.shutdownNow();
        }
        waitForNoRuntimeThreads();
    }

    private static void waitForNoRuntimeThreads() throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (runtimeThreads() > 0) {
            if (System.nanoTime() > until) {
                throw new AssertionError(runtimeThreads() + " runtime threads still alive");
            }
            Thread.sleep(20);
        }
    }

    private static int runtimeThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("drawio-graaljs-")) {
                count++;
            }
        }
        return count;
    }
}
