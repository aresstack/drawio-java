# Integration notes: Enterprise AI Client and Space Harvester (#16)

Tracking: [Issue #16](https://github.com/aresstack/drawio-java/issues/16).
Written 2026-11-12 from the code on `main` of both consumers. **No consumer
code has been changed yet**; this is the plan for a thin adapter.

## Where draw.io is rendered today

Both consumers share the same code: Space Harvester's
`enterprise-ai-sources` is an unchanged copy of Enterprise AI Client's
`proasteion/emporion/holkas/source-confluence` module (see its
`UPSTREAM.md`; changes go to EAC first and are then re-synced, as the
"EAC-Kopie auf den Merge von …" commits do).

- `DrawioDiagramRenderer.toSvg(String xml)` renders the first page of an
  `mxfile`/`mxGraphModel` with **JGraphX 4.2.2** to SVG, after normalizing
  styles. Anything JGraphX cannot draw (all `mxgraph.*` stencils such as
  `mxgraph.bpmn.gateway2`, non-embedded images, unreadable data) throws
  `DrawioDiagramRenderer.Unsupported` with style names only.
- `ConfluenceRestClient.viewImage(...)` → `drawioSvg(...)` fetches the
  diagram via `/rest/drawio/1.0/diagram/crud/<name>/<pageId>?revision=<n>`
  (authorized session, existing transport), extracts `xml` and calls
  `toSvg`. On `Unsupported` or `KnowledgeSourceException` it logs the reason
  and falls back to `previewImage(...)`, the plugin's PNG preview, through the
  existing authorized `withWebSession` path. A `TwoFactorRequired` from that
  fallback is re-thrown unchanged (only a detail is appended), so the OTP
  prompt still appears exactly once.
- The reader rasterizes the returned SVG with Batik (same path as Mermaid).
  The image viewer's "Original" toggle requests the PNG preview directly.

## Target design

Replace only the JGraphX call. Fetching, authorization, OTP handling and the
PNG fallback stay exactly as they are.

```java
/** Thin adapter in source-confluence; replaces the JGraphX-based DrawioDiagramRenderer. */
final class DrawioDiagramRenderer {

    static final class Unsupported extends Exception { ... }   // keep: callers already handle it

    private static final RenderOptions OPTIONS = RenderOptions.builder()
            .page(PageSelector.first())                         // as today
            .resourcePolicy(ResourcePolicy.dataUrisOnly())      // never fetch images from inside the renderer
            .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.FAIL)
            .limits(RenderLimits.builder().timeout(Duration.ofSeconds(20)).build())
            .build();

    private static final class Holder {                         // one renderer per JVM, created lazily
        static final DrawioRenderer RENDERER = DrawioRenderer.builder()
                .maxConcurrentRenders(2).maxQueuedRenders(16).build();
    }

    static String toSvg(String xml) throws Unsupported {
        try {
            return Holder.RENDERER.renderSvg(xml, OPTIONS).getOutput();
        } catch (RenderException e) {
            // Only code and capability id (e.g. shape:mxgraph.foo), never diagram content.
            throw new Unsupported(e.getCode()
                    + (e.getCapabilityId() == null ? "" : " " + e.getCapabilityId()));
        }
    }
}
```

Mapping: every `RenderErrorCode` becomes `Unsupported`, so the existing
fallback to the authorized PNG preview applies. That is correct for
`UNSUPPORTED_STENCIL`, `UNSUPPORTED_FEATURE`, `MISSING_RESOURCE`,
`INVALID_*`, `PAGE_NOT_FOUND`, `TIMEOUT`, `LIMIT_EXCEEDED`, `OVERLOADED` and
`RENDER_FAILED`. `CANCELLED` means the calling thread was interrupted; the
adapter should restore nothing (the interrupt flag is already set) and the
caller may simply give up. The renderer itself never throws
`SignInRequiredException`, `TwoFactorRequired` or `SignInPending` and never
performs HTTP requests, so it cannot swallow or duplicate an OTP prompt.

Keep `UnsupportedFeaturePolicy.FAIL`: a partially drawn BPMN diagram would
be semantically wrong without a visible marker. If a later UI wants partial
diagrams, use `WARN` and show `RenderResult.isComplete() == false` to the
user.

### SVG or PNG

Returning SVG keeps the reader's existing Batik path and zoom quality.
drawio-java's SVG is a standalone document without DOCTYPE or external
references. If Batik rasterization of text differs visibly from the
renderer's own Java2D output, switch the adapter to
`renderPng(xml, OPTIONS.toBuilder().scale(2).build())`; `viewImage` already
accepts PNG by signature.

### Lifecycle

- One `DrawioRenderer` per JVM (holder above). Close it on application
  shutdown if there is a hook; its threads are daemons, so a missing close
  does not block exit.
- First render costs ~1.5 s (GraalJS class loading), later small diagrams
  tens of milliseconds ([PERFORMANCE.md](PERFORMANCE.md)). Optionally warm
  up with a tiny diagram on a background thread after startup.
- Desktop: `maxConcurrentRenders(2)`; server (Space Harvester export):
  up to `min(4, CPUs)`, and size the heap for that many large diagrams.

## Dependencies

- Prerequisite: a published `com.aresstack:drawio-java` release (#15).
  Both consumers resolve only from Maven Central
  (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`), like `mermaid-java`.
- **GraalJS alignment.** EAC and Space Harvester already get GraalJS 21.2
  through `mermaid-java` and `win-proxy-java`. drawio-java uses
  **21.3.20.0.3** ([GRAALJS-VERSION.md](GRAALJS-VERSION.md)). Gradle will
  resolve all `org.graalvm.*` modules to 21.3.20.0.3; the 21.x polyglot API
  is compatible, but run the Mermaid and proxy regression tests after adding
  the dependency. Do not exclude GraalJS modules: mixed Truffle versions on
  one classpath fail at runtime.
- Batik: drawio-java needs `batik-svggen` 1.17, the same Batik line as
  mermaid-java.
- Remove `libs.jgraphx` from `source-confluence` once the old renderer is
  gone.
- Java: EAC builds for Java 8 (fine). Space Harvester builds with Java 21;
  drawio-java's CI runs its suite on consumer JDKs 8, 11, 17 and 21.

## Tests to add in the consumers

1. Adapter unit test with a synthetic BPMN diagram containing
   `shape=mxgraph.bpmn.gateway2`, an edge and labels (reuse
   `src/test/resources/drawio/golden/bpmn-gateway-fixture.drawio` from
   drawio-java; no real Confluence diagrams).
2. Unknown stencil → `Unsupported` with only the capability id in the
   message (port `DrawioDiagramRendererTest`'s "no content in messages"
   expectations).
3. `ConfluenceViewImageTest`: when rendering fails, `previewImage` is
   called exactly once; a `TwoFactorRequired` from the fallback still
   reaches the caller with its type; no extra HTTP request is made by the
   renderer (the fake transport records all requests).
4. Re-sync the copy in Space Harvester and run its `ExportImagesTest`,
   `ImagesController`/`AssetsController` tests, so reading view, image
   export and caching use the same diagram bytes.

## Out of scope

- Space Harvester issue #571 (OTP) is a separate problem in the
  authentication flow, not in the renderer.
- Multi-page selection in the UI: today only the first page is shown. The
  `pageId` in the REST path is the Confluence page, not a diagram page. If
  the macro carries a diagram page name, pass it via `PageSelector.name`.
