package com.aresstack.drawio.graal;

import java.util.Objects;

/**
 * Name and text of one trusted script evaluated by {@link GraalJsRuntime}.
 *
 * <p>Bundled scripts come from the renderer's own {@code /drawio/} classpath
 * area. Inline scripts are meant for small, trusted entry points written by
 * this library; diagram XML must never be passed as script text.
 */
public final class ScriptSource {

    private static final ClasspathScriptLoader LOADER = new ClasspathScriptLoader();

    private final String name;
    private final String text;

    private ScriptSource(String name, String text) {
        this.name = Objects.requireNonNull(name, "name");
        this.text = Objects.requireNonNull(text, "text");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** Load a script bundled under {@code /drawio/} on the classpath. */
    public static ScriptSource bundled(String resourceName) {
        return new ScriptSource(resourceName, LOADER.load(resourceName));
    }

    /** Wrap a trusted script written by this library. */
    public static ScriptSource inline(String name, String text) {
        return new ScriptSource(name, text);
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }
}
