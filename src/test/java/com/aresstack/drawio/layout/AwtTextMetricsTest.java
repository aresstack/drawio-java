package com.aresstack.drawio.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwtTextMetricsTest {

    private final TextMetrics metrics = new AwtTextMetrics();
    private final FontDescriptor font =
            new FontDescriptor("sans-serif", 16, false, false);

    @Test
    void returnsDeterministicFontMetricsWithoutDisplayingUi() {
        TextBounds first = metrics.measure("Hello", font);
        TextBounds second = metrics.measure("Hello", font);

        assertTrue(first.getWidth() > 0);
        assertTrue(first.getAscent() > 0);
        assertTrue(first.getHeight() >= first.getAscent() + first.getDescent());
        assertEquals(first.getWidth(), second.getWidth());
        assertEquals(first.getHeight(), second.getHeight());
    }

    @Test
    void prefersAvailableFontsInCssFamilyList() {
        TextBounds fallback = metrics.measure("Hello",
                new FontDescriptor("UnknownFontName, sans-serif", 16, false, false));
        TextBounds expected = metrics.measure("Hello", font);

        assertEquals(expected.getWidth(), fallback.getWidth());
        assertEquals(expected.getHeight(), fallback.getHeight());
    }

    @Test
    void reportsMultilineHeightAndLongestLineWidth() {
        TextBounds first = metrics.measure("Short", font);
        TextBounds second = metrics.measure("A substantially longer line", font);
        TextBounds combined = metrics.measure("Short\nA substantially longer line", font);

        assertEquals(second.getWidth(), combined.getWidth());
        assertEquals(first.getHeight() * 2, combined.getHeight());
        assertEquals(first.getAscent(), combined.getAscent());
    }

    @Test
    void acceptsUnicodeTextWithoutSubstitutingCharacters() {
        TextBounds measured = metrics.measure("Grüße 你好", font);
        assertTrue(measured.getWidth() > 0);
        assertTrue(measured.getHeight() > 0);
    }

    @Test
    void treatsEmptyTextAsAnEmptyMeasurement() {
        TextBounds empty = metrics.measure("", font);
        assertEquals(0, empty.getWidth());
        assertEquals(0, empty.getAscent());
        assertEquals(0, empty.getDescent());
        assertEquals(0, empty.getHeight());
    }

    @Test
    void validatesFontSizeAndTextLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new FontDescriptor("sans-serif", 0, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FontDescriptor("sans-serif", Double.NaN, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new FontDescriptor("sans-serif", 1024, false, false));

        StringBuilder hugeText = new StringBuilder();
        for (int index = 0; index < 10001; index++) {
            hugeText.append('x');
        }
        assertThrows(IllegalArgumentException.class,
                () -> metrics.measure(hugeText.toString(), font));
    }
}
