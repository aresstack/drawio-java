# drawio-java

Headless Draw.io diagram rendering for Java. The planned renderer executes the
original Draw.io shape and layout logic with GraalJS and delegates drawing to
Java2D. No browser or Node.js runtime should be required by consumers.

> **Status: bootstrap only.** This repository does not render diagrams yet.
> The architecture and implementation milestones are tracked in
> [Epic #1](https://github.com/aresstack/drawio-java/issues/1).

## Scope

- Keep the public Java API independent of GraalJS, Swing/JavaFX, Confluence and network access.
- Reuse verified ideas and small components from
  [mermaid-java](https://github.com/aresstack/mermaid-java) and
  [playwright4j](https://github.com/aresstack/playwright4j) without importing either as a runtime dependency.
- Target Java 8 for the library API and runtime compatibility.
- Render with the original Draw.io JavaScript shape/stencil logic, eventually via
  a JavaScript canvas adapter and a restricted Java host bridge.
- Provide PNG/BufferedImage and SVG outputs through the same Java2D drawing model.

See [REUSE.md](REUSE.md) for the detailed component inventory and decisions.

## Development

JDK 8 and a Gradle 8.12 wrapper are used. Java 8 toolchains must be available.

On Windows:

```powershell
.\gradlew.bat clean test jar
```

On Linux/macOS:

```bash ./gradlew clean test jar
```

The current build intentionally does **not** depend on a GraalJS runtime: choosing
a compatible pinned version and implementing restricted host access belong to
[Issue #3](https://github.com/aresstack/drawio-java/issues/3).

## Roadmap

1. [#2 Bootstrap and reuse inventory](https://github.com/aresstack/drawio-java/issues/2)
2. [#3 Restricted GraalJS runtime](https://github.com/aresstack/drawio-java/issues/3)
3. [#4 Pinned Draw.io JavaScript bundle](https://github.com/aresstack/drawio-java/issues/4)
4. [#5 Java2D canvas](https://github.com/aresstack/drawio-java/issues/5)
5. [#6 JS canvas bridge](https://github.com/aresstack/drawio-java/issues/6)
6. [#7 Original BPMN `gateway2` end-to-end proof](https://github.com/aresstack/drawio-java/issues/7)

Follow the [epic checklist](https://github.com/aresstack/drawio-java/issues/1)
for subsequent full-diagram rendering, tests, release and integrations.
