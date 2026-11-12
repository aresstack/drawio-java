package com.aresstack.drawio.graal;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.polyglot.Source;

/**
 * Name and text of one trusted script evaluated by {@link GraalJsRuntime}.
 *
 * <p>Bundled scripts come from the renderer's own {@code /drawio/} classpath
 * area. Inline scripts are meant for small, trusted entry points written by
 * this library; diagram XML must never be passed as script text.
 */
public final class ScriptSource {

    private static final ClasspathScriptLoader LOADER = new ClasspathScriptLoader();
    private static final ConcurrentMap<String, ScriptSource> BUNDLED =
            new ConcurrentHashMap<String, ScriptSource>();

    private final String name;
    private final String text;
    private final boolean bundled;
    private volatile Source polyglotSource;

    private ScriptSource(String name, String text, boolean bundled) {
        this.name = Objects.requireNonNull(name, "name");
        this.text = Objects.requireNonNull(text, "text");
        this.bundled = bundled;
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * Load a script bundled under {@code /drawio/} on the classpath.
     *
     * <p>Bundled scripts are immutable, so each is read once and its parsed
     * form is reused by every context of the shared engine.
     */
    public static ScriptSource bundled(String resourceName) {
        ScriptSource cached = BUNDLED.get(resourceName);
        if (cached != null) {
            return cached;
        }
        ScriptSource loaded = new ScriptSource(resourceName, LOADER.load(resourceName), true);
        ScriptSource previous = BUNDLED.putIfAbsent(resourceName, loaded);
        return previous != null ? previous : loaded;
    }

    /** Wrap a trusted script written by this library. */
    public static ScriptSource inline(String name, String text) {
        return new ScriptSource(name, text, false);
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }

    /**
     * Return the polyglot source. Bundled sources are marked cacheable so the
     * shared engine keeps their parsed code; inline sources are not, so
     * per-request entry scripts never accumulate in the engine.
     */
    Source polyglotSource() {
        Source source = polyglotSource;
        if (source == null) {
            source = Source.newBuilder("js", text, name).cached(bundled).buildLiteral();
            polyglotSource = source;
        }
        return source;
    }
}
