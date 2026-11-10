# Pinned Draw.io JavaScript sources

Implementation scope: [Issue #4](https://github.com/aresstack/drawio-java/issues/4).

## Source provenance

- Official repository: [jgraph/drawio](https://github.com/jgraph/drawio).
- Branch at selection time: `dev`.
- **Immutable source commit:** `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`.
- Manifest: [js-bundle/upstream.json](js-bundle/upstream.json).
- License: [Apache License 2.0](src/main/resources/drawio/upstream/LICENSE).
- Local, committed bundle: `src/main/resources/drawio/drawio-shapes.js`.

Two original upstream files are vendored unchanged except for canonical LF line endings:

| Original path relative to `src/main/webapp` | Bundled purpose |
| --- | --- |
| `mxgraph/src/util/mxAbstractCanvas2D.js` | Original mxGraph state, coordinates and canvas operation vocabulary |
| `shapes/bpmn/mxBpmnShape2.js` | Original BPMN JavaScript shapes, including `mxgraph.bpmn.gateway2` |

The SHA-256 hashes of both LF-normalized files are pinned in the manifest.
The bundled output contains these two scripts in the same order and retains
the original copyright headers.

**Excluded:** the full editor, viewer, network loaders, remote fonts, stencil
XML packs, templates and vendor icon packs. This is a **minimal first-stage
shape bundle**, not a production-ready Draw.io viewer.

## Reproduce and verify the bundle

Run from a clean checkout with Node.js 18 or later:

```bash
node js-bundle/build.mjs
node js-bundle/build.mjs --verify
```

The script has no npm dependencies. It reads only the checked-in, pinned
JavaScript files, verifies their source digests, normalizes line endings and
concatenates them deterministically. The `--verify` mode checks the committed
bundle without overwriting it. The CI workflow exercises this verification.

**Java consumers need neither Node.js nor a browser.** The pre-generated
bundle ships in the Java JAR and is read from the classpath.

### Updating upstream

1. Review changes and licensing in the official Draw.io repository.
2. Pin an immutable upstream commit and verify the two original paths there.
3. Copy the necessary original files to `src/main/resources/drawio/upstream`.
4. Update commit, original paths and normalized SHA-256 values in the manifest.
5. Regenerate the checked-in bundle with `node js-bundle/build.mjs`.
6. Run `node js-bundle/build.mjs --verify` and `gradlew clean build`.
7. Review the registered shape names and the test fixture requirements before
   declaring compatibility. Do not auto-upgrade upstream without review.

## Additional pinned headless mxGraph-model sources

Issue #9 introduces an independent `drawio-model.js` bundle of
original `mxCell`, `mxGraphModel`, geometry, events and supporting
model classes. Its twelve source paths and normalized SHA-256 digests
are pinned in the same manifest. `node js-bundle/build.mjs --verify`
now validates **both** generated bundles. No source from the larger
browser UI/viewer has been added to the runtime.

See [docs/HEADLESS-MXGRAPH.md](docs/HEADLESS-MXGRAPH.md) for the
verified scope and known, still-missing DOM/View/Renderer dependencies.

## Original JavaScript API entry points

- `mxAbstractCanvas2D.prototype.translate` stores `state.dx`/`state.dy`.
  `scale` updates `state.scale`. Coordinates are later drawn as
  `(x + state.dx) * state.scale`; this informs the Java2D behavior in #5.
- `mxCellRenderer.registerShape('mxgraph.bpmn.gateway2', mxShapeBpmnGateway)`
  occurs in `mxBpmnShape2.js`.
- `mxShapeBpmnGateway.prototype.paintVertexShape(canvas, x, y, w, h)`
  issues Draw.io canvas commands, including `translate`, `begin`,
  `moveTo`, `lineTo`, `close`, `fillAndStroke` and `setShadow`.
- The gateway shape reads `gwType` values `event`, `exclusive`,
  `parallel`, `complex` along with `outline` and `symbol`.

## What the tests prove

`DrawioUpstreamBundleTest` evaluates the **original pinned JavaScript**
inside the restricted Java 8 GraalJS runtime, without a browser or Node.js
process. A test-only bootstrap provides minimal mxGraph inheritance and
registration objects. The test confirms:

- The expected gateway shape is registered and callable.
- A parallel gateway emits two `fillAndStroke` operations via its
  **original** JavaScript shape method.
- Original `mxAbstractCanvas2D` scale and translation state behaves as
  expected.

**Important limitation:** the test-only bootstrap is *not* a replacement for
mxGraph. Issue #6 adds `drawio-canvas-adapter.js` and a restricted
`DrawioCanvasBridge` that execute the original BPMN Gateway JavaScript
directly into Java2D. The end-to-end pixel tests for parallel, exclusive and
complex gateways have passed. However, the complete graph model, real DOM
replacements and reference-image parity remain in #7–#10.
Do not advertise full Draw.io compatibility based on this registration smoke test.

## Licensing and Atlassian integration

The original Draw.io **source code** is licensed under Apache 2.0. Its
[README](https://github.com/jgraph/drawio/blob/dev/README.md) additionally
restricts the included **icon sets, stencil libraries and diagram templates**
and derivatives in products distributed for Atlassian or its Marketplace
without written permission. This repository currently bundles two JavaScript
source files, **not** the XML stencil or icon packages.

Before distributing additional stencil/icon libraries, or distributing this
renderer as part of a Confluence/Atlassian-related product, assess the actual
terms and intended integration independently; a separate external application
reading Confluence is not automatically the same thing as an Atlassian plugin,
but the restriction must not be assumed irrelevant. See #15 for the release
license audit.

The upstream production viewer bundles default to remote resources and can
load stencil XML dynamically. See the
[official Embedding documentation](https://github.com/jgraph/drawio/wiki/Embedding).
This project deliberately does **not** ship `viewer-static.min.js` and does
not configure a network-based stencil fallback. Unknown assets must be
reported as unsupported until a licensed offline resolution is available.
