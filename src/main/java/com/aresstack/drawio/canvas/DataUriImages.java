package com.aresstack.drawio.canvas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Decode raster images embedded as {@code data:} URIs under strict limits.
 *
 * <p>Accept only base64 PNG, JPEG, GIF and BMP payloads. Check the encoded
 * size before decoding and the pixel dimensions before allocating the raster.
 * Use an in-memory ImageIO stream, so no temporary files are written.
 * SVG images are rejected: rendering them would require an SVG engine with
 * its own script and reference policy.
 */
public final class DataUriImages {

    /** Default maximum decoded payload size (4 MiB). */
    public static final int DEFAULT_MAX_BYTES = 4 * 1024 * 1024;

    /** Default maximum number of pixels (16 Mpx). */
    public static final long DEFAULT_MAX_PIXELS = 16L * 1024 * 1024;

    private DataUriImages() {
    }

    /** Return {@code true} if the reference uses the {@code data:} scheme. */
    public static boolean isDataUri(String reference) {
        return reference != null && reference.regionMatches(true, 0, "data:", 0, 5);
    }

    /** Return {@code true} if the reference is a {@code data:image/svg+xml} URI. */
    public static boolean isSvgDataUri(String reference) {
        return isDataUri(reference)
                && reference.substring(5).trim().toLowerCase(Locale.ROOT).startsWith("image/svg");
    }

    /**
     * Build a base64 {@code data:} URI for raster bytes after validating them.
     *
     * @throws IllegalArgumentException if the bytes are not a supported image within limits
     */
    public static String toDataUri(byte[] bytes, int maxBytes, long maxPixels) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Image data is empty");
        }
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException("Image exceeds the byte limit");
        }
        String format = readerFormat(bytes, maxPixels);
        return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Decode a {@code data:} URI into an image.
     *
     * @throws IllegalArgumentException if the URI is malformed, unsupported or exceeds a limit
     */
    public static BufferedImage decode(String dataUri, int maxBytes, long maxPixels) {
        byte[] bytes = payload(dataUri, maxBytes);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = supportedReader(input);
            try {
                reader.setInput(input, true, true);
                checkPixels(reader, maxPixels);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("Image could not be decoded");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image could not be decoded", exception);
        }
    }

    /**
     * Return the decoded payload bytes of a base64 image {@code data:} URI.
     */
    public static byte[] payload(String dataUri, int maxBytes) {
        if (!isDataUri(dataUri)) {
            throw new IllegalArgumentException("Only data: URIs can be painted");
        }
        int comma = dataUri.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Malformed data URI");
        }
        String header = dataUri.substring(5, comma).trim().toLowerCase(Locale.ROOT);
        String[] parts = header.split(";");
        String mime = parts.length == 0 ? "" : parts[0].trim();
        boolean base64 = false;
        for (int index = 1; index < parts.length; index++) {
            if ("base64".equals(parts[index].trim())) {
                base64 = true;
            }
        }
        if (mime.startsWith("image/svg")) {
            throw new IllegalArgumentException("SVG images are not supported");
        }
        if (!isSupportedMime(mime)) {
            throw new IllegalArgumentException("Unsupported image type");
        }
        if (!base64) {
            throw new IllegalArgumentException("Only base64 image data URIs are supported");
        }

        int length = dataUri.length() - comma - 1;
        if ((long) length * 3 / 4 > maxBytes + 3L) {
            throw new IllegalArgumentException("Image exceeds the byte limit");
        }
        StringBuilder encoded = new StringBuilder(length);
        for (int index = comma + 1; index < dataUri.length(); index++) {
            char current = dataUri.charAt(index);
            if (!Character.isWhitespace(current)) {
                encoded.append(current);
            }
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed base64 image data", exception);
        }
        if (bytes.length == 0 || bytes.length > maxBytes) {
            throw new IllegalArgumentException("Image exceeds the byte limit or is empty");
        }
        return bytes;
    }

    private static boolean isSupportedMime(String mime) {
        return "image/png".equals(mime) || "image/jpeg".equals(mime)
                || "image/jpg".equals(mime) || "image/gif".equals(mime)
                || "image/bmp".equals(mime) || "image/x-ms-bmp".equals(mime);
    }

    private static String readerFormat(byte[] bytes, long maxPixels) {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = supportedReader(input);
            try {
                reader.setInput(input, true, true);
                checkPixels(reader, maxPixels);
                return reader.getFormatName().toLowerCase(Locale.ROOT);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Image could not be read", exception);
        }
    }

    private static ImageReader supportedReader(ImageInputStream input) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            String format;
            try {
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
            } catch (IOException exception) {
                reader.dispose();
                continue;
            }
            if ("png".equals(format) || "jpeg".equals(format) || "jpg".equals(format)
                    || "gif".equals(format) || "bmp".equals(format)) {
                return reader;
            }
            reader.dispose();
        }
        throw new IllegalArgumentException("Unsupported or unrecognized image format");
    }

    private static void checkPixels(ImageReader reader, long maxPixels) throws IOException {
        long width = reader.getWidth(0);
        long height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || width * height > maxPixels) {
            throw new IllegalArgumentException("Image exceeds the pixel limit");
        }
    }
}
