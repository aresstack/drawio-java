package com.aresstack.drawio.layout;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Objects;

/**
 * Use an isolated Java2D graphics context to measure text in headless mode.
 *
 * <p>Keep mutable Graphics2D resources confined to the current call.
 */
public final class AwtTextMetrics implements TextMetrics {

    private static final int MAX_TEXT_CHARACTERS = 10000;
    private static final TextBounds EMPTY = new TextBounds(0, 0, 0, 0);

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

        Font font = resolveFont(descriptor);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        try {
            FontMetrics metrics = graphics.getFontMetrics(font);
            return measureLines(text, metrics);
        } finally {
            graphics.dispose();
        }
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
            String candidate = normalizeFamily(family);
            if (!candidate.isEmpty()) {
                Font requested = new Font(candidate, style, 1)
                        .deriveFont(descriptor.getSizePixels());
                if (!"Dialog".equalsIgnoreCase(requested.getFamily(Locale.ROOT))
                        || "Dialog".equalsIgnoreCase(candidate)) {
                    return requested;
                }
            }
        }
        return new Font(Font.SANS_SERIF, style, 1)
                .deriveFont(descriptor.getSizePixels());
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
