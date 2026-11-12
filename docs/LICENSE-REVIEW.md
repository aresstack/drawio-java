# License review for the first release (#15)

Review date: 2026-11-12. Scope: everything in the published jar, everything
the published POM pulls in, and the test assets in the repository.

## Summary

| Area | Status |
|---|---|
| draw.io JavaScript source (mxGraph, Graph.js parts, Shapes.js, mxBpmnShape2.js) | OK: Apache-2.0, LICENSE shipped in the jar, original headers kept |
| draw.io **stencil data** (`drawio-stencils.js`: arrows, basic, bpmn, flowchart) | **Release blocker for Atlassian-related use.** See below |
| GraalJS, Truffle, TRegex, graal-sdk | OK: UPL-1.0 (js also MIT), dependency only, not redistributed |
| ICU4J | OK: Unicode/ICU license, dependency only |
| Batik svggen, xmlgraphics-commons, commons-logging | OK: Apache-2.0, dependency only |
| Code adapted from mermaid-java | OK: MIT, notice in THIRD-PARTY-NOTICES.md |
| Corpus fixtures and reference images | OK: synthetic, CC0-1.0, no confidential data |
| Fonts | None bundled. Java2D uses the platform's fonts |
| drawio-java's own license | OK: MIT (decided by the owner on 2026-11-12), `LICENSE` in the repository and in the jar's `META-INF/` |

## 1. drawio-java's own license (resolved: MIT)

The owner chose the **MIT License** on 2026-11-12, the same as mermaid-java
(`Copyright (c) 2026 aresstack`). The text is in [`LICENSE`](../LICENSE), the
jar build copies it to `META-INF/LICENSE`, and the published POM declares
`MIT License` (https://opensource.org/licenses/MIT).

MIT is compatible with every dependency: the Apache-2.0 draw.io sources
keep their own license and headers (`drawio/upstream/LICENSE` in the jar),
and the UPL, ICU and Apache-2.0 dependencies are not relicensed. MIT applies
to drawio-java's own code only.

## 2. draw.io stencil libraries and the Atlassian restriction (blocker for Atlassian-related use)

The upstream README at the pinned commit `c72c9a98` says:

> The icon sets and stencil libraries included in this software, and any
> derivatives thereof (including conversions to other formats, traced
> reproductions, substantially similar visual representations, or
> AI-generated images created using these icons as reference or training
> input), may not be used as software assets in, distributed for use with,
> or incorporated into Atlassian products or products distributed through
> the Atlassian marketplace or plugin ecosystem, without explicit written
> permission.
>
> This restriction does not apply to end-user diagram output (such as
> exported images or documents) created using this software.

There is no upstream NOTICE file at that commit (checked: HTTP 404).

**What the jar contains today:** the production pipeline
(`OriginalGraphPipeline`) loads `drawio-stencils.js` by default. That file
holds the original encoded stencil sets `arrows.xml`, `basic.xml`,
`bpmn.xml` and `flowchart.xml`, so they are "stencil libraries" in the sense
of the restriction. They are generic notation sets, not vendor icon packs,
but the README makes no such distinction.

**What is unaffected:** the JavaScript shape implementations, including
`mxgraph.bpmn.gateway2`, `mxgraph.bpmn.event`, `mxgraph.bpmn.task` and the
core mxGraph shapes. These are source code under Apache-2.0. The BPMN
approval-flow corpus fixture (the Space Harvester case) renders through
those JavaScript shapes. Stencil data is only needed for shapes defined
purely as XML stencils, for example some BPMN markers and flowchart shapes.

**Assessment for the planned consumers:**

- *Enterprise AI Client:* not an Atlassian product and not distributed
  through the Marketplace. The restriction applies only if it is
  distributed "for use with" Atlassian products.
- *Space Harvester:* reads Confluence, an Atlassian product. Whether an
  independent tool that renders Confluence attachments counts as
  "distributed for use with" Atlassian products is a **legal question this
  review cannot settle**. The output exception covers the rendered images,
  not the stencil data shipped inside the tool.

**Recommendation (not implemented, owner decision):**

1. Move the stencil data into a separate optional artifact, for example
   `drawio-java-stencils`. The core jar would then contain only Apache-2.0
   code, and the renderer would report the stencil shapes as
   `UNSUPPORTED_STENCIL` when that artifact is absent. Consumers in an
   Atlassian context can then leave it out.
2. Until that split exists, or written permission is obtained, do not ship
   the current jar inside Space Harvester or anything Confluence- or
   Marketplace-related.

## 3. Runtime dependencies (published POM)

The published jar is a thin jar. It declares these dependencies and
redistributes none of their binaries. The NOTICE and attribution duties of
Apache-2.0 §4(d), UPL and ICU therefore fall on whoever builds a fat jar or
a distribution. THIRD-PARTY-NOTICES.md lists the licenses so that those
consumers can comply.

| Dependency | License | Note |
|---|---|---|
| org.graalvm.js:js 21.3.20.0.3 | UPL-1.0, MIT | The jar contains no LICENSE file. Ship the UPL text with a fat jar |
| org.graalvm.regex:regex, org.graalvm.truffle:truffle-api, org.graalvm.sdk:graal-sdk 21.3.20.0.3 | UPL-1.0 | Same as above |
| com.ibm.icu:icu4j 71.1 | Unicode/ICU license plus bundled data licenses | Keep the LICENSE inside the icu4j jar |
| org.apache.xmlgraphics:batik-svggen 1.20, batik-awt-util, batik-util, batik-constants, batik-i18n, batik-shared-resources | Apache-2.0 | Batik jars carry META-INF/NOTICE |
| org.apache.xmlgraphics:xmlgraphics-commons 2.12 | Apache-2.0 | |
| commons-logging:commons-logging 1.3.0 | Apache-2.0 | |

The newer Oracle `org.graalvm.polyglot:js` artifacts (23.1 to 24.2) are
licensed under the GraalVM Free Terms and Conditions, which is not an
open-source license. Use `js-community` (UPL/MIT) for any future migration.

## 4. What goes into the jar

- `drawio/upstream/LICENSE`: the Apache-2.0 text of draw.io (shipped today).
- `META-INF/LICENSE`: drawio-java's own MIT license.
- `META-INF/THIRD-PARTY-NOTICES.md` (see [RELEASE.md](RELEASE.md)).

## 5. Test-only material (not published)

- Batik transcoder and codec 1.20 and JUnit 5 (EPL-2.0) are test
  dependencies only.
- The corpus (`src/test/resources/drawio/corpus`) is synthetic and CC0. Its
  reference images are end-user output of the official viewer, which the
  upstream README exempts. The viewer itself is not checked in.
- `src/test/resources/drawio/test-upstream/` holds Apache-2.0 upstream
  sources with their original headers.
