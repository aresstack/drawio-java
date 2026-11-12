package com.aresstack.drawio.api;

/**
 * Report which library build and which pinned draw.io sources render diagrams.
 */
public final class RendererVersion {

    /** The draw.io upstream commit all bundled JavaScript is pinned to (see UPSTREAM.md). */
    public static final String DRAWIO_UPSTREAM_COMMIT = "c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3";

    /** The library version from the JAR manifest, or {@code development} in a source build. */
    public static final String LIBRARY_VERSION = libraryVersion();

    private RendererVersion() {
    }

    private static String libraryVersion() {
        Package pkg = RendererVersion.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.trim().isEmpty() ? "development" : version;
    }
}
