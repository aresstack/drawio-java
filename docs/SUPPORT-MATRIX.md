# Supported and unsupported features (#15)

State of `main` on 2026-11-12. Every "supported" row is backed by tests. Most
rows point to the corpus in `src/test/resources/drawio/corpus`, which is
compared against the **official draw.io viewer** of the pinned commit
`c72c9a98`; scores are listed in [the corpus README](../src/test/resources/drawio/corpus/README.md).
If a feature is not listed here, treat it as unsupported until a fixture
proves otherwise. The library makes **no claim of full draw.io
compatibility**.

Legend: ✅ supported and regression-tested · ⚠️ partial or known deviation ·
❌ unsupported, reported as an error or warning · 🚫 deliberately refused.

## Platforms

| Item | Status | Evidence |
|---|---|---|
| Library bytecode Java 8 (class major ≤ 52) | ✅ | `verifyJava8Bytecode` in every CI build |
| Consumer JDK 8 (Linux, Windows) | ✅ | CI: reference job and Windows JDK 8 job |
| Consumer JDK 11, 17, 21 (Linux) and JDK 21 (Windows) | ✅ | CI consumer-JDK matrix runs all tests and the consumer smoke test |
| Offline consumer jar (no Node.js, browser or network) | ✅ | `consumerSmokeTest` runs against the packaged jar with a dead SOCKS proxy |
| macOS | ⚠️ | Not tested in CI. Java2D and GraalJS are portable |
| GraalVM distribution or Graal JIT | ⚠️ | Not needed. On a stock JDK GraalJS runs in interpreter mode (see [PERFORMANCE.md](PERFORMANCE.md)) |

## Input

| Feature | Status | Evidence |
|---|---|---|
| Plain `mxGraphModel` XML | ✅ | `golden/bpmn-gateway-fixture.drawio`, parser tests |
| `mxfile` with one or more pages | ✅ | `multipage-mixed` (3 pages, one empty) |
| Compressed pages (base64 + raw deflate + URL encoding) | ✅ | `bpmn-approval-flow-compressed` decodes to the same model and image as the plain fixture |
| Page selection by index or name, with ambiguity detection | ✅ | `duplicate-page-names` fails with `AMBIGUOUS_PAGE` |
| Unicode page names and labels | ✅ | `multipage-mixed` ("Übersicht"), `labels-unicode` |
| DTD, external entities, XInclude | 🚫 | `xxe-doctype` fails with `INVALID_XML` |
| Decompression bombs and oversize input | 🚫 | `decompression-bomb` (23 KiB inflating to 17 MiB) fails with `LIMIT_EXCEEDED` |
| Encrypted or other non-draw.io formats (`.vsdx`, `.svg` with embedded diagram, PNG with embedded XML) | ❌ | `unsupported-root` fails with `UNSUPPORTED_FORMAT` |

## Graph and layout (original mxGraph code)

| Feature | Status | Evidence |
|---|---|---|
| Vertices, edges, layers, cell hierarchy | ✅ | `groups-nested`, `edges-arrows` (score ≥ 0.99) |
| Groups, nested groups, swimlane containers | ✅ | `groups-nested` |
| Relative child geometry with offset | ✅ | `groups-nested` (badge) |
| Edge routing: straight, orthogonal, elbow, curved, waypoints, exit and entry constraints, self loop | ✅ | `edges-arrows`, `bpmn-approval-flow` |
| Arrow markers: classic, block, open, oval, diamond, none, start and end | ✅ | `edges-arrows` |
| Dangling edges (terminal points only) | ✅ | `edges-arrows` |
| Edge pointing at a missing cell | ⚠️ | Rendered with a structured warning `edge:dangling-target` (`dangling-edge-reference`) |
| Rotation, flip, opacity, dashed lines, stroke width | ✅ | `styles-basic-shapes` |
| Gradients and shadows | ✅ | `styles-basic-shapes` |
| Placeholders, translations, data styles and rules, collapsed tables (Graph.js) | ❌ | [HEADLESS-GRAPH.md](HEADLESS-GRAPH.md), known gaps |
| Sketch style (`sketch=1`, hachure fills) | ❌ | Reported as `canvas:fillStyle:*` |

## Shapes

