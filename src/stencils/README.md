# drawio-java-stencils

Optional draw.io XML stencil data for
[drawio-java](https://github.com/aresstack/drawio-java). This artifact has no
Java API, so this javadoc jar holds documentation only.

Add it next to `drawio-java`, in the same version, when diagrams use the XML
stencil sets `mxgraph.arrows.*`, `mxgraph.basic.*` (XML part), `mxgraph.bpmn.*`
(legacy symbols and task markers) or `mxgraph.flowchart.*`. drawio-java picks
it up from the classpath; no configuration is needed.

Read `META-INF/THIRD-PARTY-NOTICES.md` before you ship it: the upstream
draw.io README restricts these stencil libraries for Atlassian products and
the Atlassian Marketplace.
