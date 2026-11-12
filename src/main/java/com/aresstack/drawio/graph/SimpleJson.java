package com.aresstack.drawio.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read the small JSON report produced by {@code drawio-headless.js}.
 *
 * <p>Only for output of this library's own script; bounded in depth.
 */
final class SimpleJson {

    private static final int MAX_DEPTH = 32;

    private final String text;
    private int position;

    private SimpleJson(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        SimpleJson parser = new SimpleJson(text);
        Object value = parser.value(0);
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw new IllegalArgumentException("Trailing characters in JSON report");
        }
        return value;
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("JSON report nested too deeply");
        }
        skipWhitespace();
        if (position >= text.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON report");
        }
        char c = text.charAt(position);
        switch (c) {
            case '{':
                return object(depth);
            case '[':
                return array(depth);
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                return number();
        }
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        position++;
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = string();
            skipWhitespace();
            expect(':');
            map.put(key, value(depth + 1));
            skipWhitespace();
            char c = text.charAt(position++);
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Malformed JSON object");
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<Object>();
        position++;
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return list;
        }
        while (true) {
            list.add(value(depth + 1));
            skipWhitespace();
            char c = text.charAt(position++);
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new IllegalArgumentException("Malformed JSON array");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c == '\\') {
                char e = text.charAt(position++);
                switch (e) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default: out.append(e);
                }
            } else {
                out.append(c);
            }
        }
    }

    private Double number() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        if (start == position) {
            throw new IllegalArgumentException("Malformed JSON value");
        }
        return Double.valueOf(text.substring(start, position));
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) {
            throw new IllegalArgumentException("Malformed JSON literal");
        }
        position += word.length();
        return value;
    }

    private void expect(char c) {
        if (position >= text.length() || text.charAt(position) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' in JSON report");
        }
        position++;
    }

    private char peek() {
        return position < text.length() ? text.charAt(position) : 0;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }
}
