# Security model and dependency audit (#15)

Audit date: 2026-11-12. This covers the state of `main` after the GraalJS
21.3.20.0.3 upgrade ([GRAALJS-VERSION.md](GRAALJS-VERSION.md)) and the Batik
1.20 upgrade.

## Trust model

| Input | Trust | Handling |
|---|---|---|
| Bundled draw.io JavaScript (`src/main/resources/drawio/*.js`) | Trusted | Pinned upstream commit, SHA-256 per source in `js-bundle/upstream.json`, verified in CI (`node js-bundle/build.mjs --verify`). Loaded only from the jar. |
| Diagram XML (`mxfile`, `mxGraphModel`) | **Untrusted data** | Parsed in Java by `DrawioDocumentParser` with DTD/XXE disabled, depth, element and page limits, and a shared 16 MiB inflate budget. It is handed to JavaScript as data and is **never evaluated as code**. |
| Images referenced by diagrams | Untrusted | `data:` URIs only when `ResourcePolicy` allows them, within byte and pixel limits. Other URLs only through a caller-supplied `ResourceResolver`. The renderer never opens a connection itself. |
| Caller-supplied JavaScript | **Not supported** | The runtime is not a sandbox for hostile scripts. Do not pass user JavaScript to `GraalJsRuntime`. |

### Guest restrictions (GraalJS context)

`GraalJsRuntime.createRestrictedContext()` builds every context with:

- `HostAccess.EXPLICIT`: only `@HostAccess.Export` methods of the bridge objects are callable.
- No host class lookup, no host class loading, no IO, no thread creation, no native access.
- No polyglot access, no environment access, no experimental options.
- stdout and stderr discarded.
- A separate context per evaluation, force-closed on timeout.

`GraalJsSecurityTest` pins the effect: `Polyglot` is undefined, and these
fail: `load()` of files or URLs, `Java.type(...)`, `Packages.java.*`,
reflection on bridge objects, `Java.addToClasspath`, and threads. The
GraalJS options that would remove `eval`, `load` and `print` altogether
(`js.disable-eval`, `js.load`, ...) are **experimental** in 21.3 and are
deliberately not used.

### Data is never evaluated as code

mxGraph has optional `eval` paths: function values in `mxCodec` `<add>`
elements, `edgeStyle` and `perimeter` style strings, and stencil
`<add>`/`<text>` content. All of them are off:

- `mxObjectCodec.allowEval = false` and `mxStylesheetCodec.allowEval = false` (set again in `drawio-headless.js`).
- `mxGraphView.prototype.allowEval = false`.
- `mxStencil.allowEval = false`.
- The stencil runtime port never calls `eval`.

The corpus fixture `negative/codec-eval-injection.drawio` places an
infinite loop in all three positions. It must render without timing out,
which shows the payload is never run. `negative/active-content-label.drawio`
puts `<script>`, `onerror` and `javascript:` into HTML labels. They must
render as text without executing or fetching anything.

### Resource exhaustion

Limits come from `RenderLimits`, `ResourcePolicy` and the parser defaults
in [XML-PARSING.md](XML-PARSING.md):

- input size, inflate budget, XML depth and element count;
- cell count, image bytes and pixels, output size;
- a wall-clock timeout per render, with forced context close;
- a bounded worker pool and queue (fails fast with `OVERLOADED`).

**Known limit:** GraalJS on a stock JDK cannot cap heap use per context.
Isolates exist only in Oracle GraalVM. A very large but valid diagram can
still use a lot of heap before the timeout fires. Callers that render
untrusted diagrams on a shared server should size the JVM heap and keep the
default limits.

## Runtime dependency audit

Resolved runtime classpath (from `gradlew dependencies`):

| Artifact | Version | License | Bytecode | Known vulnerabilities |
|---|---|---|---|---|
| org.graalvm.js:js | 21.3.20.0.3 | UPL-1.0 and MIT | Java 8 | None open. CVE-2022-21597 (GraalJS, CVSS 5.3) is fixed from 21.3.4. |
| org.graalvm.regex:regex | 21.3.20.0.3 | UPL-1.0 | Java 8 | None known |
| org.graalvm.truffle:truffle-api | 21.3.20.0.3 | UPL-1.0 | Java 8 | None known |
| org.graalvm.sdk:graal-sdk | 21.3.20.0.3 | UPL-1.0 | Java 8 | See "scanner false positives" below |
| com.ibm.icu:icu4j | 71.1 | Unicode/ICU license | Java 7 | None known. ICU CVEs such as CVE-2025-5222 are in ICU4C, not ICU4J. |
| org.apache.xmlgraphics:batik-svggen and its batik-* deps | 1.20 | Apache-2.0 | Java 8 | None known. 1.20 fixes the 2026 Batik CVEs (CVE-2026-40853, -44929, -49236, -89326, -90993, -111792) that affect 1.19 and earlier. |
| org.apache.xmlgraphics:xmlgraphics-commons | 2.12 | Apache-2.0 | Java 8 | None known |
| commons-logging:commons-logging | 1.3.0 | Apache-2.0 | Java 8 | None known |

The upgrade from Batik 1.17 to 1.20 also removes `commons-io` 2.11.0
(CVE-2024-47554, low) from the runtime classpath.

Test-only dependencies (JUnit 5.10.2, Batik transcoder and codec 1.20) are
not part of the published POM.

### Support status of GraalJS 21.3

- 21.3.x is the last GraalJS line with Java 8 bytecode. 22.1.0 dropped
  Java 8, 23.1 and later need Java 17, and 25.1 and later need Java 21.
- The 21.3 Maven artifacts follow the Oracle GraalVM Enterprise 21.3 line,
  which still gets quarterly Critical Patch Updates. 21.3.20.0.3 was
  published 2026-10-27. Oracle's calendar lists further 21.3 patches for
  2027-01 and 2027-04.
- No end-of-support date has been published.

**Recommendation:** stay on the newest 21.3.x while Java 8 is required, and
bump it after each Oracle CPU (January, April, July, October). Plan a 2.0
line on `org.graalvm.polyglot:polyglot` and `org.graalvm.polyglot:js-community`
(UPL/MIT, not GFTC) with a Java 17 or 21 baseline. Keep 1.x on 21.3 for
Java 8 consumers.

### Scanner false positives

Oracle files most "GraalVM Enterprise Edition" CVEs against JDK components:
Hotspot, JAXP, JSSE, 2D, Libraries and others. Scanners that match the
`graalvm` CPE then attribute them to `graal-sdk`. Examples are
CVE-2021-35567, CVE-2022-21449 and CVE-2024-21068. These bugs live in the
JDK the library runs on, not in the Maven jars. **Keep the consumer JDK
patched.**

GraalVM rows for the Graal JIT compiler, Node.js, the LLVM runtime, Native
Image and org.json do not apply either. None of those components is on the
classpath, and on a stock JDK GraalJS runs in interpreter mode.

### Continuous checks

- `dependency-scan` CI job: OSV-Scanner checks the resolved classpath on
  every change and every Monday. It writes a throwaway lockfile with
  `-PlockForScan`. The job is report-only because of the false positives
  above. Review its summary after each run.
- `GraalJsSecurityTest` and the corpus negative cases run on every build,
  on JDK 8, 11, 17 and 21 and on Linux and Windows.

## Reporting

Report suspected vulnerabilities privately to the repository owner (GitHub
security advisories on `aresstack/drawio-java`). Do not open a public
issue.
