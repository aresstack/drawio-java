# Shape and stencil support matrix (Issue #10)

All shapes come from the **original** draw.io JavaScript and stencil data of the
pinned upstream commit `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`; nothing is
re-implemented in Java. Java only provides the canvas (see
[CANVAS.md](CANVAS.md)) and safe host capabilities.

## Load order in one GraalJS context

| # | Script | Owner | Content |
| --- | --- | --- | --- |
| 1 | `drawio-headless-env.js` | #9 | Inert window/document stubs |
| 2 | `drawio-graph.js` | #9 | Original mxClient core: mxUtils, mxConstants, mxShape and core shapes, mxText, mxMarker, mxStencil, mxStencilRegistry, model, view, codecs |
| 3 | `drawio-xml-dom.js` | #9 | Pure-JS XML nodes built from Java-parsed XML |
| 4 | `drawio-headless.js` | #9 | Headless `Graph` subclass (Shapes.js references it at load) |
| 5 | **`drawio-shape-library.js`** | #10 | grapheditor `Shapes.js`, `shapes/mxBasic.js`, `shapes/bpmn/mxBpmnShape2.js`, `shapes/mxArrows.js`, `shapes/er/mxER.js`, `shapes/mxFlowchart.js` |
| 6 | **`drawio-stencil-runtime.js`** | #10 | Budgeted `atob`/`pako.inflateRaw`/`TextDecoder` via host codec; headless `mxStencilRegistry.getStencil` |
| 7 | **`drawio-stencils.js`** | #10 | Original `stencils.min.js` decoder + selected stencil sets. Only when on the classpath: shipped in the optional `drawio-java-stencils` artifact, not in `drawio-java` |
| 8 | `drawio-canvas-adapter.js` | #10 | `DrawioJavaCanvas` → `DrawioCanvasBridge` → `Java2DCanvas` |
| 9 | render entry | #9 | `mxImageExport` into `DrawioJavaCanvas` |

Order matters: `mxBpmnShape2.js` extends `mxgraph.basic.rect`, registered by
`Shapes.js`; the stencil decoder captures `mxStencilRegistry.loadStencil` when
it is evaluated, so the runtime must come first.

The older `drawio-shapes.js` (mxAbstractCanvas2D + mxBpmnShape2.js) and
`drawio-model.js` remain only for the stub-based regression tests from #4–#7.
They stay on the test classpath and are not shipped in the jar
(`testOnlyResources` in `build.gradle`).

## JavaScript shapes (`drawio-shape-library.js`)

| Source | Registered names |
| --- | --- |
| `js/grapheditor/Shapes.js` | Standard draw.io shapes: `process`, `process2`, `document`, `note`, `note2`, `card`, `cube`, `isoCube`, `isoCube2`, `isoRectangle`, `cylinder2`, `cylinder3`, `datastore`, `dataStorage`, `internalStorage`, `parallelogram`, `trapezoid`, `step`, `tape`, `tapeData`, `display`, `delay`, `manualInput`, `loopLimit`, `offPageConnector`, `collate`, `sortShape`, `switch`, `or`, `xor`, `orEllipse`, `sumEllipse`, `lineEllipse`, `plus`, `cross`, `corner`, `tee`, `crossbar`, `partialRectangle`, `callout`, `wedgeCallout`, `message`, `folder`, `component`, `module`, `link`, `filledEdge`, `flexArrow`, `singleArrow`, `doubleArrow`, `taperedArrow`, `curlyBracket`, `dimension`, `table`, `tableRow`, `tableLine`, UML (`umlActor`, `umlBoundary`, `umlControl`, `umlEntity`, `umlDestroy`, `umlFrame`, `umlLifeline`, `umlState`, `startState`, `endState`, `lollipop`, `requires`, `requiredInterface`, `providedRequiredInterface`, `associativeEntity`), sequence (`seqActorStick`, `seqBoundary`, `seqControl`, `seqEntity`, `seqDatabase`, `seqQueue`, `seqCollections`), git, mermaid and misc shapes (`hexagon`, `zigzag`, `wire`, `pipe`, `waypoint`, `curvedText`, `transparent`, ...). |
| `shapes/mxBasic.js` | `mxgraph.basic.*` JS shapes (rect, octagon, pie, arc, polygon, donut, cone2, wave, layered/corner/diag round & snip rects, callouts, frames, buttons, pyramid, triangles, ...). |
| `shapes/bpmn/mxBpmnShape2.js` | `mxgraph.bpmn.shape`, `mxgraph.bpmn.event`, `mxgraph.bpmn.gateway2`, `mxgraph.bpmn.task`, `mxgraph.bpmn.task2`, `mxgraph.bpmn.data`, `mxgraph.bpmn.data2`, `mxgraph.bpmn.swimlane`, `mxgraph.bpmn.conversation`, `mxgraph.bpmn.conversation2`, send marker. This covers the whole BPMN sidebar of the editor (`Sidebar-BPMN.js` uses `task2`, `event`, `gateway2`, `data2`, `swimlane`, `conversation2`). |

