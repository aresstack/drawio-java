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

## Original draw.io stencil data (opt-in subset)

`js-bundle/upstream/stencils.min.js` (build input, not in the JAR) and the
generated `src/main/resources/drawio/drawio-stencils.js` are taken unchanged
from `src/main/webapp/js/stencils.min.js` of
[jgraph/drawio](https://github.com/jgraph/drawio) at commit
`c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`. The generated file contains the
original decoder and the encoded stencil sets `arrows.xml`, `basic.xml`,
`bpmn.xml` and `flowchart.xml`. The upstream file carries no
per-file header; the repository's Apache License 2.0 applies (see
`src/main/resources/drawio/upstream/LICENSE`).

These are **stencil libraries** in the sense of the upstream README
restriction for products distributed for Atlassian or its Marketplace. Their
use is opt-in and they must be reviewed in the release license audit (#15)
before distribution; see [docs/STENCILS.md](docs/STENCILS.md).

Test-only copies of the original `mxStencil.js`, `mxStencilRegistry.js` and
`mxConnectionConstraint.js` (same commit, Apache-2.0, original headers) live in
`src/test/resources/drawio/test-upstream/` and are not packaged.
