package com.aresstack.drawio.graal;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.graalvm.polyglot.HostAccess;

/**
 * Provide budgeted binary decoding capabilities to guest JavaScript.
 *
 * <p>The original draw.io stencil decoder needs {@code atob},
 * {@code pako.inflateRaw} and {@code TextDecoder}. GraalJS has none of these
 * browser APIs, so {@code drawio-stencil-runtime.js} implements them on top of
 * this bridge. Binary data crosses the guest boundary as Latin-1 strings: every
 * character holds exactly one byte value from 0 to 255. Strings convert safely
 * under {@link HostAccess#EXPLICIT} without exposing Java arrays to scripts.
 *
 * <p>Every method enforces input size limits. Raw inflation additionally
 * enforces a per-call output cap and a cumulative output budget for the
 * lifetime of this codec, so a hostile or corrupted payload cannot expand
 * without bound.
 */
public final class DrawioBinaryCodec {

    /** Default maximum inflated size of a single stencil set: 8 MiB. */
    public static final int DEFAULT_MAX_INFLATED_BYTES_PER_CALL = 8 * 1024 * 1024;

    /** Default cumulative inflation budget of one codec: 64 MiB. */
    public static final long DEFAULT_MAX_TOTAL_INFLATED_BYTES = 64L * 1024 * 1024;

    private static final int MAX_INPUT_CHARACTERS = 16 * 1024 * 1024;
    private static final int CHUNK_BYTES = 64 * 1024;

    private final int maxInflatedBytesPerCall;
    private final long maxTotalInflatedBytes;
    private final AtomicLong totalInflatedBytes = new AtomicLong();

    /**
     * Create a codec with the default budgets.
     */
    public DrawioBinaryCodec() {
        this(DEFAULT_MAX_INFLATED_BYTES_PER_CALL, DEFAULT_MAX_TOTAL_INFLATED_BYTES);
    }

    /**
     * Create a codec with explicit inflation budgets.
     *
     * @param maxInflatedBytesPerCall maximum output of a single raw inflation
     * @param maxTotalInflatedBytes maximum cumulative output of all inflations
     */
    public DrawioBinaryCodec(int maxInflatedBytesPerCall, long maxTotalInflatedBytes) {
        if (maxInflatedBytesPerCall < 1) {
            throw new IllegalArgumentException("maxInflatedBytesPerCall must be positive");
        }
        if (maxTotalInflatedBytes < 1) {
            throw new IllegalArgumentException("maxTotalInflatedBytes must be positive");
        }
        this.maxInflatedBytesPerCall = maxInflatedBytesPerCall;
        this.maxTotalInflatedBytes = maxTotalInflatedBytes;
    }

    /**
     * Decode base64 text like the browser's {@code atob}.
     *
     * <p>ASCII whitespace is ignored and padding is optional, as in the WHATWG
     * forgiving-base64 algorithm. Any other non-alphabet character fails.
     *
     * @return the decoded bytes as a Latin-1 string
     */
    @HostAccess.Export
    public String decodeBase64(String text) {
        requireInput(text);
        String compact = stripAsciiWhitespace(text);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid base64 input");
        }
        return toLatin1String(bytes, bytes.length);
    }

    /**
     * Inflate raw DEFLATE data (RFC 1951, no zlib or gzip header) like
     * {@code pako.inflateRaw}.
     *
     * @param latin1 compressed bytes as a Latin-1 string
     * @return the inflated bytes as a Latin-1 string
     * @throws IllegalStateException when an output budget would be exceeded
     * @throws IllegalArgumentException when the input is not a complete raw
     *         DEFLATE stream
     */
    @HostAccess.Export
    public String inflateRaw(String latin1) {
        requireInput(latin1);
        byte[] input = fromLatin1String(latin1);
        // Raw zlib mode may need one extra dummy input byte, see Inflater(boolean).
        byte[] padded = new byte[input.length + 1];
        System.arraycopy(input, 0, padded, 0, input.length);

        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(padded);
            byte[] output = new byte[Math.min(CHUNK_BYTES, maxInflatedBytesPerCall)];
            int length = 0;
            while (!inflater.finished()) {
                if (length == output.length) {
                    if (length >= maxInflatedBytesPerCall) {
                        throw new IllegalStateException("Inflated stencil data exceeds "
                                + maxInflatedBytesPerCall + " bytes");
                    }
                    byte[] grown = new byte[(int) Math.min(
                            (long) output.length * 2, maxInflatedBytesPerCall)];
                    System.arraycopy(output, 0, grown, 0, length);
                    output = grown;
                }
                int count = inflater.inflate(output, length, output.length - length);
                if (count == 0 && !inflater.finished()) {
                    // Output space was available, so no progress means missing input or a dictionary.
                    throw new IllegalArgumentException("Truncated or invalid raw DEFLATE data");
                }
                length += count;
            }
            if (inflater.getRemaining() > 1) {
                throw new IllegalArgumentException("Unexpected data after raw DEFLATE stream");
            }
            if (inflater.getRemaining() == 0) {
                // The dummy byte was consumed as payload: the real input was truncated.
                throw new IllegalArgumentException("Truncated or invalid raw DEFLATE data");
            }
            reserveTotal(length);
            return toLatin1String(output, length);
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("Invalid raw DEFLATE data");
        } finally {
            inflater.end();
        }
    }

    /**
     * Decode UTF-8 bytes strictly, like a fatal {@code TextDecoder('utf-8')}.
     *
     * <p>A leading byte order mark is removed, as the default
     * {@code TextDecoder} does. Malformed or truncated sequences fail.
     *
     * @param latin1 UTF-8 bytes as a Latin-1 string
     * @return the decoded text
     */
    @HostAccess.Export
    public String decodeUtf8(String latin1) {
        requireInput(latin1);
        byte[] bytes = fromLatin1String(latin1);
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer chars = decoder.decode(
                    ByteBuffer.wrap(bytes, offset, bytes.length - offset));
            return chars.toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF-8 data");
        }
    }

    /**
     * Return the number of bytes inflated so far by this codec.
     */
    public long getTotalInflatedBytes() {
        return totalInflatedBytes.get();
    }

    private void reserveTotal(int length) {
        while (true) {
            long current = totalInflatedBytes.get();
            long next = current + length;
            if (next > maxTotalInflatedBytes) {
                throw new IllegalStateException("Cumulative inflated stencil data exceeds "
                        + maxTotalInflatedBytes + " bytes");
            }
            if (totalInflatedBytes.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private static void requireInput(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Input must not be null");
        }
        if (text.length() > MAX_INPUT_CHARACTERS) {
            throw new IllegalArgumentException("Input exceeds size limit");
        }
    }

    private static String stripAsciiWhitespace(String text) {
        StringBuilder result = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean whitespace = c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
            if (whitespace && result == null) {
                result = new StringBuilder(text.length());
                result.append(text, 0, i);
            } else if (!whitespace && result != null) {
                result.append(c);
            }
        }
        return result == null ? text : result.toString();
    }

    private static byte[] fromLatin1String(String text) {
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < bytes.length; i++) {
            char c = text.charAt(i);
            if (c > 0xFF) {
                throw new IllegalArgumentException("Binary string contains a non-byte character");
            }
            bytes[i] = (byte) c;
        }
        return bytes;
    }

    private static String toLatin1String(byte[] bytes, int length) {
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }
}
