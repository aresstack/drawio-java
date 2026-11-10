# drawio-java

Headless Draw.io diagram rendering for Java. The planned renderer executes the
original Draw.io shape and layout logic with GraalJS and delegates drawing to
Java2D. No browser or Node.js runtime should be required by consumers.

> **Status: bootstrap only.** This repository does not render diagrams yet.
> The architecture and implementation milestones are tracked in
> [Epic #1](https://github.com/aresstack/drawio-java/issues/1).

## Scope

- Keep the public Java API independent of GraalJS, Swing/JavaFX, Confluence and network access.
- Reuse verified ideas and small components from
  [mermaid-java](https://github.com/aresstack/mermaid-java) and
  [playwright4j](https://github.com/aresstack/playwright4j) without importing either as a runtime dependency.
- Target Java 8 for the library API and runtime compatibility.
- Render with the original Draw.io JavaScript shape/stencil logic, eventually via
  a JavaScript canvas adapter and a restricted Java host bridge.
- Provide PNG/BufferedImage and SVG outputs through the same Java2D drawing model.

See [REUSE.md](REUSE.md) for the detailed component inventory and decisions.

## Development

JDK 8 and a Gradle 8.12 wrapper are used. Java 8 toolchains must be available.

On Windows:

```powershell
.\gradlew.bat clean test jar
```

On Linux/macOS:

```bash ./gradlew clean test jar
```

## GraalJS runtime (Issue #3)

The library now contains a small Java 8-compatible GraalJS execution adapter,
pinned to GraalVM JavaScript **21.2.0**, following patterns from Mermaid Java
and Playwright4J.

```java
DrawioHost host = new DrawioHost(resourceId -> {
    if ("greeting".equals(resourceId)) {
        return "hello";
    }
    throw new IllegalArgumentException("Unknown resource");
});

try (GraalJsRuntime runtime = new GraalJsRuntime(Duration.ofSeconds(10))) {
    ScriptExecutionResult result = runtime.execute(
            "greeting.js", "drawioHost.loadResource('greeting')", host);
    if (!result.isSuccessful()) {
        throw new IllegalStateException(result.getErrorMessage());
    }
    System.out.println(result.getOutput());
}
```

The runtime creates a separate JavaScript context for every evaluation and
only exposes annotated host bridge methods. Arbitrary host-class lookup,
native access, JavaScript-managed threads and direct guest file I/O are disabled.
Bundled scripts are read from the `/drawio/` classpath area, not fetched online.
An execution timeout forcibly closes the active context and renders that
runtime unusable; create another runtime to retry.

**Security boundary:** This is a restricted embedding environment, **not**
a hardened sandbox guaranteeing resource isolation from malicious JavaScript.
Use trusted, pinned Draw.io JavaScript bundles; treat diagram XML as untrusted
*data*, not JavaScript source. Complete memory/resource limits, security audits
and a stable consumer-facing API remain in #12/#15. JavaScript 21.2.0 is an
older version that must be reassessed for support and vulnerabilities before
releasing or using untrusted scripts. The actual Draw.io bundle and renderer
remain future work in #4–#7.

## Java2D canvas (Issue #5)

The `DrawioCanvas` interface expresses drawing operations independently
of JavaScript and Swing. `Java2DCanvas` implements them using a caller-supplied
`Graphics2D`, including `Path2D`, ellipses, rounded rectangles, transforms,
horizontal/vertical mirroring, nested drawing-state stacks, clipping,
separate fill/stroke opacity and dashed strokes.

Pass an ordinary `BufferedImage.createGraphics()` context for raster output.
In #11, the same drawing implementation will be tested against Batik's
`SVGGraphics2D` for vector output.

```java
BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
Graphics2D graphics = image.createGraphics();
try (DrawioCanvas canvas = new Java2DCanvas(graphics)) {
    canvas.setFillColor("#ff0000");
    canvas.rect(10, 10, 40, 30);
    canvas.fill();
} finally {
    graphics.dispose();
}
```

Call `closePath()` to close a path; `close()` releases the canvas's child
graphics resources. The future JavaScript adapter maps upstream Draw.io
`close()` to `closePath()`. Text and image methods currently throw
`UnsupportedOperationException` deliberately; full text, gradients, shadows,
embedded resources and complete Draw.io shape coverage belong to #9/#10.
Invalid colors and geometry are rejected rather than silently misrendered.

## Roadmap

1. [#2 Bootstrap and reuse inventory](https://github.com/aresstack/drawio-java/issues/2)
2. [#3 Restricted GraalJS runtime](https://github.com/aresstack/drawio-java/issues/3)
3. [#4 Pinned Draw.io JavaScript bundle](https://github.com/aresstack/drawio-java/issues/4)
4. [#5 Java2D canvas](https://github.com/aresstack/drawio-java/issues/5)
5. [#6 JS canvas bridge](https://github.com/aresstack/drawio-java/issues/6)
6. [#7 Original BPMN `gateway2` end-to-end proof](https://github.com/aresstack/drawio-java/issues/7)

Follow the [epic checklist](https://github.com/aresstack/drawio-java/issues/1)
for subsequent full-diagram rendering, tests, release and integrations.
