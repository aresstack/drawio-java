package com.aresstack.drawio.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RenderOptionsTest {

    @Test
    void defaultsAreSafe() {
        RenderOptions options = RenderOptions.defaults();
        assertEquals(1.0, options.getScale());
        assertNull(options.getBackground());
        assertEquals(UnsupportedFeaturePolicy.FAIL, options.getUnsupportedFeaturePolicy());
        assertEquals(false, options.getResourcePolicy().isDataUrisAllowed());
        assertNull(options.getResourcePolicy().getResolver());
        assertEquals(RenderLimits.DEFAULT_TIMEOUT, options.getLimits().getTimeout());
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> RenderOptions.builder().scale(0));
        assertThrows(IllegalArgumentException.class, () -> RenderOptions.builder().scale(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> RenderOptions.builder().scale(100));
        assertThrows(IllegalArgumentException.class, () -> RenderOptions.builder().padding(-1));
        assertThrows(IllegalArgumentException.class, () -> RenderOptions.builder().background("red"));
        assertThrows(IllegalArgumentException.class,
                () -> RenderLimits.builder().timeout(java.time.Duration.ofMillis(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class,
                () -> RenderLimits.builder().maxInputCharacters(RenderLimits.DEFAULT_MAX_INPUT_CHARACTERS + 1));
    }

    @Test
    void toBuilderRoundTrips() {
        RenderOptions options = RenderOptions.builder().scale(2).padding(4).background("#FFFFFF")
                .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.WARN).build();
        RenderOptions copy = options.toBuilder().build();
        assertEquals(2.0, copy.getScale());
        assertEquals(4, copy.getPadding());
        assertEquals("#ffffff", copy.getBackground());
        assertEquals(UnsupportedFeaturePolicy.WARN, copy.getUnsupportedFeaturePolicy());
    }

    @Test
    void pageSelectorResolvesByIndexNameAndId() throws DiagramFormatException {
        DrawioDocument document = new DrawioDocument(Arrays.asList(
                new DiagramPage(0, "a", "One", "<mxGraphModel/>"),
                new DiagramPage(1, "b", "Two", "<mxGraphModel/>")));
        assertEquals("a", PageSelector.first().select(document).getId());
        assertEquals("b", PageSelector.index(1).select(document).getId());
        assertEquals("b", PageSelector.name("Two").select(document).getId());
        assertEquals("a", PageSelector.id("a").select(document).getId());
        DiagramFormatException missing = assertThrows(DiagramFormatException.class,
                () -> PageSelector.id("zzz").select(document));
        assertEquals(DiagramFormatException.Reason.PAGE_NOT_FOUND, missing.getReason());
    }

    @Test
    void formatExceptionsMapToStableCodes() {
        RenderException exception = RenderException.fromFormatException(
                new DiagramFormatException(DiagramFormatException.Reason.INVALID_COMPRESSION, "bad"));
        assertEquals(RenderErrorCode.INVALID_DIAGRAM, exception.getCode());
        assertEquals(DiagramFormatException.Reason.INVALID_COMPRESSION, exception.getFormatReason());
        assertEquals(RenderErrorCode.PAGE_NOT_FOUND, RenderException.fromFormatException(
                new DiagramFormatException(DiagramFormatException.Reason.AMBIGUOUS_PAGE, "x")).getCode());
        assertEquals(RenderErrorCode.LIMIT_EXCEEDED, RenderException.fromFormatException(
                new DiagramFormatException(DiagramFormatException.Reason.LIMIT_EXCEEDED, "x")).getCode());
    }
}
