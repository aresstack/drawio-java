# Reuse inventory and implementation decisions

Scope: [Issue #2](https://github.com/aresstack/drawio-java/issues/2).
The goal is **selective reuse with clear responsibilities**, not copying whole
implementations. Source and target versions are distinct: `mermaid-java`
targets Java 8; `playwright4j` targets Java 21.

## Verified source inventory

| Source | Purpose | License | Decision | Java 8 / adaptations | Tests |
| --- | --- | --- | --- | --- | --- |
| [mermaid-java: `GraalJsExecutor.java`](https://github.com/aresstack/mermaid-java/blob/main/src/main/java/com/aresstack/mermaid/GraalJsExecutor.java) | GraalJS Context execution, error reporting and timeout | MIT | **Adapt** for #3; do not copy whole class | Existing 21.2.0 runtime is Java-8-oriented; remove `allowAllAccess(true)`, `System.err` diagnostics and hard-coded executor/thread behavior; use narrow host bindings | Script success/failure, timeout, cancellation, close, denied host access |
| [mermaid-java: `JsExecutionResult.java`](https://github.com/aresstack/mermaid-java/blob/main/src/main/java/com/aresstack/mermaid/JsExecutionResult.java) | Immutable success/failure value | MIT | **Adapted in #2** as `ScriptExecutionResult` | Java 8; no GraalVM dependency; strengthened failure invariants and left public API design to #12 | `ScriptExecutionResultTest` |
| [mermaid-java: `MermaidRenderer.java`](https://github.com/aresstack/mermaid-java/blob/main/src/main/java/com/aresstack/mermaid/MermaidRenderer.java) | Resource loading, cached bundle, invocation and SVG result | MIT | **Adapt pattern**, #3/#4 | Mermaid-specific JS calls, singleton, HTML/SVG fixups and regex-based transformations must not be copied | Load pinned bundle, repeated render, missing resources |
| [mermaid-java: `browser-shim.js`](https://github.com/aresstack/mermaid-java/blob/main/src/main/resources/mermaid/browser-shim.js) | Minimal browser-like DOM/SVG features | MIT | **Reference / selective adapt**, #9 | Not a real browser DOM; detect requirements from actual Draw.io JS and implement only necessary features | Missing DOM functions, graph/text layout |
| [mermaid-java: `GraalJsExecutor.JavaBridge`](https://github.com/aresstack/mermaid-java/blob/main/src/main/java/com/aresstack/mermaid/GraalJsExecutor.java) | Text measurement via AWT and Batik bounding box | MIT | **Extract small ports**, #9 | Use explicit `@HostAccess.Export` and Java interfaces; do not expose arbitrary host objects | Font metrics, Unicode, layout geometry |
| [mermaid-java: `BatikBBoxService.java`](https://github.com/aresstack/mermaid-java/blob/main/src/main/java/com/aresstack/mermaid/BatikBBoxService.java) | SVG/Font geometry; GVT `getBBox`; LRU cache | MIT | **Adapt**, #9/#11 | Separate from UI and JS; restrict XML, SVG entities and external resource access; maintain thread confinement | Bounding boxes, XXE/external URLs, caching |
| [mermaid-java: `js-bundle/bundle.mjs`](https://github.com/aresstack/mermaid-java/blob/main/js-bundle/bundle.mjs) | esbuild to IIFE and bundled JS | MIT | **Adapt build pattern**, #4 | Pin a specific Draw.io upstream source version; no Node.js required by final consumer | Reproducible hashes, offline loading |
| [playwright4j: `GraalPlaywrightRuntime.java`](https://github.com/aresstack/playwright4j/blob/master/playwright4j/src/main/java/com/aresstack/playwright4j/graal/GraalPlaywrightRuntime.java) | Restricted GraalJS context, explicit host binding, resource loading and lifecycle | Apache-2.0 | **Adapt architecture, do not copy directly**, #3 | Upstream uses Java 21 and GraalVM polyglot 25.0.0; design equivalent Java-8-compatible runtime | Denied I/O, host lookup, close, exceptions |
| [playwright4j: `Playwright4JHost.java`](https://github.com/aresstack/playwright4j/blob/master/playwright4j/src/main/java/com/aresstack/playwright4j/graal/Playwright4JHost.java) | Host boundary with `@HostAccess.Export` | Apache-2.0 | **Reimplement small role-specific interfaces**, #3/#6 | Expose only canvas, text-metric and controlled resource operations, not generic filesystem/network or process hosts | Unauthorized access rejection, bridge calls |
| [playwright4j: `DenyAllHostClassLookup.java`](https://github.com/aresstack/playwright4j/blob/master/playwright4j/src/main/java/com/aresstack/playwright4j/graal/DenyAllHostClassLookup.java) | Forbid arbitrary Java class lookup | Apache-2.0 | **Reimplement pattern**, #3 | Java 8; use a restricted GraalJS host access configuration instead of `allowAllAccess(true)` | `Java.type` denied, reflection denied |
| [playwright4j: `node-compat-bootstrap.js`](https://github.com/aresstack/playwright4j/blob/master/playwright4j/src/main/resources/com/aresstack/playwright4j/runtime/node-compat-bootstrap.js) | Node.js compatibility/polyfills | Apache-2.0 | **Do not copy** | Draw.io renderer should not require Node, CDP, BiDi, WebSocket, HTTP, processes or driver pipes | Verify browser/Node-free rendering |

## Decisions and boundaries

1. **Current bootstrap:** Only `ScriptExecutionResult` is adapted as production code in
   this issue. The planned package boundaries are `api`, `graal`, `canvas` and
   `render`. No Draw.io renderer, downloads, Confluence authentication or network code
   belongs here yet.
2. **Dependency strategy:** Do **not** pull `mermaid-java` or `playwright4j` transitively.
   Keep their JavaScript engines and licensing independent. Select GraalJS coordinates
   only after testing Java-8 support in #3.
3. **Version investigation:** `mermaid-java/build.gradle` pins
   `org.graalvm.sdk:graal-sdk:21.2.0` and `org.graalvm.js:js:21.2.0` and declares
   Java 8. `playwright4j/playwright4j/build.gradle` uses
   `org.graalvm.polyglot:polyglot:25.0.0` and `org.graalvm.polyglot:js:25.0.0`
   with a **Java 21** toolchain. These versions cannot be assumed drop-in compatible.
   Prove actual runtime functionality with the Java-8 toolchain and the pinned
   JS bundle in #3/#4; then define a supported JDK matrix.
4. **Host safety:** Copy **no** `allowAllAccess(true)` from the old Mermaid executor.
   Use the restrictive `HostAccess`, no arbitrary `Java.type`, no filesystem,
   processes or network by default. Set explicit limits for untrusted diagram XML.
5. **Java rendering:** Render using an intentional `DrawioCanvas` interface and
   `Graphics2D` (`Path2D`, transforms, strokes), with SVG through Batik's
   `SVGGraphics2D`. JavaScript remains responsible for upstream shape and stencil
   semantics, if the proof in #7 succeeds.
6. **No hidden fallback:** The library must **not** download protected previews,
   resolve Confluence credentials or swallow `SignInRequired` exceptions.
   Keep that logic in the host application's authorized asset pipeline.

## Source licensing / attribution

- **mermaid-java** is MIT: Copyright (c) 2026 aresstack.
  The adapted `ScriptExecutionResult` derives its success/failure value-object
  design from the MIT-licensed `JsExecutionResult`.
  Preserve the MIT license notice in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
- **playwright4j** is Apache-2.0: Copyright 2026 Aresstack / Angelo Iglesias.
  No Playwright4J source is copied by this bootstrap; its safety architecture
  is a design reference. Attribute any future copied Apache-licensed components.
- **Draw.io upstream** must be evaluated separately for actual shape/stencil
  assets, bundled JS, fonts and third-party dependencies in #4/#15. Do not assume
  a single license covers every bundled asset.
- **drawio-java's own license:** not yet selected. Set the project's release
  license before publishing binaries (#15). Avoid implying that source-origin
  licenses automatically assign one to this new repository.

## Follow-up issue ownership

- [#3](https://github.com/aresstack/drawio-java/issues/3): execution runtime,
  security, timeouts and GraalJS dependencies.
- [#4](https://github.com/aresstack/drawio-java/issues/4): Draw.io upstream
  bundle, version pin and asset licensing.
- [#5](https://github.com/aresstack/drawio-java/issues/5): the Java2D canvas.
- [#7](https://github.com/aresstack/drawio-java/issues/7): go/no-go based on an
  original JS BPMN gateway rendered without a browser.
