package com.aresstack.drawio.render;

import java.io.IOException;
import java.util.Objects;

import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.api.ResourceResolver;
import com.aresstack.drawio.canvas.DataUriImages;
import com.aresstack.drawio.canvas.ImageSourcePolicy;

/**
 * Apply a public {@link ResourcePolicy} to image references found in a diagram.
 *
 * <p>Embedded {@code data:} URIs are accepted only if the policy allows them;
 * every other reference is handed to the consumer's {@link ResourceResolver},
 * if one is configured. The bytes are validated (type, byte and pixel limits)
 * and returned as a {@code data:} URI for the canvas. Nothing is fetched by
 * the renderer itself.
 */
public final class PolicyImageSources implements ImageSourcePolicy {

    private final ResourcePolicy policy;

    public PolicyImageSources(ResourcePolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public String resolve(String reference) {
        if (reference == null) {
            return null;
        }
        int maxBytes = policy.getMaxResourceBytes();
        long maxPixels = policy.getMaxImagePixels();
        if (DataUriImages.isDataUri(reference)) {
            if (!policy.isDataUrisAllowed()) {
                return null;
            }
            return DataUriImages.toDataUri(DataUriImages.payload(reference, maxBytes),
                    maxBytes, maxPixels);
        }
        ResourceResolver resolver = policy.getResolver();
        if (resolver == null) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = resolver.resolve(reference);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Resource resolver failed", exception);
        }
        return bytes == null ? null : DataUriImages.toDataUri(bytes, maxBytes, maxPixels);
    }
}
