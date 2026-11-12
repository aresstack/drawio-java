# drawio-java

Headless draw.io diagram rendering for Java 8+. The renderer runs the
original, pinned draw.io JavaScript (mxGraph model, view, codec and shapes,
including BPMN) on GraalJS and paints it with Java2D into `BufferedImage`,
PNG or SVG. Consumers need no browser, no Node.js and no network access.

> **Status: pre-release.** Diagrams render end to end through the original
> draw.io code, and a reference corpus compares them with the official draw.io
> viewer (BPMN `gateway2` approval flow: score 0.998). Before the first release,
> one owner decision is open: the draw.io stencil license caveat
> ([docs/LICENSE-REVIEW.md](docs/LICENSE-REVIEW.md)).
>
> - What works and what does not: [docs/SUPPORT-MATRIX.md](docs/SUPPORT-MATRIX.md)
> - Security model and dependency audit: [docs/SECURITY.md](docs/SECURITY.md)
> - CI, reproducible builds and the release process: [docs/RELEASE.md](docs/RELEASE.md)
> - Reference and regression corpus: [src/test/resources/drawio/corpus](src/test/resources/drawio/corpus/README.md)
> - Milestones: [Epic #1](https://github.com/aresstack/drawio-java/issues/1)

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

## Using the renderer (Issue #12)

```java
try (DrawioRenderer renderer = DrawioRenderer.create()) {
    RenderResult<byte[]> png = renderer.renderPng(xml, RenderOptions.builder()
            .page(PageSelector.name("Process")).scale(2.0).build());
    RenderResult<String> svg = renderer.renderSvg(xml);
} catch (RenderException e) {
    // e.getCode(): INVALID_XML, UNSUPPORTED_STENCIL, MISSING_RESOURCE, TIMEOUT, LIMIT_EXCEEDED, ...
}
```

`DrawioRenderer` is thread-safe and meant to be long-lived: bounded worker
threads, one isolated JavaScript context per request, timeouts that
force-close the context, structured warnings and stable error codes, no
network or file access. Unsupported shapes fail by default instead of being
approximated. Until the graph pipeline (#9) is wired in, requests fail with
`RENDER_FAILED`. Details: [docs/PUBLIC-API.md](docs/PUBLIC-API.md),
performance and resource control: [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Development

JDK 8 and a Gradle 8.12 wrapper are used. A Java 8 toolchain must be
installed; Gradle itself can run on JDK 17.

On Windows:

```powershell
.\gradlew.bat clean build
```

On Linux/macOS:

```bash
./gradlew clean build
```

`build` runs all tests, including the reference corpus, the Java 8 bytecode
gate (`verifyJava8Bytecode`) and an offline smoke test against the packaged
jar (`consumerSmokeTest`). Pass `-PtestJavaVersion=11|17|21` to run the tests
on another installed JDK. The corpus report with rendered images and diffs is
written to `build/reports/corpus/render.html`. CI runs all of this on JDK 8,
11, 17 and 21 and on Linux and Windows; see [docs/RELEASE.md](docs/RELEASE.md).

## GraalJS runtime (Issue #3)

The library now contains a small Java 8-compatible GraalJS execution adapter,
pinned to GraalVM JavaScript **21.3.20.0.3** (newest Java 8-compatible
line, see [docs/GRAALJS-VERSION.md](docs/GRAALJS-VERSION.md)), following patterns from Mermaid Java
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
remain in #15. GraalJS 21.3 is the newest Java 8-compatible line but still
old; a full CVE review is part of the release audit (#15). The pinned original Draw.io scripts
are now included for the registration proof; the full renderer is still work in #6–#10.

## Pinned Draw.io source bundle (Issue #4)

The repository includes the original `mxAbstractCanvas2D.js` and
`mxBpmnShape2.js` from a pinned upstream Git commit, bundled locally
without a browser or runtime network connection. A GraalJS smoke test
runs the **original** `mxgraph.bpmn.gateway2` painting method against
a test-only command recorder. The full upstream Viewer/DOM, XML stencils
and fonts are **not** bundled.

Regenerate or verify the bundle offline from checked-in sources:

```bash
node js-bundle/build.mjs
node js-bundle/build.mjs --verify
```

See [UPSTREAM.md](UPSTREAM.md) for commit hashes, licensing conditions,
unsupported assets and the exact upstream JavaScript entry points.

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

## Draw.io JavaScript-to-Java2D canvas bridge (Issue #6)

The original Draw.io JavaScript now sends shape commands to Java2D through
`DrawioJavaCanvas` in `drawio-canvas-adapter.js`. This adapter extends
the original `mxAbstractCanvas2D` and forwards explicitly supported
operations through the `DrawioHost.canvas()` method to the restricted
`DrawioCanvasBridge` Java object. The JavaScript guest cannot obtain the
underlying `Graphics2D`, Java classes, the filesystem or network access.

`DrawioJavaCanvasBridgeTest` invokes the **original**
`mxgraph.bpmn.gateway2` shape method with the `exclusive`, `parallel`
and `complex` styles, then verifies actual Java2D pixels. It also checks
curves, save/restore, and blocked reflection. No shape is reimplemented in
Java, and neither Chromium nor Node.js is needed at runtime.

**Current limitation:** the tests bootstrap the original shape registration
using a small, test-only mxGraph scaffold. The production draw.io graph
model and layout engine are not initialized yet. This is a successful
canvas-interoperability proof, **not yet a general XML diagram renderer**.
Gradient fills, text, images, shadows and arcs fail explicitly until
implemented in #9/#10. Full diagram rendering and upstream visual parity
are tracked in #7–#13.

## Verified BPMN reference rendering (Issue #7)

The original `mxgraph.bpmn.gateway2` JavaScript now passes a visual
comparison against the official Draw.io viewer at the same pinned
upstream version. A synthetic three-gateway `mxGraphModel` fixture
and its independently generated viewer SVG are stored under
`src/test/resources/drawio/golden/`. A test-only Batik rasterization
compares stable pixels against our Java2D output.

See [GO-NO-GO.md](GO-NO-GO.md) for the **GO** decision for this
shape-canvas architecture, the explicit production limitations and
remaining prerequisites for full diagram rendering.

## Secure multi-page document reader (Issue #8)

`DrawioDocumentParser` can now read direct `mxGraphModel` XML and
`mxfile` documents with multiple pages, including compressed
Base64/raw-Deflate/URL-encoded page contents. Each immutable
`DiagramPage` preserves its id, name, index and graph XML.
Pages can be selected by name or index without silently losing
additional pages.

The parser is independent of GraalJS, Java2D, UI and Confluence.
It disables XML external entities and DTDs, validates the XML
structure before DOM materialization, and caps compressed output
and page/document complexity. Unsupported input produces a typed
`DiagramFormatException`.

See [docs/XML-PARSING.md](docs/XML-PARSING.md) for supported formats,
limits, API and security requirements. A general graph renderer
still requires #9–#11.

## Original headless mxGraph model (Issue #9, first slice)

The pinned `drawio-model.js` bundle contains twelve unmodified original
mxGraph model, geometry, event and transaction classes. Headless GraalJS
tests create an actual `mxGraphModel`, insert vertices and edges with
real parent/terminal relationships, receive model events, and render an
original BPMN gateway using geometry retrieved from that model.

This is a **model-core integration proof**, not yet a complete
`mxGraphView` layout/renderer. XML-to-model decoding, edge routing,
DOM-dependent validation and text metrics remain work for #9.
See [docs/HEADLESS-MXGRAPH.md](docs/HEADLESS-MXGRAPH.md).

## Headless font measurement (Issue #9, separate slice)

`TextMetrics` and `AwtTextMetrics` provide offscreen Java2D font
measurements (width, ascent, descent and multiline height), using a
bounded immutable `FontDescriptor` and `TextBounds`. The optional
`DrawioHost.measureText` capability exposes a plain value to GraalJS,
not a Java AWT object. Existing host callers remain source-compatible.

The implementation follows the font-measurement approach already
used by `mermaid-java`. It is **not yet complete CSS/HTML label
layout**; missing typography and SVG layout remain part of #9/#10.
See [docs/TEXT-METRICS.md](docs/TEXT-METRICS.md).

## PNG, BufferedImage and SVG output (Issue #11)

`SceneRenderer` in `com.aresstack.drawio.render` writes a `DrawioScene` as
`BufferedImage`, PNG (with DPI metadata) or a standalone SVG document. The
original JavaScript runs once into a `CanvasRecorder`; the recorded commands
are replayed through the same `Java2DCanvas` into a raster `Graphics2D` and
into Batik's `SVGGraphics2D`, so there is no second SVG drawing path.
Output bounds include measured stroke, arrow and shadow extents by default.
Scale, fit width, padding, transparent or solid background and size limits
are configurable. See [docs/OUTPUT.md](docs/OUTPUT.md).

## Roadmap

1. [#2 Bootstrap and reuse inventory](https://github.com/aresstack/drawio-java/issues/2)
2. [#3 Restricted GraalJS runtime](https://github.com/aresstack/drawio-java/issues/3)
3. [#4 Pinned Draw.io JavaScript bundle](https://github.com/aresstack/drawio-java/issues/4)
4. [#5 Java2D canvas](https://github.com/aresstack/drawio-java/issues/5)
5. [#6 JS canvas bridge](https://github.com/aresstack/drawio-java/issues/6)
6. [#7 Original BPMN `gateway2` end-to-end proof](https://github.com/aresstack/drawio-java/issues/7)

Follow the [epic checklist](https://github.com/aresstack/drawio-java/issues/1)
for subsequent full-diagram rendering, tests, release and integrations.

## License

drawio-java is licensed under the [MIT License](LICENSE). The bundled draw.io
JavaScript keeps its Apache-2.0 license (`src/main/resources/drawio/upstream/LICENSE`);
see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and
[docs/LICENSE-REVIEW.md](docs/LICENSE-REVIEW.md).
