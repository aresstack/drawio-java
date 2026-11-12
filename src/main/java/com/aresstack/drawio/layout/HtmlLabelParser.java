package com.aresstack.drawio.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Turn a draw.io label value into lines of styled text runs.
 *
 * <p>HTML labels ({@code html=1}) are treated strictly as data. Only a small
 * formatting vocabulary is interpreted: line breaks ({@code br}), block
 * elements ({@code div}, {@code p}, {@code li}, headings, ...) as line
 * boundaries, {@code b/strong}, {@code i/em}, {@code u}, {@code s/strike},
 * {@code font color/size/face}, {@code sup/sub} as plain text and inline
 * {@code style} declarations for {@code font-weight}, {@code font-style},
 * {@code font-size}, {@code font-family}, {@code color} and
 * {@code text-decoration}. Everything else contributes its text content only.
 * Content of {@code script}, {@code style}, {@code head}, {@code title} and
 * {@code template} is dropped. Nothing is executed, fetched or resolved.
 *
 * <p>Plain labels keep their characters and break lines at {@code \n}.
 */
public final class HtmlLabelParser {

    /** Maximum number of characters of a label value that is interpreted. */
    public static final int MAX_LABEL_CHARACTERS = 64 * 1024;
    private static final int MAX_NESTING = 64;

    private static final Map<String, String> ENTITIES = createEntities();
    private static final java.util.Set<String> BLOCKS = set("div", "p", "li", "ul", "ol",
            "h1", "h2", "h3", "h4", "h5", "h6", "tr", "table", "blockquote", "pre",
            "section", "article", "header", "footer", "dl", "dt", "dd", "hr", "center");
    private static final java.util.Set<String> DROPPED = set("script", "style", "head",
            "title", "template", "noscript", "iframe", "object", "svg", "math");
    private static final java.util.Set<String> VOID = set("br", "hr", "img", "input",
            "meta", "link", "wbr", "col", "area", "base", "source");

    private HtmlLabelParser() {
    }

    /**
     * Parse a label value.
     *
     * @param value label text or HTML markup
     * @param html  whether the value is HTML ({@code html=1})
     * @param base  font of the label
     * @return lines of runs, never empty
     */
    public static List<List<TextRun>> parse(String value, boolean html, RunStyle base) {
        Objects.requireNonNull(base, "base");
        String text = value == null ? "" : value;
        if (text.length() > MAX_LABEL_CHARACTERS) {
            text = text.substring(0, MAX_LABEL_CHARACTERS);
        }
        return html ? new HtmlState(base).parse(text) : parsePlain(text, base);
    }

