package com.aresstack.drawio.graal;

import java.util.Objects;

import org.graalvm.polyglot.HostAccess;

/**
 * Expose explicitly approved capabilities to guest JavaScript.
 *
 * <p>Keep Java2D objects and filesystem access outside the guest boundary.
 */
public final class DrawioHost {

    private final ScriptResourceProvider resources;
    private final DrawioCanvasBridge canvas;

    public DrawioHost(ScriptResourceProvider resources) {
        this(resources, null);
    }

    public DrawioHost(ScriptResourceProvider resources, DrawioCanvasBridge canvas) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.canvas = canvas;
    }

    @HostAccess.Export
    public DrawioCanvasBridge canvas() {
        if (canvas == null) {
            throw new IllegalStateException("No drawing canvas is configured for this script");
        }
        return canvas;
    }

    @HostAccess.Export
    public String loadResource(String resourceId) {
        return resources.loadText(Objects.requireNonNull(resourceId, "resourceId"));
    }
}
