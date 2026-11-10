package com.aresstack.drawio.graal;

/**
 * Supply text resources to a sandboxed JavaScript renderer.
 *
 * <p>Allow only resources explicitly approved by the host application.
 */
@FunctionalInterface
public interface ScriptResourceProvider {

    String loadText(String resourceId);
}
