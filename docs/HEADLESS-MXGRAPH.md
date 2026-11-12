# Headless mxGraph model – first integration slice

> **Historical.** This page documents the first model-only slice of #9.
> The production path is now the full original mxGraph pipeline described
> in [HEADLESS-GRAPH.md](HEADLESS-GRAPH.md) (`OriginalGraphPipeline`). The
> `drawio-model.js` and `drawio-shapes.js` bundles below remain only for
> the older stub-based tests; production rendering does not load them.

Tracking: [Issue #9](https://github.com/aresstack/drawio-java/issues/9).

## Verified scope

A separate `drawio-model.js` bundle contains the following **original
upstream** JavaScript classes, all pinned to the same immutable draw.io
commit `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`:

- Geometry: `mxPoint`, `mxRectangle`, `mxGeometry`
- Model: `mxCellPath`, `mxCell`, `mxGraphModel`
- Transactions/events: `mxEventObject`, `mxEventSource`, `mxEvent`,
  `mxUndoableEdit`
- Identity and dictionaries: `mxObjectIdentity`, `mxDictionary`

Their original files are checked in under
`src/main/resources/drawio/upstream/model/`. The `modelSources`
section of `js-bundle/upstream.json` records the original source path
and LF-normalized SHA-256 for each file.

Regenerate or verify both pinned JS bundles **offline** using:

```bash
node js-bundle/build.mjs
node js-bundle/build.mjs --verify
```

Consumers of the Java library do **not** require Node.js or a browser.

## Real upstream model proof

`OriginalMxGraphModelTest` runs the genuine `mxGraphModel` constructor
and cell/model APIs inside the restricted GraalJS runtime. The five
test cases demonstrate:

1. Creating a real graph model and looking up an inserted vertex by ID,
   parent and geometry.
2. Preserving nested group hierarchy and real edge source/target
   terminal references.
3. Emitting original model change notifications without a browser DOM.
4. Keeping `document`, `window.fetch`, `require` and `process`
   unavailable.
5. Obtaining vertex bounds from the real model and drawing the original
   `mxgraph.bpmn.gateway2` JavaScript shape into Java2D.

The tests load the pinned model bundle, pinned BPMN shapes and the
Java2D canvas adapter. No user-controlled JavaScript, network access,
filesystem access or UI toolkit is enabled.

## Explicit temporary limitations

The tests use **test-only** `test-bootstrap.js` plus
`model-bootstrap.js`: the latter provides an inert `window`
object for upstream event-module initialization and only two
known, pure `mxUtils` functions (`bind` and `isNumeric`).

These helpers are **not** a general browser DOM implementation and
are not packaged in the production JAR. They must not be mistaken
for a completed production runtime. In particular:

- The **actual** `mxGraph`, `mxGraphView`, `mxCellRenderer`,
  `mxCodec` and DOM-dependent layout/painting path are **not yet
  initialized**. Current rendering calls the original BPMN shape
  function explicitly after retrieving geometry from the model.
- The XML parser from #8 does **not yet** populate these upstream
  `mxGraphModel` objects. A codec-to-host bridge is still required.
- Edge routing, group coordinate transformations, graph bounds,
  zoom, label placement, text measurement and visual layout parity
  still belong to #9.
- The original `mxUtils` implementation includes DOM and browser
  dependencies; only the two small audited, pure utilities required
  by the current model proof are available.
- No real DOM, network, external resource resolver or browser events
  may be introduced as a shortcut. Unsupported capabilities must
  fail explicitly, not silently paint incorrect geometry.

## Next integration decisions

Done: the codec seam, `mxGraphView`/`mxCellRenderer` in GraalJS, the
`TextMetrics`/`LabelLayout` port and the comparison against the pinned
official viewer are implemented. See [HEADLESS-GRAPH.md](HEADLESS-GRAPH.md).
