# drawio-java-stencils: third-party notice

This artifact contains only `drawio/drawio-stencils.js`: the original draw.io
stencil decoder and the encoded stencil sets `arrows.xml`, `basic.xml`,
`bpmn.xml` and `flowchart.xml`. The file is taken unchanged, line by line,
from `src/main/webapp/js/stencils.min.js` of
[jgraph/drawio](https://github.com/jgraph/drawio) at commit
`c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`. Only a provenance comment was
added at the top.

## License

draw.io is licensed under the Apache License, Version 2.0. The full text is
in `META-INF/LICENSE` of this jar. The upstream file has no per-file header
and the upstream repository has no NOTICE file at that commit.

## Additional upstream restriction

The upstream README at that commit states:

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

The stencil sets in this artifact are stencil libraries in the sense of that
restriction. **Do not ship this artifact in, or for use with, Atlassian
products or products distributed through the Atlassian Marketplace or plugin
ecosystem unless you hold written permission.**

## Without this artifact

`drawio-java` works without this jar. It loads the stencil data only when
`drawio/drawio-stencils.js` is on the classpath. Without it, every shape that
needs one of these XML stencils (for example `mxgraph.flowchart.decision`,
`mxgraph.bpmn.gateway_and`, or the BPMN task markers service, user, manual,
business rule, script, standard loop, compensation and ad hoc) is reported as
`stencil:<name>`. With the default `UnsupportedFeaturePolicy.FAIL` the render
fails with `UNSUPPORTED_STENCIL`; with `WARN` it returns a warning. The
JavaScript shapes in `drawio-java` itself, such as `mxgraph.bpmn.gateway2`,
`mxgraph.bpmn.event`, `mxgraph.bpmn.task` without these markers and the
standard draw.io shapes, need no stencil data.

Use the same version of `drawio-java` and `drawio-java-stencils`.
