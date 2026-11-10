package com.aresstack.drawio.graal;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.layout.AwtTextMetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawioTextMetricsBridgeTest {

    @Test
    void measuresRealJava2dTextThroughTheRestrictedHost() {
        DrawioHost host = new DrawioHost(resourceId -> null,
                null, new AwtTextMetrics());

        ScriptExecutionResult result = evaluate(
                "drawioHost.measureText('Grüße', 'sans-serif', 16, false, false)", host);

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        String[] metrics = result.getOutput().split(",");
        assertEquals(4, metrics.length);
        assertTrue(Integer.parseInt(metrics[0]) > 0);
        assertTrue(Integer.parseInt(metrics[1]) > 0);
        assertTrue(Integer.parseInt(metrics[3]) > 0);
    }

    @Test
    void rejectsMissingTextMeasurementCapability() {
        DrawioHost host = new DrawioHost(resourceId -> null);

        ScriptExecutionResult result = evaluate(
                "drawioHost.measureText('x', 'sans-serif', 16, false, false)", host);

        assertFalse(result.isSuccessful());
    }

    @Test
    void doesNotExposeJavaReflectionOrFilesystemThroughTextMetrics() {
        DrawioHost host = new DrawioHost(resourceId -> null,
                null, new AwtTextMetrics());

        ScriptExecutionResult result = evaluate(
                "typeof drawioHost.getClass + ':' + typeof require + ':'"
                        + " + typeof drawioHost.measureText", host);

        assertTrue(result.isSuccessful(), result.getErrorMessage());
        assertEquals("undefined:undefined:function", result.getOutput());
    }

    private ScriptExecutionResult evaluate(String javascript, DrawioHost host) {
        try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(15))) {
            return runtime.execute("drawio-text-metrics.js", javascript, host);
        }
    }
}