Core mxGraph shapes (`rectangle`, `ellipse`, `rhombus`, `triangle`, `cylinder`,
`actor`, `cloud`, `swimlane`, `label`, `image`, `line`, `arrow`,
`arrowConnector`, `doubleEllipse`, `connector` with markers) come from the core
bundle of #9.

## XML stencils (`drawio-stencils.js`, optional artifact `drawio-java-stencils`)

These sets are published separately as `com.aresstack:drawio-java-stencils`
because the upstream README restricts stencil libraries for Atlassian
products and the Atlassian Marketplace ([LICENSE-REVIEW.md](LICENSE-REVIEW.md),
section 2). Without that artifact every stencil below is reported as
`stencil:<name>` (`UNSUPPORTED_STENCIL` under the default policy), and so
are the BPMN task markers that draw them (task types `service`, `user`,
`manual`, `businessRule`, `script`; standard loop, compensation and ad hoc
markers of `mxgraph.bpmn.task`/`task2`). The JavaScript shapes above are
unaffected otherwise. See [STENCILS.md](STENCILS.md#optional-artifact-drawio-java-stencils).

| Set | Package | Stencils | Typical use |
| --- | --- | --- | --- |
| `bpmn.xml` | `mxgraph.bpmn.*` | 39 | Legacy BPMN symbols: gateways (`gateway_and`, `gateway_xor_(data)`, ...), events (`timer_start`, `message_end`, `terminate`, ...), task markers (`user_task`, `service_task`, `script_task`, `manual_task`, `business_rule_task`, `loop`, `ad_hoc`, ...). Also used internally by `mxgraph.bpmn.shape`. |
| `basic.xml` | `mxgraph.basic.*` | 30 | Stars, heart, x, arrows, ... |
| `flowchart.xml` | `mxgraph.flowchart.*` | 36 | Flowchart library (document, database, terminator, delay, ...). |
| `arrows.xml` | `mxgraph.arrows.*` | 34 | Arrow library. |

All 139 stencils paint through the original `mxStencil.drawShape` into Java2D
(`DrawioOfflineStencilsTest.paintsEveryBundledStencilThroughTheJavaCanvas`).

## Not supported (diagnosed, never substituted)

| Feature | Diagnostic | Reason / next step |
| --- | --- | --- |
| Stencil packages not bundled (`mxgraph.aws4.*`, `mxgraph.cisco.*`, `mxgraph.mockup.*`, ...) | `stencil:<name>` diagnostic → `UNSUPPORTED_STENCIL` under the default fail policy (`DrawioStencils.missing` in JS) | Vendor icon/stencil libraries carry the upstream Atlassian distribution restriction; add sets deliberately via the `include` list after the #15 license audit. |
| JS shape libraries other than basic/BPMN/arrows2/ER/flowchart (`mxAWS4.js`, `mxCisco.js`, `mxMockup*.js`, ...) | unknown shape → reported by the pipeline | Add to `shapeLibrarySources` on demand from the test corpus (#13). |
| Sketch/rough style, `fillStyle` hachure/dots/cross-hatch | `canvas:fillStyle:<style>` | Requires rough.js; out of scope. |
| Vertical writing modes | `canvas:text-direction:vertical-*` | No vertical text layout in Java2D path yet. |
| Embedded SVG images, external image URLs | `image:svg`, `image:external` | Policy: no SVG engine, no network. Hosts may supply bytes via `ResourcePolicy.withResolver`. |
| Shadow blur | (none; sharp shadow) | Kept identical for raster and SVG output. |
