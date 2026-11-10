# Draw.io BPMN gateway reference fixture

## Provenance

- Draw.io upstream repository: https://github.com/jgraph/drawio
- Immutable source commit: `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`.
- Reference renderer: `src/main/webapp/js/viewer-static.min.js` from that commit.
- Reference viewer SHA-256:
  `1ed96a05a7cbe8158896bf2d52043434d648655a0577cd973da5e143eecadcac`.
- Viewer documentation: https://github.com/jgraph/drawio/wiki/Embedding
- Created: 2026-11-10, with locally executed Chrome Headless on Windows.
- Fixture: `bpmn-gateway-fixture.drawio`.
- Captured image: `bpmn-gateway-reference.svg`.

## Test input

The XML is synthetic, not copied from Confluence or any internal service.
It contains three 80-by-80 cells at positions (5, 5), (105, 5) and
(205, 5), all with `shape=mxgraph.bpmn.gateway2`, white fill, black
stroke, and types `parallel`, `exclusive` and `complex`.

## Generation procedure

1. Check out the pinned Draw.io source commit.
2. Load `viewer-static.min.js` **locally**, not from a CDN.
3. Create a local HTML page containing an `mxgraph` div with
   `data-mxgraph` holding JSON `{ "xml": "<mxGraphModel>...", "border": 0, "zoom": 1 }`
   for the fixture. Use HTML attribute escaping for the JSON.
4. Execute the file in a headless Chrome browser with local-file access
   enabled and sufficient script execution time; capture the fully rendered DOM.
5. Extract the single Draw.io SVG element. The captured SVG has six
   `path` elements (three outer diamonds and three internal symbols).
6. Normalize the SVG document for Batik with a 300x100 viewBox:
   add a `translate(4,4)` wrapper to reverse the viewer's responsive
   `translate(-4,-4)` origin shift, retain its original path geometry
   and the 0.5-pixel path transform, and remove only `light-dark()`
   inline color declarations, which Batik cannot interpret. Keep the
   underlying SVG `fill`/`stroke` attributes unchanged.
7. Check in only this normalized SVG and the XML fixture. Do **not** check
   the 4.9-MB upstream viewer bundle, Chromium profile or temp DOM output.

The reference was rendered with the official original Draw.io viewer.
It was **not** produced by the `drawio-java` renderer under test.

## Visual validation

`BpmnGatewayVisualReferenceTest` parses the static XML fixture with
external entities/DOCTYPE disabled, invokes the unchanged upstream BPMN
JavaScript functions through GraalJS and the Java2D bridge, and compares
the resulting raster with Batik's rasterization of the reference SVG.

The test classifies visible pixels as transparent, dark or light and
compares only stable 3x3 interiors, avoiding non-semantic antialiasing
differences along edges. It allows no more than 30 stable pixel
differences across the full 300x100 image. The renderer must paint
all three distinct gateway shapes; a blank output does not pass.

Batik is a **test-only** dependency here. The Java library runs without
a browser, Node.js or a CSS/SVG rendering engine. More complete shape,
text and connector parity belongs to later tickets (#8–#13).
