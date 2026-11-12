# Performance and resource control (#14)

Tracking: [Issue #14](https://github.com/aresstack/drawio-java/issues/14).

## What was measured

`./gradlew benchmark` runs `GraalJsBenchmark` (test sources) on JDK 8. Each
"request" creates a fresh `GraalJsRuntime` and context, loads the pinned
`drawio-model.js`, `drawio-shapes.js` and `drawio-canvas-adapter.js`
bundles, builds an original `mxGraphModel` with N cells and paints every
cell through the original `mxgraph.bpmn.gateway2` code into a Java2D image.
That is the per-request work of the graph pipeline **without** the view,
layout and label steps, which were not on `main` when this was measured.
Re-run the benchmark once the full pipeline (#9) is integrated.

Options: `-Dbench.mode=shared|unshared` (one mode per JVM gives honest cold
starts), `-Dbench.cells=N`, `-Dbench.runs=N`, `-Dbench.threads=N`.

## Baseline (2026-11-12)

Environment: OpenJDK 1.8.0_512 (Ubuntu package, HotSpot, no Graal compiler,
so GraalJS runs in interpreter mode), Linux x86_64 container, 4 CPUs,
`-Xmx1g`, GraalJS 21.2.0. Absolute numbers depend on the machine; the
ratios are what matters.

| Mode | Cells | Cold first request | Warm median | Warm p90 | 4 threads |
|------|------:|-------------------:|------------:|---------:|----------:|
| engine per runtime (before) | 100 | 1317 ms | 68 ms | 114 ms | 39.9 req/s |
| shared engine (now) | 100 | 1531 ms | 37 ms | 90 ms | 87.1 req/s |
| engine per runtime (before) | 1000 | 1937 ms | 133 ms | 317 ms | 21.0 req/s |
| shared engine (now) | 1000 | 1809 ms | 147 ms | 174 ms | 23.0 req/s |

Heap used after a full GC at the end of each run: 4–7 MiB. Live threads
returned to the JVM baseline (5).

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
- Heap ceiling per render: GraalJS 21.2 on a stock JDK has no per-context
  heap limit. The practical controls are the cell limit
  (`RenderLimits.maxCells`, checked before any JavaScript runs), the
  parser's decompression budget, output pixel limits and the worker count.
  Size the JVM heap for `maxConcurrentRenders` simultaneous large diagrams.
