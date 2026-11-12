# Third-party notices (bootstrap)

## Code adapted from mermaid-java

The internal `com.aresstack.drawio.graal.ScriptExecutionResult` derives its
immutable success/failure value-object design from `JsExecutionResult` in
[aresstack/mermaid-java](https://github.com/aresstack/mermaid-java), MIT license.

Copyright (c) 2026 aresstack

MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Architectural reference: Playwright4J

[aresstack/playwright4j](https://github.com/aresstack/playwright4j) is
licensed under Apache License 2.0. Its restricted GraalJS-host pattern is a
reference only; no Playwright4J implementation source has been copied at
bootstrap time.

## Original Draw.io JavaScript sources

The two vendored JavaScript source files and their deterministic combined bundle
are derived from [jgraph/drawio](https://github.com/jgraph/drawio), commit
`c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`.

Original source copyrights remain in their respective headers. The original
repository's Apache License 2.0 is included at
`src/main/resources/drawio/upstream/LICENSE`.

The upstream README imposes additional restrictions on icon sets, stencil
libraries and templates (including certain Atlassian-related distributions).
The bundled source selection currently excludes the stencil/icon/template
assets; see [UPSTREAM.md](UPSTREAM.md) for provenance, legal cautions and
unsupported resources. Reassess the license implications before adding
further upstream packages or publishing an Atlassian-related integration (#15).

## Apache Batik SVG generator (runtime dependency)

`org.apache.xmlgraphics:batik-svggen:1.17` and its transitive dependencies
(`batik-awt-util`, `batik-util`, `batik-constants`, `batik-i18n`,
`batik-shared-resources`, `xmlgraphics-commons` 2.9, `commons-io` 2.11.0,
`commons-logging` 1.0.4) are used for SVG output (#11). All are licensed under
the Apache License 2.0; they are resolved from Maven Central and not
redistributed in this repository. Batik 1.17 includes the fixes for
CVE-2022-44729 and CVE-2022-44730. `BoundsGraphics2D` extends Batik's
`AbstractGraphics2D`; no Batik source code is copied.

