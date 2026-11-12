package com.aresstack.drawio.layout;

import java.util.Objects;

/**
 * Immutable font and decoration of a run of label text.
 *
 * <p>A {@code null} color means the label's font color.
 */
public final class RunStyle {

    private static final double MIN_SIZE = 1;
    private static final double MAX_SIZE = 512;
    private static final int MAX_VALUE_CHARACTERS = 256;

    private final String family;
    private final double size;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strikeThrough;
    private final String color;

    public RunStyle(String family, double size, boolean bold, boolean italic) {
        this(family, size, bold, italic, false, false, null);
    }

    private RunStyle(String family, double size, boolean bold, boolean italic,
                     boolean underline, boolean strikeThrough, String color) {
        this.family = bound(Objects.requireNonNull(family, "family"));
        this.size = Double.isFinite(size) ? Math.max(MIN_SIZE, Math.min(MAX_SIZE, size)) : 12;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikeThrough = strikeThrough;
        this.color = color == null ? null : bound(color);
    }

    private static String bound(String value) {
        String trimmed = value.trim();
        return trimmed.length() > MAX_VALUE_CHARACTERS ? trimmed.substring(0, MAX_VALUE_CHARACTERS) : trimmed;
    }

    public RunStyle withFamily(String value) {
        return new RunStyle(value, size, bold, italic, underline, strikeThrough, color);
    }

    public RunStyle withSize(double value) {
        return new RunStyle(family, value, bold, italic, underline, strikeThrough, color);
    }

    public RunStyle withBold(boolean value) {
        return new RunStyle(family, size, value, italic, underline, strikeThrough, color);
    }

    public RunStyle withItalic(boolean value) {
        return new RunStyle(family, size, bold, value, underline, strikeThrough, color);
    }

    public RunStyle withUnderline(boolean value) {
        return new RunStyle(family, size, bold, italic, value, strikeThrough, color);
    }

    public RunStyle withStrikeThrough(boolean value) {
        return new RunStyle(family, size, bold, italic, underline, value, color);
    }

    public RunStyle withColor(String value) {
        return new RunStyle(family, size, bold, italic, underline, strikeThrough, value);
    }

    public String getFamily() {
        return family;
    }

    public double getSize() {
        return size;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public boolean isStrikeThrough() {
        return strikeThrough;
    }

    /** CSS color of the run, or {@code null} for the label font color. */
    public String getColor() {
        return color;
    }

    public FontDescriptor toFont() {
        return new FontDescriptor(family.isEmpty() ? "sans-serif" : family, size, bold, italic);
    }
}
