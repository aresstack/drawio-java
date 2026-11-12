package com.aresstack.drawio.api;

import java.util.Objects;

/**
 * Control which external resources (mainly images) a diagram may use.
 *
 * <p>The default policy {@link #none()} allows no resources at all; an image
 * reference then produces {@link RenderErrorCode#MISSING_RESOURCE} or a
 * warning, depending on the {@link UnsupportedFeaturePolicy}. The renderer
 * itself never fetches URLs.
 */
public final class ResourcePolicy {

    public static final int DEFAULT_MAX_RESOURCE_BYTES = 4 * 1024 * 1024;
    public static final long DEFAULT_MAX_IMAGE_PIXELS = 16L * 1024 * 1024;

    private static final ResourcePolicy NONE =
            new ResourcePolicy(false, null, DEFAULT_MAX_RESOURCE_BYTES, DEFAULT_MAX_IMAGE_PIXELS);

    private final boolean dataUrisAllowed;
    private final ResourceResolver resolver;
    private final int maxResourceBytes;
    private final long maxImagePixels;

    private ResourcePolicy(boolean dataUrisAllowed, ResourceResolver resolver,
                           int maxResourceBytes, long maxImagePixels) {
        if (maxResourceBytes < 1 || maxImagePixels < 1) {
            throw new IllegalArgumentException("resource limits must be positive");
        }
        this.dataUrisAllowed = dataUrisAllowed;
        this.resolver = resolver;
        this.maxResourceBytes = maxResourceBytes;
        this.maxImagePixels = maxImagePixels;
    }

    /** Allow no external resources. */
    public static ResourcePolicy none() {
        return NONE;
    }

    /** Allow images embedded in the diagram as {@code data:} URIs only. */
    public static ResourcePolicy dataUrisOnly() {
        return new ResourcePolicy(true, null, DEFAULT_MAX_RESOURCE_BYTES, DEFAULT_MAX_IMAGE_PIXELS);
    }

    /** Allow {@code data:} URIs and references the given resolver can supply. */
    public static ResourcePolicy withResolver(ResourceResolver resolver) {
        return new ResourcePolicy(true, Objects.requireNonNull(resolver, "resolver"),
                DEFAULT_MAX_RESOURCE_BYTES, DEFAULT_MAX_IMAGE_PIXELS);
    }

    public ResourcePolicy withLimits(int maxResourceBytes, long maxImagePixels) {
        return new ResourcePolicy(dataUrisAllowed, resolver, maxResourceBytes, maxImagePixels);
    }

    public boolean isDataUrisAllowed() {
        return dataUrisAllowed;
    }

    /** Return the consumer resolver, or {@code null} when none is configured. */
    public ResourceResolver getResolver() {
        return resolver;
    }

    public int getMaxResourceBytes() {
        return maxResourceBytes;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }
}
