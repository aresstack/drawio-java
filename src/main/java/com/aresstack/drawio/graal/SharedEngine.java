package com.aresstack.drawio.graal;

import java.io.IOException;
import java.io.OutputStream;

import org.graalvm.polyglot.Engine;

/**
 * Hold the one polyglot {@link Engine} that all restricted contexts share.
 *
 * <p>Sharing an engine lets GraalJS reuse the parsed form of the pinned
 * draw.io bundles across contexts, so each render request no longer parses
 * hundreds of kilobytes of JavaScript again. Contexts stay isolated: every
 * request still gets its own {@code Context} with its own global scope and
 * heap objects, and a context is never used by two threads. An engine is
 * thread-safe by design. It is never closed; it holds no threads on a stock
 * JDK and only caches code of the trusted, bundled sources.
 */
final class SharedEngine {

    static final OutputStream DISCARD_OUTPUT = new OutputStream() {
        @Override
        public void write(int value) throws IOException {
            // Discard guest output to avoid exposing host streams.
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            // Discard guest output to avoid exposing host streams.
        }
    };

    private SharedEngine() {
    }

    static Engine get() {
        return Holder.ENGINE;
    }

    static Engine createUnshared() {
        return Engine.newBuilder()
                .out(DISCARD_OUTPUT)
                .err(DISCARD_OUTPUT)
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    private static final class Holder {
        private static final Engine ENGINE = createUnshared();
    }
}
