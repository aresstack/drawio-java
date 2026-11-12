package com.aresstack.drawio.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.aresstack.drawio.api.RenderErrorCode;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;

class RenderSessionTest {

    @Test
    void unsupportedFailsByDefaultWithStencilCode() {
        RenderSession session = new RenderSession(RenderOptions.defaults());
        RenderException exception = assertThrows(RenderException.class,
                () -> session.unsupported("shape:mxgraph.unknown", "Shape is not registered"));
        assertEquals(RenderErrorCode.UNSUPPORTED_STENCIL, exception.getCode());
        assertEquals("shape:mxgraph.unknown", exception.getCapabilityId());
    }

    @Test
    void unsupportedCanvasFeatureUsesFeatureCode() {
        RenderSession session = new RenderSession(RenderOptions.defaults());
        RenderException exception = assertThrows(RenderException.class,
                () -> session.unsupported("canvas:gradient", "Gradients are not supported"));
        assertEquals(RenderErrorCode.UNSUPPORTED_FEATURE, exception.getCode());
    }

    @Test
    void warnPolicyRecordsDeduplicatedWarningsAndMarksIncomplete() throws RenderException {
        RenderSession session = new RenderSession(RenderOptions.builder()
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN).build());
        session.unsupported("shape:a", "missing");
        session.unsupported("shape:a", "missing");
        session.warn("font:fallback", "Font substituted");
        assertTrue(session.isIncomplete());
        assertEquals(2, session.warnings().size());
    }

    @Test
    void plainWarningDoesNotMarkIncomplete() {
        RenderSession session = new RenderSession(RenderOptions.defaults());
        session.warn("font:fallback", "Font substituted");
        assertFalse(session.isIncomplete());
    }

    @Test
    void missingResourceUsesDedicatedCode() {
        RenderSession session = new RenderSession(RenderOptions.defaults());
        RenderException exception = assertThrows(RenderException.class,
                () -> session.missingResource("image:url", "External images are not allowed"));
        assertEquals(RenderErrorCode.MISSING_RESOURCE, exception.getCode());
    }

    @Test
    void cancelRunsHooksOnceAndLateHooksImmediately() {
        RenderSession session = new RenderSession(RenderOptions.defaults());
        AtomicInteger calls = new AtomicInteger();
        session.onCancel(calls::incrementAndGet);
        session.onCancel(() -> {
            throw new IllegalStateException("broken hook");
        });
        session.onCancel(calls::incrementAndGet);
        assertTrue(session.cancel(RenderErrorCode.TIMEOUT));
        assertFalse(session.cancel(RenderErrorCode.CANCELLED));
        assertEquals(2, calls.get());
        session.onCancel(calls::incrementAndGet);
        assertEquals(3, calls.get());
        RenderException exception = assertThrows(RenderException.class, session::checkCancelled);
        assertEquals(RenderErrorCode.TIMEOUT, exception.getCode());
    }

    @Test
    void expiredDeadlineReportsTimeout() throws InterruptedException {
        RenderSession session = new RenderSession(RenderOptions.builder()
                .limits(RenderLimits.builder().timeout(Duration.ofMillis(1)).build()).build());
        Thread.sleep(5);
        assertTrue(session.isExpired());
        assertEquals(1, session.remaining().toMillis());
        RenderException exception = assertThrows(RenderException.class, session::checkCancelled);
        assertEquals(RenderErrorCode.TIMEOUT, exception.getCode());
    }

    @Test
    void warningsAreBounded() {
        RenderSession session = new RenderSession(RenderOptions.defaults());
        for (int i = 0; i < 500; i++) {
            session.warn("w:" + i, "warning");
        }
        assertEquals(201, session.warnings().size());
        assertEquals("diagnostics:truncated", session.warnings().get(200).getCapabilityId());
    }
}
