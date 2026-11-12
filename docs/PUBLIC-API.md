# Public Java API (#12)

Tracking: [Issue #12](https://github.com/aresstack/drawio-java/issues/12).

The consumer-facing surface is deliberately small and plain Java 8:

| Type | Package | Role |
|------|---------|------|
| `DrawioRenderer` | `com.aresstack.drawio` | Entry point; thread-safe, long-lived, `AutoCloseable` |
| `RenderOptions` | `com.aresstack.drawio.api` | Page, scale or fit width, padding, background, policies, limits |
| `RenderLimits` | `com.aresstack.drawio.api` | Timeout, input size, cell count, output pixels/dimension, SVG size |
| `PageSelector` | `com.aresstack.drawio.api` | First page, by index, by name or by id |
| `ResourcePolicy`, `ResourceResolver` | `com.aresstack.drawio.api` | Which images may be used; consumer supplies bytes |
| `UnsupportedFeaturePolicy` | `com.aresstack.drawio.api` | `FAIL` (default) or `WARN` for unsupported shapes/features |
| `RenderResult<T>` | `com.aresstack.drawio.api` | Output, size, page, warnings, completeness, duration, versions |
| `RenderedPage` | `com.aresstack.drawio.api` | Index, id, name of the rendered page and page count |
| `RenderWarning` | `com.aresstack.drawio.api` | Capability id plus short message |
| `RenderException`, `RenderErrorCode` | `com.aresstack.drawio.api` | Checked failure with a stable code |
| `RendererVersion` | `com.aresstack.drawio.api` | Library version and pinned draw.io commit |

No public signature exposes GraalJS (`Context`, `Value`), JavaScript,
`Graphics2D`, Batik, UI toolkits or Confluence. Infrastructure packages
(`graal`, `canvas`, `render`, `parse`, `layout`) are public only because
Java 8 has no module boundaries; they are not a stable API.

## Example (Java 8)

```java
import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.*;

try (DrawioRenderer renderer = DrawioRenderer.create()) {
    String xml = DrawioRenderer.decodeUtf8(attachmentBytes);   // strict UTF-8, BOM ok
    RenderOptions options = RenderOptions.builder()
            .page(PageSelector.name("Process"))
            .scale(2.0)
            .padding(8)
            .background("#ffffff")
            .build();
    RenderResult<byte[]> png = renderer.renderPng(xml, options);
    RenderResult<String> svg = renderer.renderSvg(xml, options);
    if (!png.getWarnings().isEmpty()) {
        // only possible with UnsupportedFeaturePolicy.WARN or for harmless deviations
    }
} catch (RenderException e) {
    switch (e.getCode()) {
        case UNSUPPORTED_STENCIL:   // e.getCapabilityId() names the shape, e.g. shape:mxgraph.foo
        case UNSUPPORTED_FEATURE:
        case MISSING_RESOURCE:
            // show "diagram cannot be displayed faithfully", offer the authorized PNG fallback
            break;
        case TIMEOUT:
        case LIMIT_EXCEEDED:
        case OVERLOADED:
            // too big or too busy; retry later or show a placeholder
            break;
        default:
            // INVALID_XML, UNSUPPORTED_FORMAT, INVALID_DIAGRAM, PAGE_NOT_FOUND,
            // CANCELLED, RENDER_FAILED
    }
}
```

Background: `RenderOptions.background("#rrggbb")` fills the output. When no
background is set, the page background of the diagram (`mxGraphModel`
attribute `background`, as a hex color) is painted, as draw.io's export
does; without either, the output is transparent. A page background that is
not a hex color is reported as the warning `page:background`.

Style values outside their range are normalized at the canvas boundary the
way browsers treat them (opacities clamped to 0..1, negative stroke width
-> 1, negative corner radius -> 0, invalid dash arrays and unknown line
caps/joins ignored, unknown gradient direction -> south). Colors the canvas
cannot parse (for example `hsl(...)`, `currentColor`) are reported as
`canvas:color` through `UnsupportedFeaturePolicy`.


`src/test/java/com/example/consumer/ExternalConsumerTest.java` compiles
against only these public types from a foreign package.

## Error codes

| Code | Meaning | Origin |
|------|---------|--------|
| `INVALID_XML` | Not well-formed, DTD/entities, invalid UTF-8 | parser (`DiagramFormatException.Reason.INVALID_XML`), `decodeUtf8` |
| `UNSUPPORTED_FORMAT` | XML but not `mxfile`/`mxGraphModel` | parser |
| `INVALID_DIAGRAM` | Broken structure, compression or references | parser (`INVALID_DIAGRAM`, `INVALID_COMPRESSION`), pipeline |
| `PAGE_NOT_FOUND` | No such page, or ambiguous name/id | parser (`PAGE_NOT_FOUND`, `AMBIGUOUS_PAGE`) |
| `UNSUPPORTED_STENCIL` | Capability id starts with `shape:` or `stencil:` | pipeline via `RenderSession.unsupported` |
| `UNSUPPORTED_FEATURE` | Other capability (`canvas:gradient`, `label:html`, ...) | pipeline via `RenderSession.unsupported` |
| `MISSING_RESOURCE` | Image not allowed by `ResourcePolicy` or not supplied | pipeline via `RenderSession.missingResource` |
| `TIMEOUT` | `RenderLimits.timeout` expired (includes queueing) | facade |
| `CANCELLED` | Caller interrupted, or renderer closed during the request | facade |
| `LIMIT_EXCEEDED` | Input, decompression, cells, output size, nesting depth | parser, facade, output |
| `OVERLOADED` | Waiting queue full | facade |
| `RENDER_FAILED` | Anything unexpected | facade |

The parser's original reason stays available via
`RenderException.getFormatReason()`; parser errors are wrapped, not
duplicated.

**Confidentiality.** Messages never contain labels, values or XML. For
unexpected exceptions only the exception type is reported and the cause is
dropped; set `-Ddrawio.debug.causes=true` while debugging to keep it.

## Unsupported features

With the default `UnsupportedFeaturePolicy.FAIL`, the first shape, stencil,
canvas feature or resource that cannot be rendered faithfully fails the
request; nothing is silently substituted. With `WARN`, the pipeline skips
the element, records a `RenderWarning` with its capability id, and the
result reports `isComplete() == false`. Consumers that show such a result
must make the incompleteness visible.

## Threading, isolation and lifecycle

- A `DrawioRenderer` owns a fixed pool of daemon worker threads
  (`maxConcurrentRenders`, default `min(4, CPUs)`) and a bounded queue
  (`maxQueuedRenders`, default 64). Excess requests fail fast with
  `OVERLOADED`.
- Every request gets its own GraalJS `Context`, used by exactly one worker
  thread and closed when the request ends. All contexts share one polyglot
  `Engine`, which only caches parsed code of the bundled, trusted draw.io
  scripts (see [PERFORMANCE.md](PERFORMANCE.md)). Globals or prototype
  changes never cross requests.
- The calling thread blocks until the result is ready, the timeout expires
  (`TIMEOUT`) or it is interrupted (`CANCELLED`, interrupt flag kept). In
  both failure cases the request's JavaScript context is force-closed.
- Input size and cell count are checked before any JavaScript runs.
- `close()` cancels running requests, stops the workers and waits up to
  2 s; later calls throw `IllegalStateException`.
- On Java 8 the final Java2D rasterization is serialized process-wide
  (OpenJDK 8 Pisces is not deterministic under concurrency); JavaScript
  and recording still run in parallel. See [OUTPUT.md](OUTPUT.md).

## Security model

- No network, file system, process, thread creation, native access or host
  class lookup for JavaScript; only explicitly exported bridge methods.
- Diagram XML is data, never script. The parser rejects DTDs and external
  entities and bounds decompression.
- Images only through `ResourcePolicy`: none by default, `data:` URIs on
  request, everything else only as bytes from a consumer `ResourceResolver`.
  The renderer never fetches URLs, so it cannot trigger Confluence, OTP or
  proxy flows.
- This is a restricted embedding, not a hardened sandbox against hostile
  JavaScript. The JavaScript is the pinned, reviewed draw.io code.

## GraalJS version

See [GRAALJS-VERSION.md](GRAALJS-VERSION.md).

## Internal ports

- `render.DiagramPipeline` – `prepare(DiagramPage, RenderSession) -> DrawioScene`;
  implemented by the original-mxGraph pipeline (#9).
- `render.RenderSession` – deadline, cancellation hooks, limits, text
  metrics, resource policy, warnings, `unsupported(...)`/`missingResource(...)`.
- `render.DrawioScene` / `render.SceneRenderer` – repaintable scene and the
  shared Java2D/Batik output (#11).
- `layout.TextMetrics`, `api.ResourceResolver` – fonts and resources.

`DrawioRenderer.Builder.pipeline(...)` replaces the pipeline for tests; it
is not a supported extension point.
