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

    public DrawioHost(ScriptResourceProvider resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    @HostAccess.Export
    public String loadResource(String resourceId) {
        return resources.loadText(Objects.requireNonNull(resourceId, "resourceId"));
    }
}
