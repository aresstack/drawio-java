# Headless text measurement (Issue #9 – focused integration slice)

## Architecture

Reuse the *approach*, not the whole Mermaid runtime: the upstream
`mermaid-java` `GraalJsExecutor.JavaBridge` measures text with
`FontMetrics` and resolves CSS font-family lists to locally available
AWT fonts. In drawio-java, keep the responsibilities separate:

- `TextMetrics`: a domain-facing, UI-independent measurement port.
- `FontDescriptor`: immutable family, pixel size and bold/italic flags.
- `TextBounds`: immutable width, ascent, descent and total line height.
- `AwtTextMetrics`: Java2D implementation with a fresh, offscreen
  `Graphics2D` per call, using only local fonts.
- `DrawioHost.measureText`: an explicitly exported GraalJS method,
  returning a plain `width,ascent,descent,height` value instead of an
  unrestricted Java `Font` or `Graphics2D` object.

The bridge capability is **optional**: a host without a configured
`TextMetrics` service fails explicitly, rather than guessing bounds.
Existing host constructors remain source-compatible.

## Example

```java
TextMetrics metrics = new AwtTextMetrics();
TextBounds bounds = metrics.measure(
        "Grüße",
        new FontDescriptor("Trebuchet MS, sans-serif", 16, false, false));
System.out.println(bounds.getWidth());
```

The GraalJS host can call:

```javascript
drawioHost.measureText("Grüße", "sans-serif", 16, false, false)
```

No DOM, network, browser, Swing window or Node.js process is required.

## Current behavior and limits

- Use `FontMetrics.stringWidth` for each line, retain the maximum width,
  and sum line heights for `\n`, `\r` and `\r\n`.
- Resolve the first available CSS family from a comma-separated list;
  use `SansSerif` when no candidate is installed.
- Translate generic CSS `sans-serif`, `serif` and `monospace` to the
  corresponding Java logical fonts.
- Validate finite font sizes in `(0, 512]` pixels, family strings of
  at most 256 characters and texts of at most 10,000 characters.
- Allocate and dispose a fresh 1x1 offscreen graphics context for every
  call; do not share mutable AWT graphics across renderer threads.
- Empty text yields zero bounds. All other bounds are nonnegative.

**This is not CSS or HTML text layout.** The service currently does
not implement Unicode line breaking, complex-script shaping parity,
`<foreignObject>` or HTML labels, text-anchor, bounding boxes for
transformed groups, CSS white-space/overflow or full Draw.io font
settings. Those belong to later #9 slices and #10, and require
visual comparison against the pinned official viewer.

**Platform caveat:** Different OS installations may select different
fallback fonts and produce different widths. Visual golden tests
must control installed fonts or tolerate documented font variance.

## Regression tests

`AwtTextMetricsTest`: six tests for deterministic in-process metrics,
CSS fallback, multilines, Unicode, empty text and bounds validation.

`DrawioTextMetricsBridgeTest`: three tests for approved GraalJS
measurement, an unavailable capability and no Java class/Node access.

Keep the overall #9 issue open until actual XML-to-model-to-view
rendering, connected edges and labels are implemented.
