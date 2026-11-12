package com.aresstack.drawio.canvas;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve CSS font-family lists to locally installed AWT fonts.
 *
 * <p>Use the same resolution rule as {@code AwtTextMetrics}: the first
 * candidate that AWT does not map to its "Dialog" fallback wins; generic CSS
 * families map to the Java logical fonts; {@code SansSerif} is the last resort.
 * Never load fonts from files or URLs.
 */
final class CanvasFonts {

    private static final int MAX_FAMILY_CHARACTERS = 256;
    private static final Map<String, String> RESOLVED = new ConcurrentHashMap<String, String>();

    private CanvasFonts() {
    }

    static Font resolve(String cssFamily, int fontStyle, double size) {
        String family = resolveFamily(cssFamily == null ? "" : cssFamily);
        int awtStyle = ((fontStyle & DrawioCanvas.FONT_BOLD) != 0 ? Font.BOLD : Font.PLAIN)
                | ((fontStyle & DrawioCanvas.FONT_ITALIC) != 0 ? Font.ITALIC : Font.PLAIN);
        Font font = new Font(family, awtStyle, 1).deriveFont((float) size);
        boolean underline = (fontStyle & DrawioCanvas.FONT_UNDERLINE) != 0;
        boolean strike = (fontStyle & DrawioCanvas.FONT_STRIKETHROUGH) != 0;
        if (underline || strike) {
            Map<TextAttribute, Object> attributes = new HashMap<TextAttribute, Object>();
            if (underline) {
                attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
            }
            if (strike) {
                attributes.put(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON);
            }
            font = font.deriveFont(attributes);
        }
        return font;
    }

    private static String resolveFamily(String cssFamily) {
        if (cssFamily.length() > MAX_FAMILY_CHARACTERS) {
            throw new IllegalArgumentException("Font family list exceeds size limit");
        }
        String cached = RESOLVED.get(cssFamily);
        if (cached != null) {
            return cached;
        }
        String result = Font.SANS_SERIF;
        for (String family : cssFamily.split(",")) {
            String candidate = normalizeFamily(family);
            if (candidate.isEmpty()) {
                continue;
            }
            Font requested = new Font(candidate, Font.PLAIN, 1);
            if (!"Dialog".equalsIgnoreCase(requested.getFamily(Locale.ROOT))
                    || "Dialog".equalsIgnoreCase(candidate)) {
                result = candidate;
                break;
            }
        }
        if (RESOLVED.size() < 256) {
            RESOLVED.put(cssFamily, result);
        }
        return result;
    }

    private static String normalizeFamily(String family) {
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
