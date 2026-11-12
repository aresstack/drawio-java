# GraalJS version decision (#12)

Decision recorded: 2026-11-12.

## Decision

Upgrade from `org.graalvm.js:js` / `org.graalvm.sdk:graal-sdk` **21.2.0**
to **21.3.20.0.3**, the newest release of the GraalVM 21.3 line on Maven
Central.

## Why

- **Java 8 is a hard requirement.** Checked class-file versions on Maven
  Central: every `js`, `truffle-api` and `graal-sdk` release up to and
  including 21.3.x and 22.0.0.x is Java 8 bytecode (major 52). From 22.1.0
  on they require Java 11 (major 55), and 23.x requires Java 17 (major 61).
- **21.2.0 is an unpatched feature release** (2021). 21.3 was the long-term
  support line and received patch releases for years; 21.3.20.0.3 carries
  those fixes while keeping the same API. 22.0.0.x is a short-lived
  feature release and was not chosen.
- **License unchanged:** the 21.3.20.0.3 POMs declare UPL 1.0 and MIT, like
  21.2.0. Transitive dependencies: `regex`, `truffle-api`, `graal-sdk`
  (same version) and `icu4j` 71.1.
- **Verified:** the full suite (145 tests at the time, including the
  official-viewer BPMN reference comparisons and the PNG/SVG output tests)
  passes on OpenJDK 8 with 21.3.20.0.3. The benchmark from
  [PERFORMANCE.md](PERFORMANCE.md) is slightly faster (warm median 26 ms vs
  37 ms for 100 cells, shared engine).

## Remaining risk

- The 21.3 line is old as well. On a stock JDK 8 GraalJS runs in
  interpreter mode; the JavaScript is the pinned, trusted draw.io code and
  diagram XML is never evaluated as script, which limits exposure.
- A full CVE review of GraalJS, Truffle, ICU4J and Batik is part of the
  release audit (#15).
- Consumers on Java 11+ could use a newer GraalJS, but that would need a
  separate build variant; not planned while Java 8 is required.
