package com.aresstack.drawio.graal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Load trusted scripts packaged with the renderer.
 */
final class ClasspathScriptLoader {

    private static final String RESOURCE_DIRECTORY = "/drawio/";
    private static final int MAX_RESOURCE_BYTES = 32 * 1024 * 1024;

    String load(String resourceName) {
        validateResourceName(resourceName);

        String path = RESOURCE_DIRECTORY + resourceName;
        try (InputStream stream = ClasspathScriptLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Unknown bundled script: " + resourceName);
            }
            return readBounded(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled script: " + resourceName, exception);
        }
    }

    private void validateResourceName(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()
                || resourceName.startsWith("/") || resourceName.contains("..")
                || !resourceName.matches("[A-Za-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid bundled script name");
        }
    }

    private String readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;

        while ((count = stream.read(buffer)) != -1) {
            total += count;
            if (total > MAX_RESOURCE_BYTES) {
                throw new IllegalArgumentException("Bundled script exceeds size limit");
            }
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
