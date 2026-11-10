package com.aresstack.drawio.layout;

/**
 * Measure text without depending on a browser, GraalJS, or a UI component.
 */
public interface TextMetrics {

    TextBounds measure(String text, FontDescriptor font);
}
