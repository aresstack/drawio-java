# Headless original mxGraph pipeline (#9)

Tracking: [Issue #9](https://github.com/aresstack/drawio-java/issues/9).
Entry point: `com.aresstack.drawio.graph.OriginalGraphPipeline`, used by
`DrawioRenderer` through `DefaultPipeline`.

## What runs

Every page is rendered by the **original** draw.io/mxGraph JavaScript,
pinned to upstream commit `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`:

```
DiagramPage XML
  -> StrictXml (Java: no DTD/XXE, depth and element limits)
  -> XmlTreeJson (Java -> JSON)            -> DrawioXml (JS node model)
  -> mxCodec.decode                        -> original mxGraphModel/mxCell/mxGeometry
  -> mxGraph + mxStylesheet (default.xml)  -> styles, perimeters
  -> mxGraphView.validate                  -> cell states, edge routing (mxEdgeStyle),
                                              terminal points, label bounds
  -> mxCellRenderer                        -> original shape objects (mxShape subclasses,
                                              registered draw.io shapes, stencils)
  -> mxImageExport.drawState               -> shape.paint / text.paint
  -> DrawioJavaCanvas -> DrawioCanvasBridge -> CanvasRecorder (Java)
```

The GraalJS context is closed before `prepare` returns. The scene handed to
the output layer is an immutable command recording.

## Bundles and load order (one context)

| Order | Resource | Origin |
| --- | --- | --- |
| 1 | `drawio-headless-env.js` | this project: inert `window`/`document`/`navigator` |
| 2 | `drawio-graph.js` | **pinned upstream**, 61 mxClient core files in mxClient include order (`graphSources` in `js-bundle/upstream.json`) |
| 3 | `drawio-xml-dom.js` | this project: JSON to XML node model |
| 4 | `drawio-headless.js` | this project: draw.io compatibility ports, headless overrides, `DrawioHeadless.render` |
| 4b | `drawio-graph-ports.js` | **pinned upstream**, copied verbatim from `js/grapheditor/Graph.js` by `js-bundle/build.mjs` (`graphPorts` in `upstream.json`): (a) top-level members that `Shapes.js` and the blocks below call (table helpers, curve and multiple-copies statics, tie helpers, ...); (b) four whole top-level blocks (hash-checked line ranges): collapsed-table painting, label gaps, label placement, and the `mxGraphView`/`mxConnector`/`mxEdgeStyle` overrides (line jumps, edge ties, fixed point spacing, curved lines, `SideToSide`/`TopToBottom`/`ElbowConnector`) |
| 5 | `drawio-shape-library.js`, `drawio-stencil-runtime.js`, `drawio-stencils.js` | #10, loaded when present |
| 6 | `drawio-canvas-adapter.js` | `DrawioJavaCanvas` (#6/#10) |
| 7 | `drawio-render.js` | constant entry script; data arrives through `DrawioHost.loadResource` |

The pinned `styles/default.xml` (draw.io default stylesheet) is decoded with
the original `mxStylesheetCodec`.

The core bundle contains UI-free mxClient files only: utilities, model,
view, shapes, codecs, `mxImageExport`, `mxAbstractCanvas2D` and
`mxSvgCanvas2D` (loaded because the view reads its statics; never
instantiated). Handlers, editors, layouts, windows, popup menus,
`mxXmlRequest` and change codecs are **not** loaded: anything that tries to
use them fails loudly.

## What is overridden, and why

`drawio-headless.js` keeps upstream logic and replaces only DOM-bound steps:

| Upstream | Headless replacement |
| --- | --- |
| `mxShape.redrawNode` updates SVG/HTML nodes | keeps `updateBoundsFromPoints` and visibility; painting happens in export |
| `mxShape.getSvgBoundingBox` uses `getBBox` | returns null, so the geometric `getShapeBoundingBox` is used |
| `mxText.redraw` measures the label DOM | measures with `LabelLayout` via `DrawioHost.measureLabel` and sets `offsetWidth/offsetHeight`, which upstream `mxText.updateBoundingBox` already uses as cached HTML size |
| `mxImageExport.doDrawShape` appends SVG nodes and sets a crisp `translate` attribute | same steps as canvas `save/translate/paint/restore` |
| `mxGraph.createHandlers`, `mxCellRenderer.installListeners`, `mxEvent.add*Listener` | inert; no interaction headless |
| `mxCellRenderer.createShape` | unchanged, but unknown shape names are reported as `shape:<name>` before upstream falls back to a rectangle |

Compatibility ports from the pinned `js/grapheditor/Graph.js` (each marked
in the source): shadow constants, `mxCodec.allowlist` (reduced to diagram
types), `mxText.baseSpacingTop/Bottom`, `ignoreRelativeEdgeParent`,
`isHtmlLabel` (`html=1` or `whiteSpace=wrap`), entity escaping of wrapped `html=0` labels (`getLabelValue`), object values with a `label`
attribute, placeholders (`placeholders=1`: `%name%` from the cell or its
ancestors, `%id%`, `%width%`, `%height%`, `%length%`, `%%` escapes and the
`placeholder` attribute), and `default` color resolution for the light theme.
`light-dark(a, b)` colors render their light value.

`urlParams` is defined as an empty frozen object (no URL parameters, as in
the viewer). `mxAnimation`/`mxMorphing` are part of the core bundle because
the Graph.js view overrides reference them at load time.

## Why not load draw.io's Graph.js

`Graph.js` (1.5 MB) was tried in the headless environment. It needs
DOMPurify, `urlParams`, the editor/UI stack and a working browser DOM at
load and construction time. Supporting it would mean emulating a browser,
which the architecture rules exclude. The ports above cover what affects
rendering of diagram files; anything else is reported, not guessed.

## DOM vocabulary

The inert DOM in `drawio-headless-env.js` provides element factories,
attribute setters that do nothing, and empty child lists. Reads of any
other property are counted in `DrawioHeadlessEnv.probes` and returned in
the render report (`domProbes`). Current probe: `CANVAS.getContext`
(mxClient feature detection during load; no canvas is created).

## Security

- XML is data only. `mxCodec` resolves element names through `window[name]`;
  the allowlist limits that to `mxGraphModel`, `mxCell`, `mxGeometry`,
  `mxPoint`, `mxRectangle`, `Array`, `mxStylesheet`. `mxObjectCodec.allowEval`
  and `mxStylesheetCodec.allowEval` stay false.
- The JS node model never parses markup; Java's `StrictXml` does.
- Labels are measured and drawn through `HtmlLabelParser`, which interprets
  a small formatting subset and drops scripts; nothing is executed or fetched.
- GraalJS host access stays `EXPLICIT`; the guest sees only `DrawioHost`.

## Diagnostics

`DrawioHeadless.render` returns `{cells, scale, bounds, diagnostics, domProbes}`.
`OriginalGraphPipeline` maps `shape:`, `stencil:`, `label:` and `canvas:`
diagnostics to `RenderSession.unsupported` (fails or warns per policy) and
others (for example `edge:dangling-source`) to warnings. `maxCells` from
`RenderLimits` is enforced after decoding.

## Known gaps

- Placeholder forms that depend on the editor (`%date{...}%`, `%date%`,
  `%time%`, `%pagenumber%`, `%pagecount%`, unit suffixes like `%width_mm%`)
  stay verbatim with a `placeholder:unresolved` warning. Translations
  (`label_<lang>`), data styles/rules and data-source escaping of Graph.js
  are not ported.
- Sequence-diagram lifeline helpers and perimeter outlines of Graph.js are
  optional in `Shapes.js` (guarded by `!= null`) and not ported.
- Label size comes from Java fonts. Helvetica/Arial map to metric-compatible
  Liberation Sans/Arimo when installed; otherwise widths differ from browsers.
- HTML labels support the subset documented in `HtmlLabelParser`.
