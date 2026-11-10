package com.aresstack.drawio.canvas;

import java.awt.Color;
import java.util.Locale;

/**
 * Convert supported Draw.io colors to Java2D colors.
 *
 * <p>Reject unknown colors rather than silently changing diagram semantics.
 */
final class CanvasColors {

    private CanvasColors() {
    }

    static Color parse(String value) {
        if (value == null || "none".equalsIgnoreCase(value)
                || "transparent".equalsIgnoreCase(value)) {
            return null;
        }

        String color = value.trim().toLowerCase(Locale.ROOT);
        switch (color) {
            case "black":
                return Color.BLACK;
            case "white":
                return Color.WHITE;
            case "red":
                return Color.RED;
            case "blue":
                return Color.BLUE;
            case "green":
                return new Color(0x008000);
            case "yellow":
                return Color.YELLOW;
            case "gray":
            case "grey":
                return Color.GRAY;
            default:
                return hexadecimal(color);
        }
    }

    private static Color hexadecimal(String value) {
        if (value.matches("#[0-9a-f]{3}")) {
            int red = Character.digit(value.charAt(1), 16);
            int green = Character.digit(value.charAt(2), 16);
            int blue = Character.digit(value.charAt(3), 16);
            return new Color(red * 17, green * 17, blue * 17);
        }

        if (value.matches("#[0-9a-f]{6}")) {
            return new Color(Integer.parseInt(value.substring(1), 16));
        }

        throw new IllegalArgumentException("Unsupported canvas color: " + value);
    }
}
