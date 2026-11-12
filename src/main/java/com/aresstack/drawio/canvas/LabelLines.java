package com.aresstack.drawio.canvas;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Split a label into lines for painting.
 *
 * <p>Plain text splits at line feeds only, exactly like
 * {@code mxSvgCanvas2D.plainText}. HTML labels use a deliberately small,
 * safe subset until the shared label layout of the graph pipeline (#9) is
 * wired in: {@code br}, {@code div}, {@code p} and {@code li} end a line,
 * all other tags are dropped (never interpreted, executed or fetched), and
 * the common character entities are decoded. Wrapping, when requested,
 * breaks greedily at spaces using the font's advance widths.
 */
final class LabelLines {

    static final int MAX_LABEL_CHARACTERS = 20000;
    private static final int MAX_LINES = 2000;

    private LabelLines() {
    }

    static List<String> plain(String text) {
        checkLength(text);
        List<String> lines = new ArrayList<String>();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            lines.add(line);
            if (lines.size() > MAX_LINES) {
                throw new IllegalArgumentException("Label exceeds the line limit");
            }
        }
        return lines;
    }

    static List<String> html(String markup) {
        checkLength(markup);
        StringBuilder text = new StringBuilder(markup.length());
        int index = 0;
        while (index < markup.length()) {
            char current = markup.charAt(index);
            if (current == '<') {
                int end = markup.indexOf('>', index + 1);
                if (end < 0) {
                    text.append(markup, index, markup.length());
                    break;
                }
                String tag = tagName(markup.substring(index + 1, end));
                boolean closing = markup.charAt(index + 1) == '/';
                if ("br".equals(tag)) {
                    text.append('\n');
                } else if (isBlock(tag) && (closing || text.length() > 0
                        && text.charAt(text.length() - 1) != '\n')) {
                    if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                        text.append('\n');
                    }
                }
                index = end + 1;
            } else if (current == '&') {
                int end = markup.indexOf(';', index + 1);
                String decoded = end > index && end - index <= 10
                        ? entity(markup.substring(index + 1, end)) : null;
                if (decoded == null) {
                    text.append('&');
                    index++;
                } else {
                    text.append(decoded);
                    index = end + 1;
                }
            } else if (current == '\n' || current == '\r' || current == '\t') {
                text.append(' ');
                index++;
            } else {
                text.append(current);
                index++;
            }
        }
        String result = text.toString();
        if (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        List<String> lines = new ArrayList<String>();
        for (String line : result.split("\n", -1)) {
            lines.add(collapseSpaces(line));
            if (lines.size() > MAX_LINES) {
                throw new IllegalArgumentException("Label exceeds the line limit");
            }
        }
        return lines;
    }

    static List<String> wrap(List<String> lines, Font font, FontRenderContext context, double width) {
        if (!(width > 0)) {
            return lines;
        }
        List<String> wrapped = new ArrayList<String>();
        for (String line : lines) {
            String[] words = line.split(" ", -1);
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (current.length() > 0 && advance(font, context, candidate) > width) {
                    wrapped.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            wrapped.add(current.toString());
            if (wrapped.size() > MAX_LINES) {
                throw new IllegalArgumentException("Label exceeds the line limit");
            }
        }
        return wrapped;
    }

    static double advance(Font font, FontRenderContext context, String text) {
        return text.isEmpty() ? 0 : font.getStringBounds(text, context).getWidth();
    }

    private static void checkLength(String text) {
        if (text.length() > MAX_LABEL_CHARACTERS) {
            throw new IllegalArgumentException("Label exceeds the size limit");
        }
    }

    private static String tagName(String content) {
        String value = content.trim();
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int end = 0;
        while (end < value.length() && Character.isLetterOrDigit(value.charAt(end))) {
            end++;
        }
        return value.substring(0, end).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlock(String tag) {
        return "div".equals(tag) || "p".equals(tag) || "li".equals(tag)
                || "ul".equals(tag) || "ol".equals(tag) || "h1".equals(tag)
                || "h2".equals(tag) || "h3".equals(tag) || "h4".equals(tag)
                || "h5".equals(tag) || "h6".equals(tag) || "tr".equals(tag);
    }

    private static String entity(String name) {
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
            case "#39":
                return "'";
            case "nbsp":
                return " ";
            default:
                break;
        }
        try {
            int codePoint;
            if (name.startsWith("#x") || name.startsWith("#X")) {
                codePoint = Integer.parseInt(name.substring(2), 16);
            } else if (name.startsWith("#")) {
                codePoint = Integer.parseInt(name.substring(1));
            } else {
                return null;
            }
            if (!Character.isValidCodePoint(codePoint) || codePoint == 0) {
                return null;
            }
            return new String(Character.toChars(codePoint));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String collapseSpaces(String line) {
        StringBuilder result = new StringBuilder(line.length());
        boolean space = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == ' ') {
                if (!space) {
                    result.append(' ');
                }
                space = true;
            } else {
                result.append(current == ' ' ? ' ' : current);
                space = false;
            }
        }
        return result.toString().trim();
    }
}
