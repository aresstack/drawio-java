# Offline draw.io XML stencils

Tracking: [Issue #10](https://github.com/aresstack/drawio-java/issues/10).
License review: [Issue #15](https://github.com/aresstack/drawio-java/issues/15).

draw.io paints most `shape=mxgraph.<set>.<name>` styles from **XML stencils**
interpreted by the original `mxStencil` class. This slice makes a selected
subset of the official stencil data available **offline** inside the
restricted GraalJS runtime: no browser, no Node.js, no network, no `eval`.

## Where the data comes from

The public draw.io repository does not contain stencil XML files. It ships
`src/main/webapp/js/stencils.min.js` (3.8 MB), pinned here at commit
`c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`. That file consists of:

1. a two-line preamble: `(function() {` and `var f = Object.create(null);`
2. one line per stencil set: `f['bpmn.xml'] = '<base64>';`. The base64 text
   is a raw DEFLATE stream of draw.io's compact binary stencil encoding
   (operation, coordinate and string streams; see `Xml2Js.java` upstream).
3. the original decoder. It builds a light `StencilNode` tree (`nodeName`,
   `nodeType`, `getAttribute`, `firstChild`, `nextSibling`, `parentNode`,
   `attributes`, `getElementsByTagName`). It also replaces
   `mxStencilRegistry.loadStencil(filename)`, which then returns
   `{documentElement: root}` for bundled sets and calls the previous
   `loadStencil` for all others.

The complete original file is checked in as `js-bundle/upstream/stencils.min.js`
for reproducibility. Its LF-normalized SHA-256 is pinned in
`js-bundle/upstream.json`. It is **not** a classpath resource and is not part
of the JAR.

## Subset bundle

`node js-bundle/build.mjs` writes `src/main/resources/drawio/drawio-stencils.js`:

```
banner comment (provenance, selected sets)
original preamble                    -- verbatim
f['arrows.xml'] = '...';             -- verbatim lines, original order
f['basic.xml'] = '...';
f['bpmn.xml'] = '...';
f['flowchart.xml'] = '...';
original decoder tail                -- verbatim
```

The selection is the `stencils.include` list in `js-bundle/upstream.json`. The
build fails if the source hash does not match, if an entry line has an
unexpected format, if the entries are not contiguous, or if a selected set
does not exist upstream. `node js-bundle/build.mjs --verify` checks all three
generated bundles without writing them, and CI runs it.

Current selection (about 17 KB in the JAR):

| Set | Package prefix | Stencils | Notes |
| --- | --- | ---: | --- |
| `bpmn.xml` | `mxgraph.bpmn.` | 39 | Events, gateways and task markers (see below) |
| `basic.xml` | `mxgraph.basic.` | 30 | Stars, callouts, polygons, smiley, ... |
| `flowchart.xml` | `mxgraph.flowchart.` | 36 | The XML part of the flowchart library |
| `arrows.xml` | `mxgraph.arrows.` | 34 | Block arrows |

Upstream has no top-level `general.xml` or `misc.xml`; only vendor-specific
sets such as `aws/misc.xml` exist, so none were added.

`bpmn.xml` registers: `ad_hoc`, `business_rule_task`, `cancel_end`,
`cancel_intermediate`, `compensation`, `compensation_end`,
`compensation_intermediate`, `error_end`, `error_intermediate`, `gateway`,
`gateway_and`, `gateway_complex`, `gateway_or`, `gateway_xor_(data)`,
`gateway_xor_(event)`, `general_end`, `general_intermediate`, `general_start`,
`link_end`, `link_intermediate`, `link_start`, `loop`, `loop_marker`,
`manual_task`, `message_end`, `message_intermediate`, `message_start`,
`multiple_end`, `multiple_instances`, `multiple_intermediate`,
`multiple_start`, `rule_intermediate`, `rule_start`, `script_task`,
`service_task`, `terminate`, `timer_intermediate`, `timer_start`, `user_task`,
all prefixed with `mxgraph.bpmn.`. The modern BPMN JavaScript shapes such as
`mxgraph.bpmn.gateway2` or `mxgraph.bpmn.shape` are not stencils. They come
from `mxBpmnShape2.js` in `drawio-shapes.js`.

To add a set, append its upstream key (for example `"electrical/logic_gates.xml"`)
to `stencils.include`, run `node js-bundle/build.mjs`, and review the license
section below first.

## Runtime data flow

Load order inside one GraalJS context:

1. Core bundle with the original `mxUtils`, `mxConstants`, `mxShape`,
   `mxConnectionConstraint`, `mxStencil` and `mxStencilRegistry` (issue #9,
   `drawio-headless-env.js` + `drawio-graph.js`).
2. `drawio-stencil-runtime.js` (this project).
3. `drawio-stencils.js` (verbatim upstream subset).

`drawio-stencil-runtime.js` must come before the subset bundle, because the
upstream decoder captures the current `mxStencilRegistry.loadStencil` as its
fallback when it is evaluated.

```
mxCellRenderer / include-shape
  -> mxStencilRegistry.getStencil('mxgraph.bpmn.gateway_and')        [runtime]
     -> getBasenameForStencil -> 'bpmn'                              [Graph.js port]
     -> loadStencilSet('stencils/bpmn.xml')                          [Graph.js port]
        -> loadStencil (upstream decoder)                            [drawio-stencils.js]
           f['bpmn.xml'] -> atob -> pako.inflateRaw -> TextDecoder
              |             |         |                 (runtime shims)
              +-------------+---------+--> DrawioHost.binaryCodec()   [Java, budgeted]
           -> StencilNode tree
        -> parseStencilSet -> new mxStencil(shape) -> addStencil     [Graph.js port]
  -> mxStencil.drawShape(canvas, ...)                                [original]
     -> DrawioJavaCanvas -> DrawioCanvasBridge -> Java2DCanvas
```

### Host capabilities instead of browser APIs

GraalJS 21.2 provides neither `atob`, `pako` nor `TextDecoder`.
`drawio-stencil-runtime.js` defines each one **only if it is not already
defined**:

| Guest API | Backed by | Limits |
| --- | --- | --- |
| `atob(text)` | `DrawioBinaryCodec.decodeBase64` (`java.util.Base64`, ASCII whitespace ignored) | 16 Mi input characters |
| `pako.inflateRaw(bytes)` | `DrawioBinaryCodec.inflateRaw` (`java.util.zip.Inflater(true)`) | 8 MiB output per call, 64 MiB per codec (configurable); truncated, corrupt or trailing data fails |
| `new TextDecoder().decode(bytes)` | `DrawioBinaryCodec.decodeUtf8` (strict `CharsetDecoder`) | UTF-8 only; malformed input fails instead of producing U+FFFD; a leading BOM is removed |

Bytes cross the guest boundary as Latin-1 strings (one character per byte,
0 to 255). Strings convert safely under `HostAccess.EXPLICIT` without exposing
Java arrays or enabling array access for scripts. `DrawioHost.binaryCodec()`
is the only new export. Use
`new DrawioHost(resources, canvas, metrics, new DrawioBinaryCodec(perCall, total))`
to change the budgets. The context keeps all existing restrictions: no host
class lookup, no IO, no threads, no native access.

### Headless `mxStencilRegistry`

`drawio-stencil-runtime.js` ports `getStencil`, `getBasenameForStencil`,
`loadStencilSet`, `isFileLoaded`, `setFileLoaded` and `parseStencilSet` from
`src/main/webapp/js/grapheditor/Graph.js` (lines 33595-33893 at the pinned
commit). The code is copied with these marked deviations:

- `libraries`, `packages` and `filesLoaded` are null-prototype maps, because
  stencil names come from untrusted diagram styles (`mxgraph.__proto__.x`).
- `.js` library entries are never fetched or evaluated (`allowEval = false`).
  They are listed in `DrawioStencils.skippedScripts`. JavaScript shapes must
  come from a pinned bundle such as `drawio-shapes.js`.
- `loadStencil` (the XMLHttpRequest loader) is replaced by a fallback that
  records `stencil-set-not-bundled` and returns `null`.
- Asynchronous loading and `parseStencilSets` (XML strings through a DOM
  parser) throw.
- Decoding errors such as an exceeded budget propagate. They are not written
  to a console and swallowed.
- When an `mxgraph.*` name is still unknown after loading, it is recorded as
  `unknown-stencil` in `DrawioStencils.missing`. With
  `DrawioStencils.strict = false`, the default, `getStencil` returns `null`
  and, when the headless graph pipeline (`drawio-headless.js`) is loaded,
  reports a `stencil:<name>` diagnostic through `DrawioHeadless.report`.
  `OriginalGraphPipeline` hands it to the `RenderSession`, whose
  `UnsupportedFeaturePolicy` decides: `FAIL` (default) ends the render with
  `UNSUPPORTED_STENCIL`, `WARN` keeps `mxCellRenderer`'s default shape and
  marks the result incomplete. A substitution is therefore never silent.
  With `strict = true`, `getStencil` throws
  `Unsupported draw.io stencil: <name>` instead.

Names that are not `mxgraph.*` (for example `rectangle`) and names registered
in `mxCellRenderer.defaultShapes` are passed through exactly as upstream does.

## Tests

`DrawioOfflineStencilsTest` evaluates everything in the restricted
`GraalJsRuntime`:

- decodes `bpmn.xml` and `basic.xml` and asserts concrete registered names,
  then all four sets with exact counts (39/30/36/34);
- checks original `mxStencil` parsing (size, `aspect="fixed"`, constraints);
- resolves stencils on demand through `getStencil` and caches the result;
- asserts that a missing set (`mxgraph.aws4.*`) throws in strict mode and is
  recorded in lenient mode (the default), together with an unknown name in a bundled set
  and a prototype-pollution style name;
- enforces the per-set and the cumulative inflate budgets;
- paints `mxgraph.bpmn.gateway_and` through the original
  `mxStencil.drawShape` into a Java2D `BufferedImage` using
  `DrawioJavaCanvas` and checks the fill, outline and plus-sign pixels.

`DrawioBinaryCodecTest` covers the Java codec directly.

The tests use **test-only scaffolding** in `src/test/resources/drawio/`:
`test-upstream/` holds unmodified copies of `mxStencil.js`,
`mxStencilRegistry.js` and `mxConnectionConstraint.js`, and
`stencil-test-bootstrap.js` adds the few `mxUtils`, `mxConstants` and
`mxShape.addPoints` members they need. In production these come from the
issue #9 core bundle; nothing of this scaffolding is in the JAR.

## Known gaps

- Canvas: 49 of the 139 bundled stencils use `<arc>`. The current
  `DrawioJavaCanvas.arcTo` throws. Among them are the BPMN task markers
  (`user_task`, `service_task`, `manual_task`, `script_task`, `loop`,
  `ad_hoc`), several flowchart shapes (`document`, `database`, `terminator`,
  `delay`, ...) and many basic shapes and arrows. The other 90 paint without
  errors. The upstream `mxAbstractCanvas2D.arcTo` converts arcs with
  `mxUtils.arcToCurves(this.lastX, this.lastY, ...)`, so the adapter must track
  `lastX/lastY` in `begin/moveTo/lineTo/quadTo/curveTo` before it can
  delegate to it.
- `<text>` stencil nodes pass alignment, rotation and font state to
  `canvas.text(...)`. The adapter currently forwards only the first five
  arguments. None of the four bundled sets uses `<text>` or `<image>`.
- `<alpha>`, `<fillalpha>` and `<strokealpha>` pass the attribute as a
  string. The adapter should convert it with `Number(...)` before calling
  the Java bridge. None of the bundled sets uses them.
- JavaScript shapes of the same libraries (`mxBasic.js`, `mxFlowchart.js`,
  `mxArrows.js`) are not bundled. Styles that use them are reported as
  `unknown-stencil`.
- `shape=stencil(<base64>)` inline stencils are a separate Graph.js path and
  are not handled here.

## Licensing note (flagged for #15)

The draw.io source code, including `stencils.min.js` and the decoder, is
Apache-2.0. The upstream [README](https://github.com/jgraph/drawio/blob/dev/README.md)
additionally states that the **stencil libraries, icon sets and diagram
templates** may not be used, directly or as derivatives, in products
distributed for Atlassian or its Marketplace without written permission.

The four selected sets are generic notation sets (BPMN, basic shapes,
flowchart, arrows) and no vendor icon packs. They are nevertheless stencil
libraries in the sense of that restriction. Therefore:

- the subset is **opt-in**: `drawio-stencils.js` and
  `drawio-stencil-runtime.js` are only evaluated when a caller loads them
  explicitly;
- the selection is kept small and recorded in the manifest;
- the subset must be reviewed in the release license audit (#15) before any
  distribution, and before any Confluence/Atlassian-related distribution in
  particular. Distributors of such products should exclude
  `drawio/drawio-stencils.js` from their artifact unless they hold permission.
