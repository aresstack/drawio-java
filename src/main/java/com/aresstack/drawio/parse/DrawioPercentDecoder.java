package com.aresstack.drawio.parse;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import com.aresstack.drawio.api.DiagramFormatException;

/**
 * Decode draw.io's percent-encoded UTF-8 without silently replacing invalid bytes.
 *
 * <p>Matches {@code decodeURIComponent} in draw.io's {@code Graph.decompress}:
 * only {@code %XX} escapes are decoded; {@code +} stays a plus sign.
 */
final class DrawioPercentDecoder {

    String decode(byte[] compressedOutput) throws DiagramFormatException {
        String escaped = decodeUtf8(compressedOutput);
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(escaped.length());

        for (int position = 0; position < escaped.length();) {
            int character = escaped.codePointAt(position);
            if (character == '%') {
                position = decodeEscape(escaped, position, decoded);
            } else {
                appendUnescapedCodePoint(character, decoded);
                position += Character.charCount(character);
            }
        }
        return decodeUtf8(decoded.toByteArray());
    }

    private int decodeEscape(String escaped, int position, ByteArrayOutputStream decoded)
            throws DiagramFormatException {
        if (position + 2 >= escaped.length()) {
            throw invalidEncoding();
        }

        int high = Character.digit(escaped.charAt(position + 1), 16);
        int low = Character.digit(escaped.charAt(position + 2), 16);
        if (high < 0 || low < 0) {
            throw invalidEncoding();
        }

        decoded.write((high << 4) | low);
        return position + 3;
    }

    private void appendUnescapedCodePoint(int character, ByteArrayOutputStream decoded) {
        if (character <= 0x7f) {
            decoded.write(character);
            return;
        }

        byte[] encoded = new String(Character.toChars(character))
                .getBytes(StandardCharsets.UTF_8);
        decoded.write(encoded, 0, encoded.length);
    }

    private String decodeUtf8(byte[] bytes) throws DiagramFormatException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new DiagramFormatException(DiagramFormatException.Reason.INVALID_COMPRESSION,
                    "Compressed draw.io page contains invalid UTF-8", exception);
        }
    }

    private DiagramFormatException invalidEncoding() {
        return new DiagramFormatException(DiagramFormatException.Reason.INVALID_COMPRESSION,
                "Compressed draw.io page has invalid percent encoding");
    }
}