    private static List<List<TextRun>> parsePlain(String text, RunStyle base) {
        List<List<TextRun>> lines = new ArrayList<List<TextRun>>();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            List<TextRun> runs = new ArrayList<TextRun>();
            if (!line.isEmpty()) {
                runs.add(new TextRun(line, base));
            }
            lines.add(runs);
        }
        return lines;
    }

    private static final class HtmlState {
        private final List<List<TextRun>> lines = new ArrayList<List<TextRun>>();
        private final Deque<Frame> stack = new ArrayDeque<Frame>();
        private List<TextRun> current = new ArrayList<TextRun>();
        private final StringBuilder pending = new StringBuilder();
        private boolean lastWasSpace = true;
        private int dropDepth;
        private int skippedOpen;

        HtmlState(RunStyle base) {
            stack.push(new Frame("#root", base, false));
        }

        List<List<TextRun>> parse(String html) {
            // mxText.paint replaces linefeeds of HTML labels with <br/>.
            String source = html.replace("\r\n", "\n").replace('\r', '\n');
            int i = 0;
            int length = source.length();
            while (i < length) {
                char c = source.charAt(i);
                if (c == '<') {
                    int end = tagEnd(source, i);
                    if (end < 0) {
                        appendText(source.substring(i));
                        break;
                    }
                    handleTag(source.substring(i + 1, end));
                    i = end + 1;
                } else if (c == '\n') {
                    if (dropDepth == 0) {
                        breakLine();
                    }
                    i++;
                } else {
                    int next = nextSpecial(source, i);
                    appendText(source.substring(i, next));
                    i = next;
                }
            }
            flushText();
            lines.add(current);
            // A trailing block boundary does not create an extra empty line.
            while (lines.size() > 1 && lines.get(lines.size() - 1).isEmpty()
                    && trailingBreakIsImplicit) {
                lines.remove(lines.size() - 1);
                trailingBreakIsImplicit = false;
            }
            return Collections.unmodifiableList(lines);
        }

        private boolean trailingBreakIsImplicit;

        private int nextSpecial(String source, int from) {
            int i = from;
            while (i < source.length() && source.charAt(i) != '<' && source.charAt(i) != '\n') {
                i++;
            }
            return i;
        }

        private int tagEnd(String source, int start) {
            if (source.startsWith("<!--", start)) {
                int close = source.indexOf("-->", start + 4);
                return close < 0 ? -1 : close + 2;
            }
            char quote = 0;
            for (int i = start + 1; i < source.length(); i++) {
                char c = source.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    return i;
                }
            }
            return -1;
        }

        private void handleTag(String body) {
            if (body.startsWith("!") || body.startsWith("?")) {
                return;
            }
            boolean closing = body.startsWith("/");
            String rest = closing ? body.substring(1) : body;
            int nameEnd = 0;
            while (nameEnd < rest.length() && !Character.isWhitespace(rest.charAt(nameEnd))
                    && rest.charAt(nameEnd) != '/' && rest.charAt(nameEnd) != '>') {
                nameEnd++;
            }
            String name = rest.substring(0, nameEnd).toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                return;
            }
            boolean selfClosing = rest.endsWith("/") || VOID.contains(name);
            if (closing) {
                close(name);
            } else {
                open(name, parseAttributes(rest.substring(nameEnd)), selfClosing);
            }
        }

        private void open(String name, Map<String, String> attributes, boolean selfClosing) {
            if (!selfClosing && stack.size() >= MAX_NESTING) {
                // Too deep: the element is ignored and so is the next closing
                // tag, which keeps outer frames intact and the stack bounded.
                if (DROPPED.contains(name)) {
                    dropDepth++;
                }
                skippedOpen++;
                if (BLOCKS.contains(name) && dropDepth == 0) {
                    blockBoundary();
                }
                return;
            }
            if (DROPPED.contains(name)) {
                if (!selfClosing) {
                    dropDepth++;
                    stack.push(new Frame(name, stack.peek().style, false));
                }
                return;
            }
            if (dropDepth > 0) {
                if (!selfClosing) {
                    stack.push(new Frame(name, stack.peek().style, false));
                }
                return;
            }
            if ("br".equals(name)) {
                breakLine();
                return;
            }
            boolean block = BLOCKS.contains(name);
            if (block) {
                blockBoundary();
            }
            if (selfClosing) {
                if ("hr".equals(name)) {
                    blockBoundary();
                }
                return;
            }
            flushText();
            stack.push(new Frame(name, styleFor(name, attributes, stack.peek().style), block));
        }

        private void close(String name) {
            if (skippedOpen > 0) {
                skippedOpen--;
                return;
            }
            Frame frame = null;
            for (Frame candidate : stack) {
                if (candidate.name.equals(name)) {
                    frame = candidate;
                    break;
                }
            }
            if (frame == null || "#root".equals(frame.name)) {
                return;
            }
            flushText();
            while (!stack.isEmpty()) {
                Frame popped = stack.pop();
                if (DROPPED.contains(popped.name) && dropDepth > 0) {
                    dropDepth--;
                }
                if (popped == frame) {
                    break;
                }
            }
            if (frame.block && dropDepth == 0) {
                blockBoundary();
            }
        }

        private RunStyle styleFor(String name, Map<String, String> attributes, RunStyle parent) {
            RunStyle style = parent;
            if ("b".equals(name) || "strong".equals(name) || name.matches("h[1-6]")) {
                style = style.withBold(true);
            } else if ("i".equals(name) || "em".equals(name) || "cite".equals(name)) {
                style = style.withItalic(true);
            } else if ("u".equals(name) || "ins".equals(name)) {
                style = style.withUnderline(true);
            } else if ("s".equals(name) || "strike".equals(name) || "del".equals(name)) {
                style = style.withStrikeThrough(true);
            } else if ("font".equals(name)) {
                String color = attributes.get("color");
                if (color != null) {
                    style = style.withColor(color);
                }
                String face = attributes.get("face");
                if (face != null) {
                    style = style.withFamily(face);
                }
                String size = attributes.get("size");
                if (size != null) {
                    style = style.withSize(fontTagSize(size, style.getSize()));
                }
            }
            String declarations = attributes.get("style");
            if (declarations != null) {
                style = applyCss(declarations, style);
            }
            return style;
        }

        private void appendText(String raw) {
            if (dropDepth > 0 || raw.isEmpty()) {
                return;
            }
            String decoded = decodeEntities(raw);
            for (int i = 0; i < decoded.length(); i++) {
                char c = decoded.charAt(i);
                if (c == '\u00a0') {
                    pending.append(' ');
                    lastWasSpace = false;
                } else if (Character.isWhitespace(c)) {
                    if (!lastWasSpace) {
                        pending.append(' ');
                        lastWasSpace = true;
                    }
                } else {
                    pending.append(c);
                    lastWasSpace = false;
                }
            }
        }

        private void flushText() {
            if (pending.length() > 0) {
                current.add(new TextRun(pending.toString(), stack.peek().style));
                pending.setLength(0);
            }
        }

        private void breakLine() {
            flushText();
            trimTrailingSpace(current);
            lines.add(current);
            current = new ArrayList<TextRun>();
            lastWasSpace = true;
            // Browsers create no line box for a <br> at the end of a block.
            trailingBreakIsImplicit = true;
        }

        private void blockBoundary() {
            flushText();
            if (!current.isEmpty()) {
                trimTrailingSpace(current);
                lines.add(current);
                current = new ArrayList<TextRun>();
                trailingBreakIsImplicit = true;
            }
            lastWasSpace = true;
        }

        private void trimTrailingSpace(List<TextRun> runs) {
            if (runs.isEmpty()) {
                return;
            }
            TextRun last = runs.get(runs.size() - 1);
            String text = last.getText();
            int end = text.length();
            while (end > 0 && text.charAt(end - 1) == ' ') {
                end--;
            }
            if (end == 0) {
                runs.remove(runs.size() - 1);
            } else if (end < text.length()) {
                runs.set(runs.size() - 1, new TextRun(text.substring(0, end), last.getStyle()));
            }
        }
    }

    private static final class Frame {
        final String name;
        final RunStyle style;
        final boolean block;

        Frame(String name, RunStyle style, boolean block) {
            this.name = name;
            this.style = style;
            this.block = block;
        }
    }

    static Map<String, String> parseAttributes(String text) {
        Map<String, String> attributes = new HashMap<String, String>();
        int i = 0;
        int length = text.length();
        while (i < length) {
            while (i < length && (Character.isWhitespace(text.charAt(i)) || text.charAt(i) == '/')) {
                i++;
            }
            int nameStart = i;
            while (i < length && !Character.isWhitespace(text.charAt(i))
                    && text.charAt(i) != '=' && text.charAt(i) != '/') {
                i++;
            }
            if (nameStart == i) {
                i++;
                continue;
            }
            String name = text.substring(nameStart, i).toLowerCase(Locale.ROOT);
            while (i < length && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            String value = "";
            if (i < length && text.charAt(i) == '=') {
                i++;
                while (i < length && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                if (i < length && (text.charAt(i) == '"' || text.charAt(i) == '\'')) {
                    char quote = text.charAt(i++);
                    int end = text.indexOf(quote, i);
                    end = end < 0 ? length : end;
                    value = text.substring(i, end);
                    i = Math.min(length, end + 1);
                } else {
                    int start = i;
                    while (i < length && !Character.isWhitespace(text.charAt(i))) {
                        i++;
                    }
                    value = text.substring(start, i);
                }
            }
            attributes.put(name, decodeEntities(value));
        }
        return attributes;
    }

    static RunStyle applyCss(String declarations, RunStyle base) {
        RunStyle style = base;
        for (String declaration : declarations.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).trim();
            String lower = value.toLowerCase(Locale.ROOT).replace("!important", "").trim();
            if ("font-weight".equals(property)) {
                style = style.withBold("bold".equals(lower) || "bolder".equals(lower)
                        || (lower.matches("\\d+") && Integer.parseInt(lower) >= 600));
            } else if ("font-style".equals(property)) {
                style = style.withItalic("italic".equals(lower) || "oblique".equals(lower));
            } else if ("font-size".equals(property)) {
                double size = cssSize(lower, style.getSize());
                if (size > 0) {
                    style = style.withSize(size);
                }
            } else if ("font-family".equals(property) && !value.isEmpty()) {
                style = style.withFamily(value);
            } else if ("color".equals(property) && !value.isEmpty()) {
                style = style.withColor(value);
            } else if ("text-decoration".equals(property) || "text-decoration-line".equals(property)) {
                style = style.withUnderline(lower.contains("underline"))
                        .withStrikeThrough(lower.contains("line-through"));
            }
        }
        return style;
    }

    private static double cssSize(String value, double parent) {
        try {
            if (value.endsWith("px")) {
                return Double.parseDouble(value.substring(0, value.length() - 2).trim());
            }
            if (value.endsWith("pt")) {
                return Double.parseDouble(value.substring(0, value.length() - 2).trim()) * 4.0 / 3.0;
            }
            if (value.endsWith("em")) {
                return Double.parseDouble(value.substring(0, value.length() - 2).trim()) * parent;
            }
            if (value.endsWith("%")) {
                return Double.parseDouble(value.substring(0, value.length() - 1).trim()) * parent / 100.0;
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** Map HTML {@code <font size>} values 1..7 to CSS pixel sizes. */
    private static double fontTagSize(String value, double parent) {
        double[] sizes = {10, 13, 16, 18, 24, 32, 48};
        try {
            String trimmed = value.trim();
            int level;
            if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
                level = 3 + Integer.parseInt(trimmed.startsWith("+") ? trimmed.substring(1) : trimmed);
            } else {
                level = Integer.parseInt(trimmed);
            }
            return sizes[Math.max(1, Math.min(7, level)) - 1];
        } catch (NumberFormatException exception) {
            return parent;
        }
    }

    static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int semicolon = c == '&' ? text.indexOf(';', i) : -1;
            if (semicolon > i + 1 && semicolon - i <= 12) {
                String entity = text.substring(i + 1, semicolon);
                String replacement = resolveEntity(entity);
                if (replacement != null) {
                    out.append(replacement);
                    i = semicolon + 1;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String resolveEntity(String entity) {
        if (entity.startsWith("#")) {
            try {
                int code = entity.startsWith("#x") || entity.startsWith("#X")
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1));
                if (code <= 0 || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
                    return "�";
                }
                return new String(Character.toChars(code));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return ENTITIES.get(entity);
    }

    private static Map<String, String> createEntities() {
        Map<String, String> map = new HashMap<String, String>();
        String[][] pairs = {
            {"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""}, {"apos", "'"},
            {"nbsp", "\u00a0"}, {"ensp", " "}, {"emsp", " "}, {"thinsp", " "},
            {"copy", "©"}, {"reg", "®"}, {"trade", "™"}, {"euro", "€"},
            {"pound", "£"}, {"yen", "¥"}, {"cent", "¢"}, {"sect", "§"},
            {"para", "¶"}, {"deg", "°"}, {"plusmn", "±"}, {"times", "×"},
            {"divide", "÷"}, {"micro", "µ"}, {"middot", "·"}, {"bull", "•"},
            {"hellip", "…"}, {"ndash", "–"}, {"mdash", "—"}, {"lsquo", "‘"},
            {"rsquo", "’"}, {"sbquo", "‚"}, {"ldquo", "“"}, {"rdquo", "”"},
            {"bdquo", "„"}, {"laquo", "«"}, {"raquo", "»"}, {"larr", "←"},
            {"rarr", "→"}, {"uarr", "↑"}, {"darr", "↓"}, {"harr", "↔"},
            {"auml", "ä"}, {"ouml", "ö"}, {"uuml", "ü"}, {"Auml", "Ä"},
            {"Ouml", "Ö"}, {"Uuml", "Ü"}, {"szlig", "ß"}, {"eacute", "é"},
            {"egrave", "è"}, {"aacute", "á"}, {"agrave", "à"}, {"ccedil", "ç"},
            {"ntilde", "ñ"}, {"oslash", "ø"}, {"aring", "å"}, {"iexcl", "¡"},
            {"iquest", "¿"}, {"shy", ""}, {"zwj", ""}, {"zwnj", ""}, {"check", "✓"}
        };
        for (String[] pair : pairs) {
            map.put(pair[0], pair[1]);
        }
        return Collections.unmodifiableMap(map);
    }

    private static java.util.Set<String> set(String... values) {
        return Collections.unmodifiableSet(new java.util.HashSet<String>(java.util.Arrays.asList(values)));
    }
}
