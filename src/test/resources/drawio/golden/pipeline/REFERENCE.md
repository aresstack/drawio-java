# Pipeline acceptance reference (#9)

## Fixtures

- `pipeline-acceptance.drawio`: synthetic BPMN-style diagram written for this
  project (no Confluence or internal content). Ellipse start event, rounded
  task with an HTML label (`<b>`), `mxgraph.bpmn.gateway2` exclusive gateway
  with a bottom label, a `group` with two child vertices (relative
  coordinates), four `orthogonalEdgeStyle` edges: one with a waypoint
  (`Array`/`mxPoint`), two with edge labels, one with `endArrow=open`, one
  dashed target.
- `pipeline-acceptance-shapes.drawio`: the same file with every `value`
  emptied (generated with `sed -E 's/ value="[^"]*"/ value=""/g'`), so the
  pixel comparison measures geometry, strokes, fills and arrows independently
  of fonts.

- `graph-features.drawio` / `graph-features-shapes.drawio`: rotation
  (`rotation=30`), rhombus, triangle with `direction=south`, cylinder,
  `swimlane` container with a child, a hidden layer (`visible="0"`) whose
  red cell must not appear, dashed ellipse with `dashPattern=8 4` and
  `strokeWidth=3`, hexagon, and edges with `elbowEdgeStyle` + diamond start,
  `curved=1` with a waypoint + open block end, `entityRelationEdgeStyle` with
  exit/entry constraints, rounded dashed orthogonal edge out of the swimlane
  child, oval end + open start.

## Reference renderer

- Upstream `jgraph/drawio` commit `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`,
  `src/main/webapp/js/viewer-static.min.js` (LF SHA-256
  `35b8ef54369e938ad2399d3c8ce3945591d4b200a01f6942d0e39743bea1f1a1`; the
  CRLF checkout used for `golden/REFERENCE.md` hashes to `1ed96a05...`).
- Loaded from a local file in Playwright Chromium (headless, Linux,
  viewport 1200x900, device scale 1), every non-`file:` request aborted
  (none were attempted). Viewer config `{xml, border: 0, zoom: 1}`.
- Created 2026-11-12. Only the outputs below are checked in, not the viewer.

## Outputs

- `pipeline-acceptance-viewer.png`: element screenshot of the viewer SVG of
  the full fixture (documentation; fonts differ across platforms).
- `pipeline-acceptance-shapes-viewer.png`: the same for the label-free
  fixture; used by `PipelineAcceptanceTest` for pixel comparison.
- `graph-features-viewer.png`, `graph-features-shapes-viewer.png`: the same
  for the feature fixture.
- `pipeline-acceptance-labels.json`: client rectangles of the innermost label
  `div`s relative to the viewer SVG. The viewer's root group is
  `translate(-19,-9)` (floored graph bounds 19.5/9.5), so diagram
  coordinates are these values plus (19, 9).

## Tolerances

- Pixels: dark pixels (luma < 128) of either image must have a dark pixel
  within 1 px in the other image (best alignment within +-2 px), for at
  least 98% of dark pixels. This absorbs half-pixel antialiasing differences.
- Labels: anchor of each `text` call (center for `align=center`, top for
  `verticalAlign=top`, middle for `middle`) within 1.5 px of the viewer box.
