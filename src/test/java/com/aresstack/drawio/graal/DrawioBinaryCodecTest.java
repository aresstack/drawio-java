package com.aresstack.drawio.graal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.Deflater;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawioBinaryCodecTest {

    @Test
    void decodesBase64LikeAtob() {
        DrawioBinaryCodec codec = new DrawioBinaryCodec();

        assertEquals("hi", codec.decodeBase64("aGk="));
        assertEquals("hi", codec.decodeBase64("aG k\n"));
        assertEquals("ÿ\u0000", codec.decodeBase64("/wA="));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBase64("a*Gk"));
    }

    @Test
    void inflatesRawDeflateStreams() {
        byte[] original = repeated("stencil ", 1000);
        DrawioBinaryCodec codec = new DrawioBinaryCodec();

        String inflated = codec.inflateRaw(latin1(deflateRaw(original)));

        assertArrayEquals(original, inflated.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(original.length, codec.getTotalInflatedBytes());
    }

    @Test
    void rejectsTruncatedOrCorruptStreams() {
        DrawioBinaryCodec codec = new DrawioBinaryCodec();
        byte[] compressed = deflateRaw(repeated("abc", 500));

        assertThrows(IllegalArgumentException.class, () -> codec.inflateRaw(
                latin1(Arrays.copyOf(compressed, compressed.length / 2))));
        assertThrows(IllegalArgumentException.class,
                () -> codec.inflateRaw("ÿÿÿÿ"));
        assertThrows(IllegalArgumentException.class, () -> codec.inflateRaw("Ā"));
    }

    @Test
    void rejectsStoredBlockMissingItsLastByte() {
        DrawioBinaryCodec codec = new DrawioBinaryCodec();
        Deflater deflater = new Deflater(Deflater.NO_COMPRESSION, true);
        byte[] stored;
        try {
            deflater.setInput("hello".getBytes(StandardCharsets.ISO_8859_1));
            deflater.finish();
            byte[] buffer = new byte[64];
            int count = deflater.deflate(buffer);
            stored = Arrays.copyOf(buffer, count);
        } finally {
            deflater.end();
        }

        assertEquals("hello", codec.inflateRaw(latin1(stored)));
        assertThrows(IllegalArgumentException.class,
                () -> codec.inflateRaw(latin1(Arrays.copyOf(stored, stored.length - 1))));
    }

    @Test
    void enforcesPerCallAndCumulativeOutputBudgets() {
        String bomb = latin1(deflateRaw(new byte[1024 * 1024]));
        DrawioBinaryCodec small = new DrawioBinaryCodec(4096, 1 << 20);

        IllegalStateException perCall = assertThrows(IllegalStateException.class,
                () -> small.inflateRaw(bomb));
        assertTrue(perCall.getMessage().contains("4096"));
        assertEquals(0, small.getTotalInflatedBytes());

        String kilobyte = latin1(deflateRaw(new byte[1000]));
        DrawioBinaryCodec cumulative = new DrawioBinaryCodec(1000, 2500);
        cumulative.inflateRaw(kilobyte);
        cumulative.inflateRaw(kilobyte);
        assertThrows(IllegalStateException.class, () -> cumulative.inflateRaw(kilobyte));
        assertEquals(2000, cumulative.getTotalInflatedBytes());
    }

    @Test
    void decodesUtf8Strictly() {
        DrawioBinaryCodec codec = new DrawioBinaryCodec();

        assertEquals("€", codec.decodeUtf8("â\u0082¬"));
        assertEquals("a", codec.decodeUtf8("ï»¿a"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUtf8("â\u0082"));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeUtf8("À¯"));
    }

    @Test
    void validatesBudgetArguments() {
        assertThrows(IllegalArgumentException.class, () -> new DrawioBinaryCodec(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new DrawioBinaryCodec(10, 0));
    }

    @Test
    void roundTripsUpstreamStyleBase64Payload() {
        byte[] original = "<shapes name=\"x\"/>".getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(deflateRaw(original));
        DrawioBinaryCodec codec = new DrawioBinaryCodec();

        String xml = codec.decodeUtf8(codec.inflateRaw(codec.decodeBase64(base64)));

        assertEquals("<shapes name=\"x\"/>", xml);
    }

    private static byte[] repeated(String text, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(text);
        }
        return builder.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] deflateRaw(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
