# Canvas features (Issue #10)

The Draw.io canvas vocabulary (`mxAbstractCanvas2D`) flows through three layers:

1. `drawio-canvas-adapter.js` (`DrawioJavaCanvas`) keeps the original
   JavaScript canvas state, so upstream shapes that read `canvas.state` behave
   as in the browser, and forwards each command to the bridge.
2. `DrawioCanvasBridge` is the policy boundary: it resolves image references
   through an `ImageSourcePolicy` and reports unsupported features to
   `CanvasDiagnostics` (default: fail fast). `render.PolicyImageSources` and
   `render.SessionCanvasDiagnostics` connect it to the public `ResourcePolicy`
   and `RenderSession`.
3. `DrawioCanvas` / `Java2DCanvas` paint with plain `Graphics2D` calls, so the
   same command stream renders to raster images and to Batik SVG.

All `DrawioCanvas` methods are `void` with primitive or `String` arguments, so a
command stream can be recorded and replayed.

## Reference semantics

The reference is the SVG canvas `mxSvgCanvas2D` of the pinned upstream commit.

| Feature | Behavior |
| --- | --- |
| Colors | CSS named colors, `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()`, `light-dark(light, dark)` (light variant), `none`. Anything else fails. |
| Stroke width | `scale()` also scales the current stroke width (as `mxAbstractCanvas2D.scale`); painted width is `strokeWidth * scale` (as `getCurrentStrokeWidth`). |
| Dashes | Lengths scale with `strokeWidth * scale`, or with `scale` only if `fixDash`. Non-numeric tokens (e.g. `none` in stencils) are ignored; no numbers means a solid line. |
| Gradients | `south` (default), `north`, `east`, `west`, `radial`. Like SVG `objectBoundingBox` gradients they span the bounds of each filled path in the shape's unrotated system and rotate with the shape. Stop alphas multiply the fill alpha. A missing end color paints a solid fill. |
| Shadows | Offset copy of the filled area and/or stroke outline (one union, so overlaps are not darkened twice) in the shadow color, offset by `(dx, dy) * scale`, painted before the shape. mxShape's shadow style object (`dx`, `dy`, `blur`, `color`, `opacity` %) and the `setShadowColor/Alpha/Offset` state are both honored. **Blur is not rendered** (Java2D/SVG parity), so shadow edges are sharp. |
| `arcTo` | The original `mxAbstractCanvas2D.arcTo` converts arcs to cubic curves via `mxUtils.arcToCurves`; the adapter tracks `lastX/lastY` for it. |
| Plain text | Positioned exactly like `mxSvgCanvas2D.plainText`: anchor and `text-anchor` from `align`, first baseline `y + size - 1`, line height `round(size * 1.2)`, `valign` middle/bottom offsets, rotation around the anchor, clip box with a 2px margin. No wrapping (as upstream). |
| HTML labels | Laid out by the shared `layout.LabelLayout`/`HtmlLabelParser` (the same layout the headless view uses to measure label bounds): safe formatting subset (`br`, block elements, `b`, `i`, `u`, `s`, `font color/size/face`, inline font styles), each run painted with its own font and color, greedy wrapping at spaces (never inside a word across style changes) at the box width when `wrap`, runs positioned with the advances of their painting fonts, line height `1.2 * max font size`, vertically centered baseline. Nothing in the markup is executed or fetched. Known gap: right-to-left text split over several styled runs is ordered run by run (single-run RTL lines are reordered correctly). |
| Font style | Bold, italic, underline and strike-through bits; CSS family lists resolve to the first installed font (same rule as `AwtTextMetrics`), else `SansSerif`. |
| Text background | `fontBackgroundColor` / `fontBorderColor` boxes around the text lines, or around the label box for `overflow=fill/width`. |
| Images | Only validated `data:` URIs reach the canvas (PNG, JPEG, GIF, BMP, base64). Byte and pixel limits are checked before decoding; ImageIO uses in-memory streams. `aspect` centers the image like `xMidYMid meet`; `flipH`/`flipV` mirror it inside the box. |
| `setLink`, `setTitle` | No visual effect; ignored. |

## Diagnostics instead of silent substitutes

| Capability id | Raised when |
| --- | --- |
| `canvas:fillStyle:<style>` | Sketch fill styles (`hachure`, `cross-hatch`, `dots`, ...). |
| `canvas:text-direction:vertical-*` | Vertical writing modes. |
| `canvas:text-format:<fmt>` | A label format other than plain or `html`. |
| `label:dom-node` | A label value that is a DOM node. |
| `image:external` | A non-`data:` image reference the policy cannot supply (missing resource). |
| `image:data-uri` | An embedded image that the policy forbids or that violates a limit (missing resource). |
| `image:svg` | Embedded SVG images (an SVG engine would need its own script/reference policy). |

Messages never contain diagram text or URLs.