| Feature | Status | Evidence |
|---|---|---|
| Core mxGraph shapes (rectangle, rounded, ellipse, rhombus, triangle, hexagon, cloud, cylinder, actor, double ellipse, swimlane, image, label) | ✅ | `styles-basic-shapes` |
| draw.io Shapes.js shapes (e.g. `process`, `parallelogram`) | ✅ | `styles-basic-shapes` |
| **BPMN `mxgraph.bpmn.gateway2`** (exclusive, parallel, complex, event) | ✅ | `bpmn-approval-flow` (score 0.998), `bpmn-shapes-catalog`, golden gateway test |
| BPMN events (`mxgraph.bpmn.event`, all outlines, common symbols) | ✅ | `bpmn-shapes-catalog` |
| BPMN tasks, data and conversation (`mxgraph.bpmn.task`, `data`, `conversation`) | ✅ | `bpmn-shapes-catalog` |
| XML stencil sets bundled offline: arrows, basic, bpmn, flowchart | ⚠️ | [STENCILS.md](STENCILS.md). See the license caveat in [LICENSE-REVIEW.md](LICENSE-REVIEW.md) |
| Other stencil sets (AWS, Azure, GCP, Cisco, UML packs, ...) | ❌ | `unknown-stencil` fails with `UNSUPPORTED_STENCIL` by default; `UnsupportedFeaturePolicy.WARN` gives a warning |
| JavaScript shape libraries `mxBasic.js`, `mxFlowchart.js`, `mxArrows.js` | ❌ | Reported as unknown stencil |
| Inline stencils `shape=stencil(...)` | ❌ | [STENCILS.md](STENCILS.md), known gaps |

## Text

| Feature | Status | Evidence |
|---|---|---|
| Plain labels, multi-line, wrapping, alignment, spacing | ✅ | `labels-unicode` (score 0.93 against the browser, all 12 labels present in SVG) |
| Umlauts, Greek, Cyrillic, CJK, symbols | ⚠️ | All glyphs are present in the SVG text. Glyph shapes depend on the installed fonts (see below) |
| HTML labels: `br`, block elements, `b/strong`, `i/em`, `u`, `s`, `font`, inline `style` | ⚠️ | `HtmlLabelParser` subset ([CANVAS.md](CANVAS.md)) |
| Plain-text label containing `<`, `>`, `&`, quotes (`html=0`) | ✅ | `labels-unicode` (SVG label check) |
| Vertical text (`horizontal=0`) | ✅ | `labels-unicode` |
| Vertical writing modes, other label formats | ❌ | Reported as `canvas:text-direction:*` and `canvas:text-format:*` |
| Scripts, event handlers and `javascript:` links in labels | 🚫 | `active-content-label`: shown as text and never executed or fetched |
| Font metrics identical to browsers | ⚠️ | Java2D fonts. Helvetica and Arial map to Liberation Sans or Arimo when installed; other fonts change widths and wrapping. The corpus tolerates this, but byte-identical output across platforms is not promised |

## Images and resources

| Feature | Status | Evidence |
|---|---|---|
| Embedded `data:` images (PNG, JPEG, GIF, BMP) with byte and pixel limits | ✅ | `images-data-uri` (with `ResourcePolicy.dataUrisOnly()`) |
| External image URLs | ⚠️ | Never fetched by the renderer. They give a warning `image:external`, or are resolved through a caller-supplied `ResourceResolver` |
| Embedded SVG images | ❌ | Reported as `image:svg` |
| Remote fonts and stencils | 🚫 | Never loaded |

## Output

| Feature | Status | Evidence |
|---|---|---|
| `BufferedImage` and PNG | ✅ | Corpus and output tests |
| SVG from the same drawing code (Batik `SVGGraphics2D`) | ✅ | Corpus label checks run on SVG output; [OUTPUT.md](OUTPUT.md) |
| Scale, fit width, padding, background, DPI | ✅ | [OUTPUT.md](OUTPUT.md) |
| Deterministic output for the same input, sequential and parallel | ✅ | `CorpusDeterminismTest` |

## Security and robustness

| Feature | Status | Evidence |
|---|---|---|
| Diagram data never evaluated as JavaScript | ✅ | `codec-eval-injection`, [SECURITY.md](SECURITY.md) |
| Guest JavaScript cannot reach host classes, files, network, threads or polyglot | ✅ | `GraalJsSecurityTest` |
| Timeout, cancellation, bounded parallelism, close | ✅ | `DrawioRendererTest` |
| Error messages without diagram content | ✅ | `CorpusNegativeTest` |
| Heap cap per render | ❌ | Not possible on a stock JDK ([SECURITY.md](SECURITY.md)) |

## How to extend this matrix

Add a synthetic fixture to the corpus, generate its official-viewer
reference, and promote it to `render="match"` once it passes (see the corpus
README). Then update the matching row here and link the fixture.
