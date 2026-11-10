package com.aresstack.drawio.layout;

/**
 * Return one or more lines of font metrics in Java2D pixel units.
 */
public final class TextBounds {

    private final int width;
    private final int ascent;
    private final int descent;
    private final int height;

    public TextBounds(int width, int ascent, int descent, int height) {
        if (width < 0 || ascent < 0 || descent < 0 || height < 0) {
            throw new IllegalArgumentException("Text dimensions must not be negative");
        }
        this.width = width;
        this.ascent = ascent;
        this.descent = descent;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getAscent() {
        return ascent;
    }

    public int getDescent() {
        return descent;
    }

    public int getHeight() {
        return height;
    }
}
