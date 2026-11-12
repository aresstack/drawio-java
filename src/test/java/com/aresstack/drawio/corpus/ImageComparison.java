package com.aresstack.drawio.corpus;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Font- and antialiasing-tolerant comparison of a rendered diagram with an
 * official draw.io viewer reference (#13).
 *
 * <p>Both images are reduced to "ink" masks (pixels visibly different from
 * the white background), cropped to their ink bounding boxes and aligned at
 * the top-left corner, so padding and viewer origin offsets do not matter.
 * The score is the F1 measure of ink coverage with a spatial tolerance:
 * a rendered ink pixel counts as correct when reference ink lies within
 * {@code tolerance} pixels, and vice versa. Missing shapes, connectors or
 * labels lower recall; spurious drawing lowers precision. Pure font
 * differences only move ink by a pixel or two and are absorbed by the
 * tolerance. Bounding-box sizes are compared separately.</p>
 */
final class ImageComparison {

    static final int DEFAULT_TOLERANCE = 2;
    private static final int INK_THRESHOLD = 40;

    final Rectangle actualBounds;
    final Rectangle referenceBounds;
    final double precision;
    final double recall;
    final double score;
    final BufferedImage diff;

    private ImageComparison(Rectangle actualBounds, Rectangle referenceBounds, double precision, double recall,
                            BufferedImage diff) {
        this.actualBounds = actualBounds;
        this.referenceBounds = referenceBounds;
        this.precision = precision;
        this.recall = recall;
        this.score = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        this.diff = diff;
    }

    static ImageComparison compare(BufferedImage actual, BufferedImage reference, int tolerance) {
        boolean[][] actualInk = ink(actual);
        boolean[][] referenceInk = ink(reference);
        Rectangle a = bounds(actualInk);
        Rectangle r = bounds(referenceInk);
        if (a == null && r == null) {
            return new ImageComparison(null, null, 1, 1, null); // both blank, e.g. an empty page
        }
        if (a == null || r == null) {
            return new ImageComparison(a, r, 0, 0, null);
        }
        int width = Math.max(a.width, r.width);
        int height = Math.max(a.height, r.height);
        boolean[][] am = crop(actualInk, a, width, height);
        boolean[][] rm = crop(referenceInk, r, width, height);
        boolean[][] rNear = dilate(rm, tolerance);
        boolean[][] aNear = dilate(am, tolerance);

        long actualCount = 0;
        long actualHit = 0;
        long referenceCount = 0;
        long referenceHit = 0;
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = 0xffffff;
                if (rm[y][x]) {
                    referenceCount++;
                    if (aNear[y][x]) {
                        referenceHit++;
                        rgb = 0x9a9a9a;
                    } else {
                        rgb = 0xd62728; // missing: in reference, not rendered
                    }
                }
                if (am[y][x]) {
                    actualCount++;
                    if (rNear[y][x]) {
                        actualHit++;
                        if (rgb == 0xffffff) {
                            rgb = 0x9a9a9a;
                        }
                    } else {
                        rgb = 0x1f77b4; // extra: rendered, not in reference
                    }
                }
                diff.setRGB(x, y, rgb);
            }
        }
        double precision = actualCount == 0 ? 0 : (double) actualHit / actualCount;
        double recall = referenceCount == 0 ? 0 : (double) referenceHit / referenceCount;
        return new ImageComparison(a, r, precision, recall, diff);
    }

    /** True when both ink bounding boxes agree within {@code max(slack, 3 %)} in width and height. */
    boolean boundsAgree(int slack) {
        if (actualBounds == null && referenceBounds == null) {
            return true;
        }
        if (actualBounds == null || referenceBounds == null) {
            return false;
        }
        int dw = Math.abs(actualBounds.width - referenceBounds.width);
        int dh = Math.abs(actualBounds.height - referenceBounds.height);
        return dw <= Math.max(slack, referenceBounds.width * 0.03)
                && dh <= Math.max(slack, referenceBounds.height * 0.03);
    }

    String summary() {
        return String.format(Locale.ROOT, "score %.3f (precision %.3f, recall %.3f), ink bounds %s vs reference %s",
                score, precision, recall, size(actualBounds), size(referenceBounds));
    }

    private static String size(Rectangle r) {
        return r == null ? "blank" : r.width + "x" + r.height;
    }

    static boolean[][] ink(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = (argb >> 16) & 0xff;
                int green = (argb >> 8) & 0xff;
                int blue = argb & 0xff;
                // Composite on white, then measure distance from white.
                int r = 255 - ((255 - red) * alpha) / 255;
                int g = 255 - ((255 - green) * alpha) / 255;
                int b = 255 - ((255 - blue) * alpha) / 255;
                int distance = Math.max(255 - r, Math.max(255 - g, 255 - b));
                mask[y][x] = distance > INK_THRESHOLD;
            }
        }
        return mask;
    }

    static Rectangle bounds(boolean[][] mask) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < mask[y].length; x++) {
                if (mask[y][x]) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static boolean[][] crop(boolean[][] mask, Rectangle bounds, int width, int height) {
        boolean[][] out = new boolean[height][width];
        for (int y = 0; y < bounds.height; y++) {
            System.arraycopy(mask[bounds.y + y], bounds.x, out[y], 0, bounds.width);
        }
        return out;
    }

    private static boolean[][] dilate(boolean[][] mask, int radius) {
        int height = mask.length;
        int width = height == 0 ? 0 : mask[0].length;
        boolean[][] horizontal = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            int last = Integer.MIN_VALUE / 2;
            for (int x = 0; x < width; x++) {
                if (mask[y][x]) {
                    last = x;
                }
                horizontal[y][x] = x - last <= radius;
            }
            last = Integer.MAX_VALUE / 2;
            for (int x = width - 1; x >= 0; x--) {
                if (mask[y][x]) {
                    last = x;
                }
                horizontal[y][x] |= last - x <= radius;
            }
        }
        boolean[][] out = new boolean[height][width];
        for (int x = 0; x < width; x++) {
            int last = Integer.MIN_VALUE / 2;
            for (int y = 0; y < height; y++) {
                if (horizontal[y][x]) {
                    last = y;
                }
                out[y][x] = y - last <= radius;
            }
            last = Integer.MAX_VALUE / 2;
            for (int y = height - 1; y >= 0; y--) {
                if (horizontal[y][x]) {
                    last = y;
                }
                out[y][x] |= last - y <= radius;
            }
        }
        return out;
    }
}
