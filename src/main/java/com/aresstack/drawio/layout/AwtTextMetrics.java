package com.aresstack.drawio.layout;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use an isolated Java2D graphics context to measure text in headless mode.
 *
 * <p>Mutable Graphics2D resources stay confined to the call that resolves a
 * font. The resolved {@link FontMetrics} are immutable and cached per font
 * descriptor, because label layout measures every word of every label.
 */
public final class AwtTextMetrics implements TextMetrics {

    private static final int MAX_TEXT_CHARACTERS = 10000;
    private static final int MAX_CACHED_FONTS = 512;
    private static final TextBounds EMPTY = new TextBounds(0, 0, 0, 0);

    private final Map<String, FontMetrics> fontMetrics = new ConcurrentHashMap<String, FontMetrics>();

    @Override
    public TextBounds measure(String text, FontDescriptor descriptor) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(descriptor, "descriptor");

        if (text.length() > MAX_TEXT_CHARACTERS) {
            throw new IllegalArgumentException("Text measurement exceeds size limit");
        }
        if (text.isEmpty()) {
            return EMPTY;
        }

        return measureLines(text, metricsFor(descriptor));
    }

    private FontMetrics metricsFor(FontDescriptor descriptor) {
        String key = descriptor.getCssFamily() + '\u0000' + descriptor.isBold() + '\u0000'
                + descriptor.isItalic() + '\u0000' + Float.floatToIntBits(descriptor.getSizePixels());
        FontMetrics cached = fontMetrics.get(key);
        if (cached != null) {
            return cached;
        }
        Font font = resolveFont(descriptor);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        FontMetrics metrics;
        try {
            metrics = graphics.getFontMetrics(font);
        } finally {
            graphics.dispose();
        }
        if (fontMetrics.size() >= MAX_CACHED_FONTS) {
            fontMetrics.clear();
        }
        fontMetrics.put(key, metrics);
        return metrics;
    }

    private TextBounds measureLines(String text, FontMetrics metrics) {
        String[] lines = text.split("\\r\\n|\\r|\\n", -1);
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, metrics.stringWidth(line));
        }
        return new TextBounds(width, metrics.getAscent(), metrics.getDescent(),
                Math.multiplyExact(metrics.getHeight(), lines.length));
    }

    private Font resolveFont(FontDescriptor descriptor) {
        int style = (descriptor.isBold() ? Font.BOLD : Font.PLAIN)
                | (descriptor.isItalic() ? Font.ITALIC : Font.PLAIN);

        for (String family : descriptor.getCssFamily().split(",")) {
            for (String candidate : candidates(normalizeFamily(family))) {
                if (!candidate.isEmpty()) {
                    Font requested = new Font(candidate, style, 1)
                            .deriveFont(descriptor.getSizePixels());
                    if (!"Dialog".equalsIgnoreCase(requested.getFamily(Locale.ROOT))
                            || "Dialog".equalsIgnoreCase(candidate)) {
                        return requested;
                    }
                }
            }
        }
        return new Font(Font.SANS_SERIF, style, 1)
                .deriveFont(descriptor.getSizePixels());
    }

    /**
     * Metric-compatible substitutes for the core web fonts that draw.io uses,
     * tried in order after the requested family (Liberation and Croscore
     * fonts share the advance widths of Arial/Helvetica, Times and Courier).
     */
    private static String[] candidates(String family) {
        String key = family.toLowerCase(Locale.ROOT);
        if ("helvetica".equals(key) || "arial".equals(key) || "helvetica neue".equals(key)) {
            return new String[] {family, "Arial", "Helvetica", "Liberation Sans", "Arimo"};
        }
        if ("times new roman".equals(key) || "times".equals(key)) {
            return new String[] {family, "Times New Roman", "Liberation Serif", "Tinos"};
        }
        if ("courier new".equals(key) || "courier".equals(key)) {
            return new String[] {family, "Courier New", "Liberation Mono", "Cousine"};
        }
        return new String[] {family};
    }

    private String normalizeFamily(String family) {
        String value = family.trim().replace("\"", "").replace("'", "");
        if ("sans-serif".equalsIgnoreCase(value)) {
            return Font.SANS_SERIF;
        }
        if ("serif".equalsIgnoreCase(value)) {
            return Font.SERIF;
        }
        if ("monospace".equalsIgnoreCase(value)) {
            return Font.MONOSPACED;
        }
        return value;
    }
}
