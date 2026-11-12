package com.aresstack.drawio.api;

import java.io.IOException;

/**
 * Supply the bytes of an image or other resource that a diagram references.
 *
 * <p>The renderer never opens URLs, files or network connections itself. A
 * consumer that already holds authorized attachment bytes can hand them in
 * through this port. Implementations must be thread-safe if the same
 * {@link RenderOptions} are used for parallel renders.
 */
@FunctionalInterface
public interface ResourceResolver {

    /**
     * Return the resource bytes, or {@code null} when the reference is unknown
     * or not permitted. Must not perform unauthorized requests.
     */
    byte[] resolve(String reference) throws IOException;
}
