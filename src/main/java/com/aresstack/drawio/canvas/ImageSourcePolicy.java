package com.aresstack.drawio.canvas;

/**
 * Decide which image references a diagram may paint.
 *
 * <p>Turn a reference found in the diagram (a {@code data:} URI or any other
 * string) into a validated {@code data:} URI that {@link DrawioCanvas#image}
 * can paint, or return {@code null} when the image is not available or not
 * permitted. Implementations must never open URLs, files or network
 * connections on their own.
 */
public interface ImageSourcePolicy {

    /** Reject every image. */
    ImageSourcePolicy NONE = new ImageSourcePolicy() {
        @Override
        public String resolve(String reference) {
            return null;
        }
    };

    /**
     * Accept embedded raster {@code data:} URIs (PNG, JPEG, GIF, BMP) within the
     * given limits and reject every other reference.
     */
    static ImageSourcePolicy dataUris(final int maxBytes, final long maxPixels) {
        if (maxBytes < 1 || maxPixels < 1) {
            throw new IllegalArgumentException("Image limits must be positive");
        }
        return new ImageSourcePolicy() {
            @Override
            public String resolve(String reference) {
                if (!DataUriImages.isDataUri(reference)) {
                    return null;
                }
                byte[] bytes = DataUriImages.payload(reference, maxBytes);
                return DataUriImages.toDataUri(bytes, maxBytes, maxPixels);
            }
        };
    }

    /**
     * Return a {@code data:} URI for the reference, or {@code null} if unavailable.
     *
     * @throws IllegalArgumentException if the reference is malformed or exceeds a limit
     */
    String resolve(String reference);
}
