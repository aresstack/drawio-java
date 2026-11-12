# Performance and resource control (#14)

Tracking: [Issue #14](https://github.com/aresstack/drawio-java/issues/14).

## What was measured

`./gradlew benchmark` runs `GraalJsBenchmark` (test sources) on JDK 8. Each
"request" creates a fresh `GraalJsRuntime` and context, loads the pinned
`drawio-model.js`, `drawio-shapes.js` and `drawio-canvas-adapter.js`
bundles (the first two are test-only and not shipped), builds an original `mxGraphModel` with N cells and paints every
cell through the original `mxgraph.bpmn.gateway2` code into a Java2D image.
That is the per-request work of the graph pipeline **without** the view,
layout and label steps, which were not on `main` when this was measured.
Re-run the benchmark once the full pipeline (#9) is integrated.

## Full pipeline through `DrawioRenderer` (2026-11-12)

The numbers below include everything a consumer pays for: parsing, the
original mxCodec, `Graph`, view validation with label measurement, shape
painting, recording and PNG plus SVG output. Measured with `./gradlew
renderFiles -PrenderArgs="outDir file..."` (one `DrawioRenderer`, files in
sequence, same JVM) on OpenJDK 1.8.0_512, 4 CPUs, interpreter-mode GraalJS
21.3.20.0.3, corpus fixtures:

| Request | Diagram | Time |
|---|---|---:|
| 1st (cold: loads and parses ~2.6 MB of bundled JavaScript) | `bpmn-approval-flow` (13 cells) | 2628 ms |
| 2nd | `bpmn-approval-flow` | 246 ms |
| 3rd | `bpmn-shapes-catalog` (~40 shapes) | 522 ms |
| later | `edges-arrows`, `groups-nested`, `labels-unicode`, `styles-basic-shapes` | 140–333 ms |
| 8th / 9th (warm) | `bpmn-approval-flow` / `bpmn-shapes-catalog` | 182 / 294 ms |

The integration probe ([INTEGRATION.md](INTEGRATION.md)) measured the same
order on JDK 21: first render about 2–3.4 s per JVM, later small diagrams
50–170 ms. Consumers should create one long-lived `DrawioRenderer` and, if
the first-request latency matters, render a small diagram at startup.
The section below is the earlier shape-only benchmark.

Options: `-Dbench.mode=shared|unshared` (one mode per JVM gives honest cold
starts), `-Dbench.cells=N`, `-Dbench.runs=N`, `-Dbench.threads=N`.

## Baseline (2026-11-12)

Environment: OpenJDK 1.8.0_512 (Ubuntu package, HotSpot, no Graal compiler,
so GraalJS runs in interpreter mode), Linux x86_64 container, 4 CPUs,
`-Xmx1g`, GraalJS 21.2.0 (re-run with 21.3.20.0.3 after the upgrade:
shared engine, 100 cells: cold 1438 ms, warm median 26 ms, 97 req/s). Absolute numbers depend on the machine; the
ratios are what matters.

| Mode | Cells | Cold first request | Warm median | Warm p90 | 4 threads |
|------|------:|-------------------:|------------:|---------:|----------:|
| engine per runtime (before) | 100 | 1317 ms | 68 ms | 114 ms | 39.9 req/s |
| shared engine (now) | 100 | 1531 ms | 37 ms | 90 ms | 87.1 req/s |
| engine per runtime (before) | 1000 | 1937 ms | 133 ms | 317 ms | 21.0 req/s |
| shared engine (now) | 1000 | 1809 ms | 147 ms | 174 ms | 23.0 req/s |

Heap used after a full GC at the end of each run: 4–7 MiB. Live threads
returned to the JVM baseline (5).

## Full production path (after #9 landed)

`./gradlew benchmark -Dbench.mode=facade -Dbench.cells=N` renders a chain
of N labelled rounded rectangles with N-1 orthogonal edges through the
public `DrawioRenderer`: parser, original `mxGraph`/`mxGraphView` pipeline
with the 1.5 MB `drawio-graph.js` bundle, recording, PNG or SVG. Same
machine, GraalJS 21.3.20.0.3, 4 workers.

| Engine | Vertices | Cold first | PNG warm median | SVG warm median | 4 workers PNG |
|--------|---------:|-----------:|----------------:|----------------:|--------------:|
| per runtime | 10 | 2474 ms | 173 ms | 153 ms | 12.9 req/s |
| shared | 10 | 2133 ms | 75 ms | 71 ms | 33.9 req/s |
| per runtime | 100 | 3294 ms | 582 ms | 452 ms | 4.7 req/s |
| shared | 100 | 3398 ms | 563 ms | 376 ms | 6.6 req/s |

Output sizes for 100 vertices: PNG 50 KiB, SVG 144 KiB. Heap after GC
10–24 MiB; no threads left behind.

With the real bundle the shared engine matters even more for typical
small diagrams (2.3x faster, 2.6x throughput). Large diagrams are
dominated by view validation and painting in interpreter-mode JavaScript;
running on a JVM with the Graal compiler (GraalVM JDK) would JIT-compile
the guest code, but that is a deployment choice, not a library change.
Profiling the 100-vertex case (how much is mxGraphView, label measurement,
bridge calls, the Java 8 Java2D lock) is the next step before any
optimization.

### Re-run with the full shape set (2026-11-12, main e8a288e)

After the production shape and stencil bundles (#10) landed. `-Dbench.diagram=bpmn`
makes every second vertex an original `mxgraph.bpmn.gateway2` (parallel,
exclusive, complex) with a label below; `grid` keeps rounded rectangles.

| Diagram | Engine | Vertices | Cold first | PNG warm median | SVG warm median | 4 workers PNG |
|---------|--------|---------:|-----------:|----------------:|----------------:|--------------:|
| grid | per runtime | 10 | 1961 ms | 176 ms | 176 ms | 9.6 req/s |
| grid | shared | 10 | 1938 ms | 69 ms | 61 ms | 40.8 req/s |
| grid | per runtime | 100 | 2835 ms | 512 ms | 429 ms | 4.6 req/s |
| grid | shared | 100 | 2837 ms | 423 ms | 299 ms | 8.3 req/s |
| bpmn | per runtime | 10 | 1975 ms | 191 ms | 171 ms | 10.4 req/s |
| bpmn | shared | 10 | 1931 ms | 85 ms | 73 ms | 38.1 req/s |
| bpmn | per runtime | 100 | 3057 ms | 566 ms | 420 ms | 4.4 req/s |
| bpmn | shared | 100 | 3002 ms | 506 ms | 326 ms | 7.2 req/s |

Output sizes (bpmn, 100 vertices): PNG 72 KiB, SVG 157 KiB. Heap after a
full GC: ~32 MiB with the shared engine (it keeps the parsed bundles) and
~11 MiB without; live threads back to the JVM baseline in every run.

The earlier conclusions hold with the full shape set: the shared engine
makes small diagrams 2.2–2.6x faster and quadruples parallel throughput;
for 100 vertices it still gives 1.6–1.8x throughput. The whole suite
(294 tests, including the official-viewer references and the golden
corpus) passes identically with `-Ddrawio.graaljs.sharedEngine=false`, so
the optimization does not change any golden output.

## Conclusions and changes

1. **Cold start** (~1.3–1.9 s) is dominated by loading GraalJS/Truffle
   classes and is the same in both modes. Consumers with interactive UIs
   should create one long-lived `DrawioRenderer` and may warm it up with a
   tiny diagram in the background at startup.
2. **Shared polyglot engine.** Re-parsing the bundles in every request was
   the largest avoidable per-request cost: sharing one `Engine` halves the
   warm time of a small diagram and doubles parallel throughput. All
   `GraalJsRuntime` contexts now use one process-wide engine
   (`SharedEngine`), and bundled scripts are loaded once and passed as
   cacheable `Source` objects. Isolation is unchanged: every request still
   gets its own `Context` (own globals, own heap objects), contexts are
   never used by two threads, and a forced close on timeout cancels only
   that context. `GraalJsRuntimeLifecycleTest` checks that globals and
   prototype pollution do not cross requests and that a timed-out context
   does not affect the next one. Inline (per-request) scripts are not
   marked cacheable, so they never accumulate in the engine. Set
   `-Ddrawio.graaljs.sharedEngine=false` to go back to one engine per
   runtime for diagnostics.
3. **Large diagrams** are dominated by painting (JS shape code plus
   bridge calls), not by setup. No command buffer or batching port was
   added: there is no measurement yet that isolates the bridge cost from
   the shape code, and #11's scene recorder already decouples JS from
   output. Revisit with a profiler when the full pipeline exists.
4. **Thread and leak control.**
   - `DrawioRenderer` uses a fixed number of daemon workers
     (default `min(4, CPUs)`) and a bounded queue (default 64); excess
     requests fail fast with `OVERLOADED` instead of piling up threads.
   - `GraalJsRuntime`'s single worker thread now ends after 5 s idle, so a
     runtime that a caller forgets to close no longer pins a thread for the
     JVM's lifetime. Closed runtimes stop their thread immediately.
   - Tests cover 200 sequential renders (every scene closed, worker count
     bounded), 80 parallel renders on 3 workers (peak concurrency never
     above 3), 90 sequential and parallel GraalJS runtimes (no runtime
     threads left), timeouts and `close()` during a running render.
5. **No promised SLA.** The numbers above are a baseline, not targets.

## Not done yet

- Re-measure with the real `mxGraph`/`mxGraphView` pipeline and golden
  corpus (#9, #13) and confirm that golden images are identical with the
  shared engine on and off.
- Heap ceiling per render: GraalJS 21.3 on a stock JDK has no per-context
  heap limit. The practical controls are the cell limit
  (`RenderLimits.maxCells`, checked before any JavaScript runs), the
  parser's decompression budget, output pixel limits and the worker count.
  Size the JVM heap for `maxConcurrentRenders` simultaneous large diagrams.
