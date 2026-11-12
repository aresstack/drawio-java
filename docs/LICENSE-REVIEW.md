# License review for the first release (#15)

Review date: 2026-11-12. Scope: everything in the published jar, everything
the published POM pulls in, and the test assets in the repository.

## Summary

| Area | Status |
|---|---|
| draw.io JavaScript source (mxGraph, Graph.js parts, Shapes.js, mxBpmnShape2.js) | OK: Apache-2.0, LICENSE shipped in the jar, original headers kept |
| draw.io **stencil data** (`drawio-stencils.js`: arrows, basic, bpmn, flowchart) | **Split out.** Not in the `drawio-java` jar; published as the optional `drawio-java-stencils` artifact, which carries the upstream Atlassian restriction. Leave it out for Atlassian-related use. See below |
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

## 2. draw.io stencil libraries and the Atlassian restriction (resolved by the optional stencil artifact)

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

**What is affected:** `drawio-stencils.js` holds the original encoded
stencil sets `arrows.xml`, `basic.xml`, `bpmn.xml` and `flowchart.xml`, so
they are "stencil libraries" in the sense of the restriction. They are
generic notation sets, not vendor icon packs, but the README makes no such
distinction. The production pipeline (`OriginalGraphPipeline`) loads the
file whenever it is on the classpath.

**Where it is now:** the `drawio-java` jar and its sources jar no longer
contain `drawio/drawio-stencils.js`. The file is published only in the
separate artifact `com.aresstack:drawio-java-stencils` (same version), whose
POM description and `META-INF/THIRD-PARTY-NOTICES.md` state the upstream
restriction, and whose `META-INF/LICENSE` is draw.io's Apache-2.0 text. The
build checks the split (`verifyStencilArtifact`), and the offline consumer
smoke test runs once without and once with the stencil jar. Details and the
steps to undo the split are in [STENCILS.md](STENCILS.md#optional-artifact-drawio-java-stencils).

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

**Recommendation:**

1. **Implemented.** Move the stencil data into a separate optional artifact,
   `drawio-java-stencils`. The core jar now contains no draw.io stencil
   data, and the renderer reports the stencil shapes as
   `stencil:<name>` (`UNSUPPORTED_STENCIL` under the default fail policy)
   when that artifact is absent. Verified by `StencilArtifactAbsentTest` and
   `consumerSmokeTest` (main jar only), while `mxgraph.bpmn.gateway2` and
   the standard shapes still render. Consumers in an Atlassian context can
   leave the artifact out.
2. Ship only `drawio-java`, without `drawio-java-stencils`, inside Space
   Harvester or anything Confluence- or Marketplace-related, unless written
   permission is obtained. What such a consumer loses: the legacy BPMN
   stencil symbols (`mxgraph.bpmn.gateway_and`, `mxgraph.bpmn.timer_start`,
   ...), the BPMN task markers drawn from stencils (task types service,
   user, manual, business rule and script; standard loop, compensation and
   ad hoc), and the `mxgraph.flowchart.*`, `mxgraph.arrows.*` and XML
   `mxgraph.basic.*` stencils. The BPMN 2 sidebar shapes (`gateway2`,
   `event`, `task2`, `data2`, ...) are JavaScript and keep working apart
   from those markers.

The split is reversible without code changes, because loading is driven by
the classpath. If written permission is obtained or the restriction is
judged irrelevant, fold the data back into the main jar as described in
[STENCILS.md](STENCILS.md#folding-the-stencils-back-into-the-main-jar).

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

## 4. What goes into the jars

`drawio-java`:

- `drawio/upstream/LICENSE`: the Apache-2.0 text of draw.io (shipped today).
- `META-INF/LICENSE`: drawio-java's own MIT license.
- `META-INF/THIRD-PARTY-NOTICES.md` (see [RELEASE.md](RELEASE.md)).
- Not `drawio/drawio-stencils.js` (see section 2).

`drawio-java-stencils` (optional):

- `drawio/drawio-stencils.js`: the draw.io stencil data.
- `META-INF/LICENSE`: draw.io's Apache-2.0 text.
- `META-INF/THIRD-PARTY-NOTICES.md`: provenance and the upstream Atlassian
  restriction (source: `src/stencils/THIRD-PARTY-NOTICES.md`).
- Its POM declares Apache-2.0, with the restriction in the license
  `comments` and in the description. Its sources jar holds the same file and
  notices; its javadoc jar holds only `src/stencils/README.md` and the
  notice, because there is no Java API.

## 5. Test-only material (not published)

- Batik transcoder and codec 1.20 and JUnit 5 (EPL-2.0) are test
  dependencies only.
- The corpus (`src/test/resources/drawio/corpus`) is synthetic and CC0. Its
  reference images are end-user output of the official viewer, which the
  upstream README exempts. The viewer itself is not checked in.
- `src/test/resources/drawio/test-upstream/` holds Apache-2.0 upstream
  sources with their original headers.
