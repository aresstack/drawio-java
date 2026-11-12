# drawio-java

Headless draw.io diagram rendering for Java 8+. The renderer runs the
original, pinned draw.io JavaScript (mxGraph model, view, codec and shapes,
including BPMN) on GraalJS and paints it with Java2D into `BufferedImage`,
PNG or SVG. Consumers need no browser, no Node.js and no network access.

> **Status: pre-release.** Diagrams render end to end through the original
> draw.io code, and a reference corpus compares them with the official draw.io
> viewer (BPMN `gateway2` approval flow: score 0.998). The draw.io XML stencil
> data, which carries an upstream restriction for Atlassian-related use, is a
> separate optional artifact ([Dependencies](#dependencies),
> [docs/LICENSE-REVIEW.md](docs/LICENSE-REVIEW.md)).
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
- Render with the original draw.io JavaScript graph, shape and stencil logic,
  through a JavaScript canvas adapter and a restricted Java host bridge.
- Provide PNG/BufferedImage and SVG outputs through the same Java2D drawing model.

See [REUSE.md](REUSE.md) for the detailed component inventory and decisions.

## Dependencies

Two artifacts, always in the same version:

| Artifact | Needed for |
| --- | --- |
| `com.aresstack:drawio-java` | Everything: parser, renderer, the original draw.io JavaScript and all JavaScript shapes (standard shapes, `mxgraph.bpmn.gateway2`, `event`, `task`, ...) |
| `com.aresstack:drawio-java-stencils` (optional) | draw.io **XML stencils**: `mxgraph.flowchart.*`, `mxgraph.arrows.*`, the XML part of `mxgraph.basic.*`, legacy `mxgraph.bpmn.*` symbols and the BPMN task markers drawn from them |

Maven:

```xml
<dependency>
    <groupId>com.aresstack</groupId>
    <artifactId>drawio-java</artifactId>
    <version>${drawio-java.version}</version>
</dependency>
<!-- Optional: draw.io XML stencil data. Not for Atlassian products or the Atlassian Marketplace, see below. -->
<dependency>
    <groupId>com.aresstack</groupId>
    <artifactId>drawio-java-stencils</artifactId>
    <version>${drawio-java.version}</version>
    <scope>runtime</scope>
</dependency>
```

Gradle:

```groovy
dependencies {
    implementation "com.aresstack:drawio-java:$drawioJavaVersion"
    // Optional: draw.io XML stencil data. Not for Atlassian products or the Atlassian Marketplace, see below.
    runtimeOnly "com.aresstack:drawio-java-stencils:$drawioJavaVersion"
}
```

`drawio-java-stencils` contains only draw.io's stencil data. The upstream
draw.io README forbids using its stencil libraries in, or distributing them
for use with, Atlassian products or products distributed through the
Atlassian Marketplace without written permission. Leave the artifact out in
that case. The renderer finds it on the classpath with no configuration. Without it,
shapes that need an XML stencil are reported as `stencil:<name>` and, under
the default policy, fail with `UNSUPPORTED_STENCIL` instead of being
approximated. Details: [docs/STENCILS.md](docs/STENCILS.md#optional-artifact-drawio-java-stencils),
[docs/LICENSE-REVIEW.md](docs/LICENSE-REVIEW.md).

## Using the renderer

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
approximated (`UnsupportedFeaturePolicy.WARN` renders them with a warning
instead). Details: [docs/PUBLIC-API.md](docs/PUBLIC-API.md),
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

`build` produces both jars in `build/libs` (`drawio-java` and
`drawio-java-stencils`, each with sources and javadoc jars) and runs all
tests, including the reference corpus, the Java 8 bytecode gate
(`verifyJava8Bytecode`), the stencil split check (`verifyStencilArtifact`)
and offline smoke tests against the packaged jars: `consumerSmokeTest` (main
jar only) and `consumerSmokeStencilsTest` (with the stencil jar). Pass `-PtestJavaVersion=11|17|21` to run the tests
on another installed JDK. The corpus report with rendered images and diffs is
written to `build/reports/corpus/render.html`. CI runs all of this on JDK 8,
11, 17 and 21 and on Linux and Windows; see [docs/RELEASE.md](docs/RELEASE.md).

## How it works

```
draw.io file ─► DrawioDocumentParser (StrictXml: no DTD/entities, size and inflate limits)
            ─► GraalJS context (HostAccess.EXPLICIT, no files/network/threads)
               original mxCodec → Graph + mxStylesheet → mxGraphView.validate
               → mxCellRenderer → mxImageExport → DrawioJavaCanvas (mxAbstractCanvas2D)
            ─► DrawioCanvasBridge → CanvasRecorder (validated commands)
            ─► Java2DCanvas → BufferedImage / PNG, or Batik SVGGraphics2D → SVG
```

- **Original JavaScript, pinned:** mxClient core, the draw.io `Graph.js`
  parts that rendering needs (copied verbatim by `js-bundle/build.mjs`),
  `Shapes.js`, `mxBasic.js`, `mxBpmnShape2.js`, `mxArrows.js`, `er/mxER.js`,
  `mxFlowchart.js` and the `stencils.min.js` decoder, all from upstream commit
  `c72c9a98` with SHA-256 in [js-bundle/upstream.json](js-bundle/upstream.json).
  Consumers never need Node.js; `node js-bundle/build.mjs --verify` checks the
  committed bundles. See [UPSTREAM.md](UPSTREAM.md) and
  [docs/HEADLESS-GRAPH.md](docs/HEADLESS-GRAPH.md).
- **Java does the browser's job only:** text metrics and label layout
  ([docs/TEXT-METRICS.md](docs/TEXT-METRICS.md)), the canvas
  ([docs/CANVAS.md](docs/CANVAS.md)), budgeted base64/inflate, strict XML.
  No shape is re-implemented in Java.
- **One drawing path:** the JavaScript runs once into a recorder; raster and
  SVG output replay the same commands ([docs/OUTPUT.md](docs/OUTPUT.md)).
- **Restricted, not a sandbox:** GraalJS 21.3.20.0.3, the newest Java 8 line
  ([docs/GRAALJS-VERSION.md](docs/GRAALJS-VERSION.md)). Diagram XML is data,
  never JavaScript source. Timeouts close the context. See
  [docs/SECURITY.md](docs/SECURITY.md) for the boundary and its limits.

## Documentation

| Topic | Document |
| --- | --- |
| Supported and unsupported features | [docs/SUPPORT-MATRIX.md](docs/SUPPORT-MATRIX.md) |
| Public API, options, error codes | [docs/PUBLIC-API.md](docs/PUBLIC-API.md) |
| Document parsing and limits | [docs/XML-PARSING.md](docs/XML-PARSING.md) |
| Graph pipeline and Graph.js ports | [docs/HEADLESS-GRAPH.md](docs/HEADLESS-GRAPH.md) |
| Shapes and XML stencils | [docs/SHAPES.md](docs/SHAPES.md), [docs/STENCILS.md](docs/STENCILS.md) |
| Canvas, text and output | [docs/CANVAS.md](docs/CANVAS.md), [docs/TEXT-METRICS.md](docs/TEXT-METRICS.md), [docs/OUTPUT.md](docs/OUTPUT.md) |
| Performance and resource control | [docs/PERFORMANCE.md](docs/PERFORMANCE.md) |
| Security model, licenses | [docs/SECURITY.md](docs/SECURITY.md), [docs/LICENSE-REVIEW.md](docs/LICENSE-REVIEW.md), [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) |
| CI and release | [docs/RELEASE.md](docs/RELEASE.md) |
| Switching Enterprise AI Client and Space Harvester from JGraphX | [docs/INTEGRATION.md](docs/INTEGRATION.md) |
| Early proofs (#4–#8): go/no-go, first headless model | [GO-NO-GO.md](GO-NO-GO.md), [docs/HEADLESS-MXGRAPH.md](docs/HEADLESS-MXGRAPH.md) |

Milestones and remaining work: [Epic #1](https://github.com/aresstack/drawio-java/issues/1).

## License

drawio-java is licensed under the [MIT License](LICENSE). The bundled draw.io
JavaScript keeps its Apache-2.0 license (`src/main/resources/drawio/upstream/LICENSE`);
see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) and
[docs/LICENSE-REVIEW.md](docs/LICENSE-REVIEW.md).
