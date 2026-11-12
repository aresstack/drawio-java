package com.aresstack.drawio.layout;

import java.util.Objects;

/**
 * A piece of label text with one style.
 */
public final class TextRun {

    private final String text;
    private final RunStyle style;

    public TextRun(String text, RunStyle style) {
        this.text = Objects.requireNonNull(text, "text");
        this.style = Objects.requireNonNull(style, "style");
    }

    public String getText() {
        return text;
    }

    public RunStyle getStyle() {
        return style;
    }
}
