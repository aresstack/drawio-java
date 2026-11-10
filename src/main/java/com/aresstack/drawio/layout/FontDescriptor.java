package com.aresstack.drawio.layout;

import java.util.Objects;

/**
 * Describe a text font without binding the domain to AWT Font objects.
 */
public final class FontDescriptor {

    private static final float MAX_SIZE = 512f;
    private static final int MAX_FAMILY_CHARACTERS = 256;

    private final String cssFamily;
    private final float sizePixels;
    private final boolean bold;
    private final boolean italic;

    public FontDescriptor(String cssFamily, double sizePixels, boolean bold, boolean italic) {
        String family = Objects.requireNonNull(cssFamily, "cssFamily").trim();
        if (family.length() > MAX_FAMILY_CHARACTERS) {
            throw new IllegalArgumentException("Font family list exceeds size limit");
        }
        if (!Double.isFinite(sizePixels) || sizePixels <= 0 || sizePixels > MAX_SIZE) {
            throw new IllegalArgumentException("Font size must be finite and within limits");
        }

        this.cssFamily = family;
        this.sizePixels = (float) sizePixels;
        this.bold = bold;
        this.italic = italic;
    }

    public String getCssFamily() {
        return cssFamily;
    }

    public float getSizePixels() {
        return sizePixels;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }
}
