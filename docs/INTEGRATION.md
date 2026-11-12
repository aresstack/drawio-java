# Integration runbook: Enterprise AI Client and Space Harvester (#16)

Tracking: [Issue #16](https://github.com/aresstack/drawio-java/issues/16).

This is a step-by-step plan for replacing JGraphX with drawio-java in both
consumers. **No consumer code has been changed yet.** Everything below was
checked against:

| Repository | Commit (`origin/main`) |
|---|---|
| enterprise-ai-client (EAC) | `fb85f38` (merge of #219) |
| space-harvester (SH) | `5f8c5e5` (merge of #683; `enterprise-ai-sources` pinned to EAC `fb85f380`) |
| drawio-java | `e44eb1f` for the probes; the stencil split and the fixes in section 6 landed on `main` after it |

Statements marked **(probed)** were checked by compiling the adapter classes
below against a jar built from drawio-java `e44eb1f` and running them, once
with and once without `drawio/drawio-stencils.js` on the classpath.
Everything marked **Recommendation** is a choice, not a fact.

## 0. Prerequisites

1. **A drawio-java release on Maven Central** (#15). Both consumers resolve
   only from Maven Central: `settings.gradle` has
   `RepositoriesMode.FAIL_ON_PROJECT_REPOS` and only `mavenCentral()`.
   The version is written as `0.1.0` below. Replace it with the version
   that was actually published.
2. **The stencil split (done on `main` since `e55f7d4`).** The stencil data
   is a separate optional artifact, `com.aresstack:drawio-java-stencils`,
   with the same version. The reason is the upstream draw.io restriction on
   using stencil libraries with Atlassian products
   ([LICENSE-REVIEW.md](LICENSE-REVIEW.md) §2, [STENCILS.md](STENCILS.md)).
   The `drawio-java` jar no longer contains `drawio/drawio-stencils.js`;
   `OriginalGraphPipeline` loads it only when the stencil jar is on the
   classpath (`OPTIONAL_SHAPE_BUNDLES`, `availableBundles`), and the build
   checks this (`verifyStencilArtifact`, two consumer smoke runs). **Ship
   drawio-java in a consumer only from a release built from `e55f7d4` or
   later.** Section 2.1 explains what changes without the stencil jar.
3. **Trying it before the release (optional).** Run
   `./gradlew build --include-build /path/to/drawio-java` in the consumer.
   Gradle then substitutes `com.aresstack:drawio-java` with the local
   project, and `settings.gradle` stays unchanged. drawio-java's own build
   needs a JDK 8 toolchain. Take a checkout of `e55f7d4` or later, so the
   substituted jar has no stencil data, like the release.

## 1. What exists today

### Enterprise AI Client: `proasteion/emporion/holkas/source-confluence`

`src/main/java/com/aresstack/enterpriseai/proasteion/emporion/holkas/confluence/DrawioDiagramRenderer.java`
is package-private and uses JGraphX `com.github.vlsi.mxgraph:jgraphx:4.2.2`.

| Member | Used by (verified with `git grep`) |
|---|---|
| `static String toSvg(String xml) throws Unsupported` | `ConfluenceRestClient.drawioSvg` (line 934), `DrawioDiagramRendererTest` |
| `static final class Unsupported extends Exception`, ctor `Unsupported(String)` | `ConfluenceRestClient.viewImage` (catch, line 866), `drawioSvg` (`throws`, and `new Unsupported(...)` at lines 902 and 909) |
| `parse`, `normalize`, `plainText`, `blockArrowPath`, `interfacePaths`, `BLOCK_ARROW`, `INTERFACE` | only `DrawioDiagramRendererTest` (`blockArrowPath`, `plainText`). No main code |
| private `TolerantGraph`, `DrawioSvgCanvas` (nested classes) | the class itself |

`ConfluencePageView` mentions the renderer only in Javadoc. It builds the
`#drawio=<name>&page=<id>&revision=<n>` fragment that
`ConfluenceRestClient.viewImage` reads. The call chain stays as it is:
`viewImage` → `drawioSvg` (authorized `GET /rest/drawio/1.0/diagram/crud/...`,
reads `xml` from the JSON) → `toSvg`. On `Unsupported` or
`KnowledgeSourceException` it logs and falls back to `previewImage`.
A `TwoFactorRequired` thrown by that fallback is re-thrown with its type
intact.

### Space Harvester

| Module | File | Role |
|---|---|---|
| `enterprise-ai-sources` | `src/main/java/.../confluence/DrawioDiagramRenderer.java` and the whole EAC module | Byte-identical copy of EAC. `verifyUpstreamCopy` (part of `check`) fails on any local edit. `build.gradle` belongs to SH and declares `implementation libs.jgraphx` |
| `document-export` | `src/main/java/com/aresstack/spaceharvester/document/DrawioDiagramRenderer.java` | A **second** JGraphX renderer: a public copy of the EAC class (`public` class, `public static String toSvg(String)`, `public static final class Unsupported` with a package-private ctor). `parse`, `graphModel` and `plainText` are package-private for `DrawioGraph` |
| `document-export` | `src/main/java/com/aresstack/spaceharvester/document/DrawioGraph.java` | **Not a rendering helper.** It extracts nodes, edges and text for AI use (ADR 0018) through `DrawioDiagramRenderer.parse/graphModel/plainText`. **It stays** |

There is no separate `DrawioSvgCanvas` file in either repository.
`DrawioSvgCanvas` and `TolerantGraph` are private nested classes and go away
with the rewrite.

Callers of the `document-export` classes (verified):

- `space-harvester-core/.../application/diagrams/Diagrams.java`:
  `drawio(byte[])` calls `DrawioDiagramRenderer.toSvg(new String(mxfile, UTF_8))`
  and turns `Unsupported` into `Rendering.notDrawn("draw.io nicht darstellbar: " + msg)`.
  `drawioPng(byte[])` rasterizes that SVG with `SvgImageRenderer` at
  width 1200. Both are called from `Assets` (`DRAWIO_RENDITION`,
  `DRAWIO_PNG_RENDITION`) and `ExportChapters`.
- `space-harvester-core/.../output/convert/JsonPackageConverter.java`:
  `graph(...)` calls `DrawioGraph.of(mxfile)` and catches
  `DrawioDiagramRenderer.Unsupported | RuntimeException` (writes `graphProblem`).

No module outside these uses `libs.jgraphx`. The only uses are
`source-confluence/build.gradle` (EAC), and `enterprise-ai-sources/build.gradle`
and `document-export/build.gradle` (SH).

## 2. Decisions to make before starting

### 2.1 Stencil artifact (owner's legal call)

Without `drawio-java-stencils`, every **XML stencil** fails with
`UNSUPPORTED_STENCIL` and capability `stencil:<name>`. The adapter turns that
into `Unsupported`, so the existing fallback applies: in EAC the plugin's
authorized PNG preview, in SH the stored preview or "nicht darstellbar".
**JavaScript shapes keep rendering without it.**

| Without the stencil jar (probed) | Result |
|---|---|
| `mxgraph.bpmn.gateway2`, `mxgraph.bpmn.event`, `mxgraph.bpmn.shape` (JS) | renders. The `bpmn-approval-flow` corpus fixture renders completely |
| `mxgraph.arrows2.arrow`, `mxgraph.basic.rect`, Shapes.js (`process`, `note`, `ext`, `cylinder3`, `umlActor`, `providedRequiredInterface`), ER and flowchart JS shapes | renders |
| XML sets `bpmn.xml` (for example `mxgraph.bpmn.user_task`), `flowchart.xml` (`mxgraph.flowchart.decision`), `basic.xml` (`mxgraph.basic.star`), `arrows.xml` | `nicht unterstützt: stencil:mxgraph.…` → fallback |
| Sets that are never bundled (AWS, Azure, ...) | `stencil:…` → fallback, with or without the jar |

This is not a regression against today: JGraphX rejects every `mxgraph.*`
shape except the two that are hand-drawn (`arrows2.arrow` and
`providedRequiredInterface`).

**Recommendation:**

- **EAC:** leave `drawio-java-stencils` out by default. EAC renders
  Confluence content, so it is used in an Atlassian context. Document the
  trade-off: XML-stencil shapes show the plugin's PNG preview instead of a
  drawing.
- **SH:** also leave it out. SH harvests Confluence. LICENSE-REVIEW §2
  treats it as the more exposed case.
- If the owner decides to include it, add one line per consumer
  (`runtimeOnly libs.drawio.stencils`, catalog entry in section 3) and
  delete the test `anXmlStencilNeedsTheOptionalStencilArtifact`.

drawio-java's own license is MIT (`LICENSE`, POM). The draw.io JavaScript in
the core jar is Apache-2.0 and keeps its headers. GraalJS is UPL/MIT, Batik
is Apache-2.0.

### 2.2 GraalJS version

- drawio-java: `org.graalvm.sdk:graal-sdk` and `org.graalvm.js:js`
  **21.3.20.0.3** (`build.gradle`, [GRAALJS-VERSION.md](GRAALJS-VERSION.md)).
  This is the last Java 8 line.
- Consumers today: `mermaid-java` 0.2.0-beta.2 (its `main` declares 21.2.0)
  and `win-proxy-java` 0.2.0 (catalog comment: "bringt GraalJS 21.2 mit").
- Gradle's conflict resolution picks **21.3.20.0.3** for `js`, `regex`,
  `truffle-api` and `graal-sdk`, and `icu4j` 71.1 instead of 69.1. The 21.x
  polyglot API is the same. **Do not exclude or pin GraalJS modules back to
  21.2:** mixed Truffle versions fail at runtime. Run the Mermaid and proxy
  (PAC) tests after the change.
- drawio-java turns off the interpreter-only warning on its own engine
  (`engine.WarnInterpreterOnly=false`), as mermaid-java does.

### 2.3 Batik version (new; the old plan said 1.17)

drawio-java depends on `batik-svggen` **1.20** (runtime). mermaid-java's
`main` declares `batik-bridge`, `batik-transcoder` and `batik-codec` **1.17**.
Gradle upgrades only the modules that both sides request (`batik-svggen`,
`batik-awt-util`, `batik-util`, `batik-shared-resources`, ...). The rest
stays at 1.17, so the classpath mixes Batik 1.17 and 1.20.

**Recommendation:** align all Batik modules to 1.20. drawio-java's tests use
transcoder and codec 1.20, so 1.20 is known to work for its output. Add the
rule to EAC's root `build.gradle` (inside `subprojects { }`) and to SH's root
`build.gradle` (inside `allprojects { }`):

```groovy
    configurations.configureEach {
        resolutionStrategy.eachDependency { details ->
            if (details.requested.group == 'org.apache.xmlgraphics' && details.requested.name.startsWith('batik-')) {
                details.useVersion '1.20'
                details.because 'drawio-java needs Batik 1.20; keep one Batik line on the classpath'
            }
        }
    }
```

Check the result with
`./gradlew :proasteion:exedra:dependencies --configuration runtimeClasspath | grep batik`
(EAC) and `./gradlew :space-harvester-app:dependencies --configuration runtimeClasspath | grep batik`
(SH). Then run the Mermaid and SVG image tests (`OfflineRenderersTest`,
`SvgDrawingTest` in `document-export`).

### 2.4 SVG or PNG

**Recommendation: keep SVG.** `viewImage` and `Diagrams.drawio` return SVG
today, and both readers rasterize it with Batik. That keeps zoom quality and
changes no caller. drawio-java's SVG is a standalone document (no DOCTYPE,
no external references, `<text>` labels). SH's `SvgImageRenderer`
sanitizer keeps `data:image/` references, so embedded images survive.

Optional later step for SH: `Diagrams.drawioPng` could call drawio-java's
`renderPng(xml, options.toBuilder().fitWidth(1200).build())` instead of the
SVG → Batik round trip. The text would then be laid out and painted with the
same Java2D metrics. Not part of this runbook.

## 3. Work order

1. **EAC PR:** adapter, Gradle, tests, docs (section 4).
2. **SH PR A:** re-sync `enterprise-ai-sources` to the EAC merge commit
   (section 5.1).
3. **SH PR B:** `document-export` (section 5.2), then remove `jgraphx` from
   SH's catalog.

PRs A and B can be one PR, but A must not contain hand edits under
`enterprise-ai-sources/src` (`verifyUpstreamCopy`).

## 4. Enterprise AI Client

### 4.1 Gradle

`gradle/libs.versions.toml`:

```toml
# [versions]: remove
jgraphx = "4.2.2"
# [versions]: add
drawioJava = "0.1.0"

# [libraries]: replace the two lines
#   # source-confluence: draw.io-Diagramme (mxGraphModel) ohne Browser als SVG (JGraphX, BSD, Java-7-Bytecode).
#   jgraphx = { module = "com.github.vlsi.mxgraph:jgraphx", version.ref = "jgraphx" }
# with
# source-confluence: draw.io-Diagramme ohne Browser als SVG (drawio-java: Original-draw.io-JavaScript auf
# GraalJS 21.3, Java2D/Batik 1.20; MIT, Java-8-Bytecode). Ohne drawio-java-stencils, siehe Entscheidung #16.
drawio-java = { module = "com.aresstack:drawio-java", version.ref = "drawioJava" }
# Only if the owner decides to ship the stencil data (section 2.1):
# drawio-stencils = { module = "com.aresstack:drawio-java-stencils", version.ref = "drawioJava" }
```

`proasteion/emporion/holkas/source-confluence/build.gradle`, replace

```groovy
    // draw.io-Diagramme als SVG (DrawioDiagramRenderer); das SVG rastert der Lesebereich über Batik.
    implementation libs.jgraphx
```

with

```groovy
    // draw.io-Diagramme als SVG (DrawioDiagramRenderer über drawio-java); das SVG rastert der Lesebereich über Batik.
    implementation libs.drawio.java
```

Also apply the Batik rule from section 2.3 to the root `build.gradle`.

### 4.2 Code

Replace the **whole** file
`proasteion/emporion/holkas/source-confluence/src/main/java/com/aresstack/enterpriseai/proasteion/emporion/holkas/confluence/DrawioDiagramRenderer.java`
with the source below. It compiles with `--release 8` (probed). It keeps
`toSvg(String)` and `Unsupported(String)`, so `ConfluenceRestClient` still
compiles unchanged, including its own two `new Unsupported(...)` calls. Every
JGraphX import, `normalize`, `plainText`, `blockArrowPath`,
`interfacePaths`, `BLOCK_ARROW`, `INTERFACE`, `TolerantGraph` and
`DrawioSvgCanvas` are deleted.

```java
package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.PageSelector;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;

import java.time.Duration;

/**
 * Zeichnet ein draw.io-Diagramm ({@code mxfile}-XML aus {@code /rest/drawio/1.0/diagram/crud/…}) ohne Browser mit
 * drawio-java (Original-draw.io-JavaScript auf GraalJS, gezeichnet mit Java2D) als SVG; das SVG rastert der
 * Lesebereich über denselben Batik-Weg wie Mermaid.
 *
 * <p>Was drawio-java nicht originalgetreu zeichnen kann (unbekannte Formen und Schablonen, Bilder, die nicht als
 * {@code data:}-URI eingebettet sind, nicht lesbare Daten, Zeit- und Größengrenzen), macht das Diagramm „nicht
 * vollständig darstellbar“ ({@link Unsupported}); dann zeigt der Client die PNG-Vorschau des Plugins. Der Renderer
 * ruft nie eine Adresse ab. Die Meldung nennt nur Fehlercode und Fähigkeit (etwa
 * {@code stencil:mxgraph.aws4.lambda}), nie Beschriftungen, IDs oder XML.</p>
 */
final class DrawioDiagramRenderer {

    /** Das Diagramm ist nicht vollständig darstellbar; die Meldung nennt nur Code und Fähigkeit, keine Inhalte. */
    static final class Unsupported extends Exception {
        private static final long serialVersionUID = 1L;

        Unsupported(String message) {
            super(message);
        }
    }

    /** Erste Seite, nur eingebettete Bilder, nichts stillschweigend weglassen, höchstens 20 s je Diagramm. */
    private static final RenderOptions OPTIONS = RenderOptions.builder()
            .page(PageSelector.first())
            .resourcePolicy(ResourcePolicy.dataUrisOnly())
            .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.FAIL)
            .limits(RenderLimits.builder().timeout(Duration.ofSeconds(20)).build())
            .build();

    /**
     * Ein Renderer je JVM, beim ersten Diagramm angelegt; seine Threads sind Daemon-Threads. Zwei Diagramme
     * gleichzeitig (Desktop), bis zu 64 wartend (Vorgabe), danach {@code OVERLOADED} und damit die PNG-Vorschau.
     */
    private static final class Holder {
        static final DrawioRenderer RENDERER = DrawioRenderer.builder()
                .maxConcurrentRenders(2)
                .build();
    }

    private DrawioDiagramRenderer() {
    }

    /**
     * @param xml {@code mxfile} (erste Seite; komprimiert oder nicht) oder ein {@code mxGraphModel}
     * @return SVG-Dokument als Text
     * @throws Unsupported wenn das Diagramm nicht lesbar oder nicht vollständig darstellbar ist
     */
    static String toSvg(String xml) throws Unsupported {
        if (xml == null || xml.trim().isEmpty()) {
            throw new Unsupported("Diagrammdaten fehlen");
        }
        RenderResult<String> result;
        try {
            result = Holder.RENDERER.renderSvg(xml, OPTIONS);
        } catch (RenderException e) {
            throw new Unsupported(reason(e));
        }
        if (result.getWidth() <= 1 && result.getHeight() <= 1) {
            // Leere Seite: drawio-java liefert ein 1×1-SVG; wie bisher lieber die Vorschau des Plugins.
            throw new Unsupported("Diagramm ohne Elemente");
        }
        return result.getOutput();
    }

    /** Nur Code und Fähigkeit; {@link RenderException#getMessage()} wird bewusst nicht übernommen (nennt Zell-IDs). */
    static String reason(RenderException e) {
        String capability = e.getCapabilityId();
        switch (e.getCode()) {
            case UNSUPPORTED_STENCIL:
            case UNSUPPORTED_FEATURE:
            case MISSING_RESOURCE:
                return "nicht unterstützt: " + (capability == null ? e.getCode().name() : capability);
            default:
                return e.getCode().name() + (capability == null ? "" : " (" + capability + ")");
        }
    }
}
```

`ConfluenceRestClient.java` line 868: the log line names JGraphX. Change

```java
LOG.info("[Confluence] draw.io: JGraphX nicht möglich (" + drawio + "), verwende PNG-Vorschau: "
```

to

```java
LOG.info("[Confluence] draw.io: Zeichnung nicht möglich (" + drawio + "), verwende PNG-Vorschau: "
```

Nothing else in `ConfluenceRestClient` changes: fetching, authorization, the
OTP and `TwoFactorRequired` handling and the PNG fallback stay as they are.
The renderer performs no HTTP requests and throws none of the sign-in
exceptions.

### 4.3 Tests

**Replace** `.../confluence/DrawioDiagramRendererTest.java` (JUnit 4, as in
the module) with the class below. It passed against the adapter with the
stencil data removed (probed). Only synthetic diagrams are used.

```java
package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** draw.io-Diagramme (erfundene Inhalte) ohne Browser mit drawio-java als SVG; Unbekanntes wird gemeldet, nicht geraten. */
public class DrawioDiagramRendererTest {

    private static final String PNG_DATA = "data:image/png,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4"
            + "2mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    private static String model(String cells) {
        return "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/>" + cells + "</root></mxGraphModel>";
    }

    private static String mxfile(String model) {
        return "<mxfile host=\"x\"><diagram id=\"d\" name=\"Seite-1\">" + model + "</diagram></mxfile>";
    }

    private static String vertex(String id, String value, String style) {
        return "<mxCell id=\"" + id + "\" value=\"" + value + "\" style=\"" + style + "\" vertex=\"1\" parent=\"1\">"
                + "<mxGeometry x=\"10\" y=\"10\" width=\"80\" height=\"60\" as=\"geometry\"/></mxCell>";
    }

    private static final String TWO_BOXES = model(
            "<mxCell id=\"a\" value=\"&lt;div&gt;&amp;lt;&amp;lt;Modul&amp;gt;&amp;gt;&lt;br&gt;Alpha&amp;nbsp;Eins&lt;/div&gt;\""
                    + " style=\"rounded=0;whiteSpace=wrap;html=1;fillColor=default;strokeColor=light-dark(#333333, #ededed);\""
                    + " vertex=\"1\" parent=\"1\"><mxGeometry x=\"10\" y=\"10\" width=\"120\" height=\"60\" as=\"geometry\"/>"
                    + "</mxCell>"
                    + "<mxCell id=\"b\" value=\"Beta\" style=\"ellipse;fillColor=#fff;\" vertex=\"1\" parent=\"1\">"
                    + "<mxGeometry x=\"200\" y=\"10\" width=\"80\" height=\"60\" as=\"geometry\"/></mxCell>"
                    + "<mxCell id=\"i\" style=\"shape=image;image=" + PNG_DATA + ";\" vertex=\"1\" parent=\"1\">"
                    + "<mxGeometry x=\"10\" y=\"100\" width=\"20\" height=\"20\" as=\"geometry\"/></mxCell>"
                    + "<mxCell id=\"e\" style=\"edgeStyle=orthogonalEdgeStyle;shape=filledEdge;html=1;\" edge=\"1\""
                    + " parent=\"1\" source=\"a\" target=\"b\"><mxGeometry relative=\"1\" as=\"geometry\"/></mxCell>");

    @Test
    public void anUncompressedDiagramBecomesSvgWithPlainLabelsAndDrawioColors() throws Exception {
        String svg = DrawioDiagramRenderer.toSvg(mxfile(TWO_BOXES));

        assertTrue(svg, svg.contains("<svg"));
        assertTrue(svg, svg.contains("&lt;&lt;Modul&gt;&gt;"));
        assertTrue(svg, svg.contains("Alpha Eins"));
        assertTrue(svg, svg.contains("Beta"));
        assertFalse("keine HTML-Reste in der Beschriftung", svg.contains("div") || svg.contains("&amp;nbsp;"));
        assertTrue("light-dark(): helle Farbe, Batik schreibt rgb()", svg.contains("rgb(51,51,51)"));
        assertTrue("eingebettetes Bild als gültige Daten-URI", svg.contains("data:image/png;base64,iVBOR"));
    }

    @Test
    public void aCompressedPageIsUnpacked() throws Exception {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(URLEncoder.encode(TWO_BOXES, "UTF-8").replace("+", "%20").getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        String packed = "<mxfile><diagram id=\"d\">" + Base64.getEncoder().encodeToString(out.toByteArray())
                + "</diagram></mxfile>";

        assertTrue(DrawioDiagramRenderer.toSvg(packed).contains("Beta"));
    }

    @Test
    public void aBpmnGatewayAndBlockArrowAreDrawnByTheOriginalShapes() throws Exception {
        String svg = DrawioDiagramRenderer.toSvg(mxfile(model(
                vertex("g", "Prüfen?", "shape=mxgraph.bpmn.gateway2;outline=none;symbol=exclusiveGw;html=1;")
                        + vertex("p", "", "shape=mxgraph.arrows2.arrow;dy=0.65;dx=25;strokeColor=#333333;direction=south;"))));

        assertTrue(svg, svg.contains("<path"));
        assertTrue(svg, svg.contains("Prüfen?"));
        assertTrue(svg, svg.contains("rgb(51,51,51)"));
    }

    @Test
    public void anUnknownStencilIsReportedByNameInsteadOfGuessed() {
        assertEquals("nicht unterstützt: stencil:mxgraph.unbekannt.form",
                unsupported(mxfile(model(vertex("s", "Pfeil", "shape=mxgraph.unbekannt.form;")))));
        assertEquals("nicht unterstützt: shape:gibtsnicht",
                unsupported(mxfile(model(vertex("s", "Pfeil", "shape=gibtsnicht;")))));
    }

    /** Gilt nur, solange drawio-java-stencils nicht auf dem Testklassenpfad liegt (Vorgabe in diesem Modul). */
    @Test
    public void anXmlStencilNeedsTheOptionalStencilArtifact() {
        assertEquals("nicht unterstützt: stencil:mxgraph.bpmn.user_task",
                unsupported(mxfile(model(vertex("s", "", "shape=mxgraph.bpmn.user_task;")))));
    }

    @Test
    public void anExternalImageIsNotFetchedButReported() {
        assertEquals("nicht unterstützt: image:external", unsupported(mxfile(model(
                vertex("s", "", "shape=image;image=https://bilder.invalid/a.png;")))));
    }

    @Test
    public void aDoctypeIsRejected() {
        assertEquals("INVALID_XML",
                unsupported("<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><mxfile>&e;</mxfile>"));
    }

    @Test
    public void anEmptyPageOrMissingDataIsNotDrawn() {
        assertEquals("Diagramm ohne Elemente", unsupported(mxfile(model(""))));
        assertEquals("INVALID_DIAGRAM", unsupported("<mxfile/>"));
        assertEquals("Diagrammdaten fehlen", unsupported(" "));
    }

    @Test
    public void messagesNeverContainLabelsOrIds() {
        String message = unsupported(mxfile(model(vertex("Geheim-ID-4711", "Geheimes Projekt Falke",
                "shape=mxgraph.unbekannt.form;"))));

        assertFalse(message, message.contains("Geheim") || message.contains("Falke") || message.contains("4711"));
    }

    private static String unsupported(String xml) {
        try {
            DrawioDiagramRenderer.toSvg(xml);
            fail("expected Unsupported");
            return null;
        } catch (DrawioDiagramRenderer.Unsupported e) {
            return e.getMessage();
        }
    }
}
```

Changes to the old test, method by method:

| Old test | Why it changes |
|---|---|
| `anUncompressedDiagramBecomesSvgWithPlainLabelsAndDrawioColors` | Kept. `#333333` becomes `rgb(51,51,51)` (Batik writes `rgb()`). The JGraphX-specific `#C3D9FF` check is removed |
| `aCompressedPageIsUnpacked` | Unchanged |
| `anUnknownStencilIsReportedByNameInsteadOfGuessed` | New message `nicht unterstützt: stencil:mxgraph.unbekannt.form` |
| `aBlockArrowIsDrawnAsOutline`, `aRotatedInterfaceSymbolIsDrawnAsBallAndSocket`, `theBlockArrowFollowsDrawioGeometry` | Asserted the hand-written JGraphX canvas paths (`rotate(-90.0 `, arc strings, `blockArrowPath`). Deleted. Replaced by `aBpmnGatewayAndBlockArrowAreDrawnByTheOriginalShapes` |
| `anExternalImageIsNotFetchedButReported` | New message `nicht unterstützt: image:external` |
| `aDoctypeIsRejected` | New message `INVALID_XML` |
| `htmlLabelsBecomeLines` | `plainText` is gone. HTML label rendering is covered by the first test |
| new | `anXmlStencilNeedsTheOptionalStencilArtifact`, `anEmptyPageOrMissingDataIsNotDrawn`, `messagesNeverContainLabelsOrIds` |

**Adapt** these tests. Their fixtures depend on JGraphX behaviour:

| File | Test / line | Change | Reason (probed) |
|---|---|---|---|
| `ConfluenceViewImageTest.java` | `anUnsupportedDiagramFallsBackToThePreviewPng`, expected log line | `"[Confluence] draw.io: Zeichnung nicht möglich (nicht unterstützt: stencil:mxgraph.unbekannt.form), verwende PNG-Vorschau"` | new log text and message |
| `ConfluenceViewImageTest.java` | `aMacroWithADeliveredPreviewIsDrawnAsSvgEvenWhenThePreviewIsALoginPage` (`mxgraph.arrows2.arrow`) | none | renders with `<path` and the label |
| `ConfluenceOneTimeCodeTest.java` | `anUndrawableDiagramAsksForTheCodeOfItsPreviewAndThenShowsThePreview`: `drawioShape = "ext";` | `"mxgraph.unbekannt.form"` | `ext` is a registered Shapes.js shape and now renders, so no challenge would appear |

These need no change: `ConfluencePageViewTest` and
`ConfluenceDownloadDiagnosisTest` (they only build fragments and paths) and
`ImageSlideshowControlsTest` (exedra, fakes). Existing coverage for the
fallback: `anUnsupportedDiagramFallsBackToThePreviewPng` (the preview is
requested, no draw.io name in the logs) and
`anUndrawableDiagramAsksForTheCodeOfItsPreviewAndThenShowsThePreview` (the
`TwoFactorRequired` type survives, one POST). Run
`./gradlew :proasteion:emporion:holkas:source-confluence:test` and the
exedra Mermaid and reader tests (GraalJS and Batik upgrade).

If a headless CI run fails in AWT, add
`systemProperty 'java.awt.headless', 'true'` to the module's `test` task, as
SH's `enterprise-ai-sources` already does. drawio-java's own build also sets
`-Djavax.accessibility.assistive_technologies=` for Ubuntu's OpenJDK 8.

### 4.4 Docs

- `docs/konfiguration-confluence.md` lines 125–140: JGraphX → drawio-java.
  Remove the alias and hand-drawn shape list. Update the log line text from
  section 4.2. Add the stencil trade-off from section 2.1.
- `docs/module.md` line 111: replace the JGraphX row with
  `drawio-java | 0.1.0 | source-confluence (draw.io-Diagramme als SVG)`.

## 5. Space Harvester

### 5.1 PR A: re-sync `enterprise-ai-sources` (follow its `UPSTREAM.md`)

```bash
enterprise-ai-sources/sync-from-upstream.sh sync <EAC merge commit of section 4>
./gradlew build
```

- The script copies the whole `source-confluence` module (sources and
  tests) and rewrites `UPSTREAM.sha256`. It needs no file-list change.
- Hand edits that belong in PR A (files owned by SH):
  - `gradle/libs.versions.toml`: add `drawioJava = "0.1.0"` and
    `drawio-java = { module = "com.aresstack:drawio-java", version.ref = "drawioJava" }`.
    **Keep `jgraphx` for now**, because `document-export` still uses it.
  - `enterprise-ai-sources/build.gradle`: replace
    `// draw.io-Diagramme als SVG (DrawioDiagramRenderer)` /
    `implementation libs.jgraphx` with
    `// draw.io-Diagramme als SVG (DrawioDiagramRenderer über drawio-java)` /
    `implementation libs.drawio.java`.
  - `enterprise-ai-sources/UPSTREAM.md`: update the "derzeit" sentence to the
    new commit and summary.
  - Root `build.gradle`: the Batik rule (section 2.3), inside `allprojects { }`.
- The copied EAC tests from section 4.3 run with `./gradlew build` (JUnit 4,
  `java.awt.headless=true` is already set in this module).

### 5.2 PR B: `document-export`

`document-export/build.gradle`: replace

```groovy
    // draw.io → SVG wie im Enterprise AI Client (DrawioDiagramRenderer, #190)
    implementation libs.jgraphx
```

with

```groovy
    // draw.io → SVG wie im Enterprise AI Client (DrawioDiagramRenderer über drawio-java, #190, drawio-java#16)
    implementation libs.drawio.java
```

`gradle/libs.versions.toml`: now delete `jgraphx = "4.2.2"` and the
`jgraphx = { module = ... }` line (no remaining user, verified above).

**Replace the whole file**
`document-export/src/main/java/com/aresstack/spaceharvester/document/DrawioDiagramRenderer.java`
with the source below. It differs from the EAC version only in its
visibility (`public`, as today), its Javadoc, the 30 s timeout and the
drawio-java default concurrency (min(4, CPUs) workers, 64 queued), because
it also runs in the server. It compiles with Java 21 (probed).

```java
package com.aresstack.spaceharvester.document;

import com.aresstack.drawio.DrawioRenderer;
import com.aresstack.drawio.api.PageSelector;
import com.aresstack.drawio.api.RenderException;
import com.aresstack.drawio.api.RenderLimits;
import com.aresstack.drawio.api.RenderOptions;
import com.aresstack.drawio.api.RenderResult;
import com.aresstack.drawio.api.ResourcePolicy;
import com.aresstack.drawio.api.UnsupportedFeaturePolicy;

import java.time.Duration;

/**
 * Zeichnet ein draw.io-Diagramm ({@code mxfile}-Quelldaten aus dem Datenbestand) ohne Browser und ohne Netz mit
 * drawio-java (Original-draw.io-JavaScript auf GraalJS, gezeichnet mit Java2D) als SVG, wie der Enterprise AI Client
 * ({@code source-confluence}); Aufrufer ist {@code Diagrams} im Core.
 *
 * <p>Was drawio-java nicht originalgetreu zeichnen kann (unbekannte Formen und Schablonen, Bilder, die nicht als
 * {@code data:}-URI eingebettet sind, nicht lesbare Daten, Zeit- und Größengrenzen), macht das Diagramm „nicht
 * vollständig darstellbar“ ({@link Unsupported}); dann nimmt der Export die abgelegte PNG-Vorschau. Der Renderer
 * ruft nie eine Adresse ab. Die Meldung nennt nur Fehlercode und Fähigkeit (etwa
 * {@code stencil:mxgraph.aws4.lambda}), nie Beschriftungen, IDs oder XML.</p>
 */
public final class DrawioDiagramRenderer {

    /** Das Diagramm ist nicht vollständig darstellbar; die Meldung nennt nur Code und Fähigkeit, keine Inhalte. */
    public static final class Unsupported extends Exception {
        private static final long serialVersionUID = 1L;

        Unsupported(String message) {
            super(message);
        }
    }

    /** Erste Seite, nur eingebettete Bilder, nichts stillschweigend weglassen, höchstens 30 s je Diagramm (Export). */
    private static final RenderOptions OPTIONS = RenderOptions.builder()
            .page(PageSelector.first())
            .resourcePolicy(ResourcePolicy.dataUrisOnly())
            .unsupportedFeaturePolicy(UnsupportedFeaturePolicy.FAIL)
            .limits(RenderLimits.builder().timeout(Duration.ofSeconds(30)).build())
            .build();

    /**
     * Ein Renderer je JVM, beim ersten Diagramm angelegt; seine Threads sind Daemon-Threads. Vorgaben von drawio-java:
     * min(4, CPUs) gleichzeitig, 64 wartend, danach {@code OVERLOADED}.
     */
    private static final class Holder {
        static final DrawioRenderer RENDERER = DrawioRenderer.create();
    }

    private DrawioDiagramRenderer() {
    }

    /**
     * @param xml {@code mxfile} (erste Seite; komprimiert oder nicht) oder ein {@code mxGraphModel}
     * @return SVG-Dokument als Text
     * @throws Unsupported wenn das Diagramm nicht lesbar oder nicht vollständig darstellbar ist
     */
    public static String toSvg(String xml) throws Unsupported {
        if (xml == null || xml.trim().isEmpty()) {
            throw new Unsupported("Diagrammdaten fehlen");
        }
        RenderResult<String> result;
        try {
            result = Holder.RENDERER.renderSvg(xml, OPTIONS);
        } catch (RenderException e) {
            throw new Unsupported(reason(e));
        }
        if (result.getWidth() <= 1 && result.getHeight() <= 1) {
            // Leere Seite: drawio-java liefert ein 1×1-SVG; wie bisher „nicht darstellbar“.
            throw new Unsupported("Diagramm ohne Elemente");
        }
        return result.getOutput();
    }

    /** Nur Code und Fähigkeit; {@link RenderException#getMessage()} wird bewusst nicht übernommen (nennt Zell-IDs). */
    static String reason(RenderException e) {
        String capability = e.getCapabilityId();
        switch (e.getCode()) {
            case UNSUPPORTED_STENCIL:
            case UNSUPPORTED_FEATURE:
            case MISSING_RESOURCE:
                return "nicht unterstützt: " + (capability == null ? e.getCode().name() : capability);
            default:
                return e.getCode().name() + (capability == null ? "" : " (" + capability + ")");
        }
    }
}
```

**`DrawioGraph.java` keeps working without the old renderer code:** move
these members from the old `DrawioDiagramRenderer` into `DrawioGraph`
**verbatim**, with only the following changes:

- `LINE_BREAK`, `TAG`, `NUMERIC_ENTITY` (patterns): `private static final`, unchanged.
- `static Document parse(String)` → `private static org.w3c.dom.Document parse(String) throws DrawioDiagramRenderer.Unsupported`.
- `static Element graphModel(Document)` → `private static Element graphModel(org.w3c.dom.Document) throws DrawioDiagramRenderer.Unsupported`.
- `private static String inflate(String) throws DrawioDiagramRenderer.Unsupported`.
- `static String plainText(String)`: unchanged.
- In those bodies, `new Unsupported(` → `new DrawioDiagramRenderer.Unsupported(`.
  The package-private constructor is reachable because both classes are in
  `com.aresstack.spaceharvester.document`.
- Change the three call sites: `DrawioDiagramRenderer.parse(` → `parse(`,
  `DrawioDiagramRenderer.graphModel(` → `graphModel(`,
  `DrawioDiagramRenderer.plainText(` → `plainText(`.
- Add the imports `java.io.ByteArrayOutputStream`, `java.io.StringReader`,
  `java.net.URLDecoder`, `java.util.Base64`, `java.util.Locale`,
  `java.util.regex.Matcher`, `java.util.regex.Pattern`,
  `java.util.zip.Inflater`, `javax.xml.parsers.DocumentBuilder`,
  `javax.xml.parsers.DocumentBuilderFactory` and `org.xml.sax.InputSource`.

`DrawioGraph.of(byte[]) throws DrawioDiagramRenderer.Unsupported` keeps its
signature, so `JsonPackageConverter` does not change. With this change,
`DrawioGraph.text()` gives the exact string that `DrawioGraphTest` expects
(probed). Its messages stay German parser messages
(`Diagrammdaten kein lesbares XML (...)`), as today.

`Diagrams.java` keeps working unchanged. Update only its class Javadoc
("draw.io (`mxfile`) wird SVG über JGraphX" → "über drawio-java").

### 5.3 SH tests

| Test | Change |
|---|---|
| `document-export/.../DrawioGraphTest` | none (probed) |
| `space-harvester-core/.../diagrams/DiagramsTest.drawsDrawioAsSvg` | none (probed: output contains `<svg` and `Start`) |
| `DiagramsTest.reportsWhatJgraphxCannotDrawInsteadOfGuessing` | rename to `reportsWhatCannotBeDrawnInsteadOfGuessing`. Assertions hold: `kein Diagramm` and the DOCTYPE input both give `INVALID_XML` |
| `ExportImagesTest.diagramsThatCannotBeDrawnAreReportedInEveryFormat` | update the comment that names JGraphX. The fixture `kein Diagramm` still fails (`INVALID_XML`) |
| `AssetsTest` (`theDrawioPreviewOfTheReadingViewBecomesThePngOfTheDiagram` and neighbours), `WorkspaceTest`, `ExportAssetCollectionTest`, `ParityConfluence`, `DemoConfluence` | none expected. Their fixtures are plain labelled vertices without `shape=`, which drawio-java renders. The `DemoConfluence` diagram has no geometry and was "Diagramm ohne Elemente" before and still is (probed). Confirm with `./gradlew build` |
| **new** `document-export/src/test/java/com/aresstack/spaceharvester/document/DrawioDiagramRendererTest` (JUnit 5 and AssertJ, as in the module) | `drawsLabelsAndShapesAsSvg` (vertex `Start`, `ellipse`, `mxgraph.bpmn.gateway2`), `reportsUnknownStencilsByCapability` (`nicht unterstützt: stencil:mxgraph.unbekannt.form`), `anXmlStencilNeedsTheOptionalStencilArtifact` (`stencil:mxgraph.bpmn.user_task`), `rejectsDoctype` (`INVALID_XML`), `anEmptyPageIsNotDrawn` (`Diagramm ohne Elemente`), `messagesNeverContainLabelsOrIds`. Same inline fixtures as the EAC test |

Fixtures: synthetic only, never Confluence content. Inline strings, as above,
are enough. If a larger BPMN case is wanted, copy drawio-java's
`src/test/resources/drawio/corpus/bpmn-approval-flow.drawio` (synthetic,
CC0-1.0 according to the corpus README) with a note on its origin. It renders
completely without the stencil jar (probed).

### 5.4 SH docs

- `document-export/UPSTREAM.md` line 7 ("draw.io zeichnet JGraphX") and line 28
  (the row now reads: `toSvg`/`Unsupported` public, adapter on drawio-java;
  `parse`/`graphModel`/`plainText` moved into `DrawioGraph`).
- `docs/CONVERTERS.md` lines 208–209 and 289: JGraphX → drawio-java.
- Optional: in `gradle/oss-licenses.gradle`, `com.aresstack` artifacts show up
  as `Unknown`. drawio-java is MIT.

## 6. Behaviour differences the owner must know

**Error mapping.** Every `RenderException` becomes `Unsupported`, and so the
existing fallback applies. The message never includes
`RenderException.getMessage()`, because that names cell ids, for example
"cell a uses an unsupported shape". It uses only the code and the capability
id:

| `RenderErrorCode` | `Unsupported` message |
|---|---|
| `UNSUPPORTED_STENCIL`, `UNSUPPORTED_FEATURE`, `MISSING_RESOURCE` | `nicht unterstützt: <capability>`, for example `stencil:mxgraph.aws4.lambda`, `shape:nosuchshape`, `image:external`, `canvas:fillStyle:…` |
| `INVALID_XML`, `UNSUPPORTED_FORMAT`, `INVALID_DIAGRAM`, `PAGE_NOT_FOUND`, `LIMIT_EXCEEDED`, `TIMEOUT`, `OVERLOADED`, `RENDER_FAILED`, `CANCELLED` | the code name, for example `INVALID_XML` |
| (adapter) empty page, 1×1 result | `Diagramm ohne Elemente` |
| (adapter) `null` or blank input | `Diagrammdaten fehlen` |

Capability ids name draw.io features or style values, not labels. SH's
`Diagrams` prefixes them with "draw.io nicht darstellbar: ". `CANCELLED` means
that the calling thread was interrupted: drawio-java keeps the interrupt
flag set, and EAC's `viewImage` still tries the preview afterwards, as it
does today after any `Unsupported`.

**What renders differently from JGraphX (probed):**

- Now drawn instead of falling back: draw.io JavaScript shapes (BPMN
  `gateway2`/`event`/`task`, `arrows2`, ER, Shapes.js such as `note` with its
  fold, `process`, `ext`), named styles without an alias table, and HTML
  labels with `b`/`i`/`u`/`font` instead of stripped plain text. The draw.io
  default stylesheet is used, not a hand-made copy.
- Two gaps found while probing `e44eb1f` are fixed on `main`, so the
  existing `"rect"` fixtures in `ConfluenceViewImageTest` and
  `ConfluenceOneTimeCodeTest` stay as they are:
  - `shape=rect` on a vertex is drawn as the plain rectangle, like draw.io's
    default vertex shape, without a warning (`shapeRectIsTheRectangleLikeInDrawio`).
  - Edges with `shape=flexArrow` or `shape=link` render; the `flat` line
    join that Shapes.js sets is ignored like browsers do
    (`flexArrowAndLinkEdgesRenderWithTheirFlatLineJoin`).
- SVG format: Batik `SVGGraphics2D` output, colors as `rgb(r,g,b)`,
  one `<text>` per label line (Java logical font names replaced by CSS
  generics such as `sans-serif`; underline and strikethrough are not emitted
  in `<text>` mode, see [OUTPUT.md](OUTPUT.md)), embedded images re-encoded as
  `data:image/png;base64,...`, and an XML prolog with `standalone="no"`.
  Code that searches the SVG for `#rrggbb` colors or JGraphX element
  structure will break. Only the tests listed above do this.
- Font metrics come from Java2D. Without Helvetica/Arial (or Liberation Sans
  or Arimo on Linux), wrapping can differ from the browser.

**Latency and resources:**

- First render per JVM: about 2–3.4 s (GraalJS and the 1.5 MB draw.io bundle;
  [PERFORMANCE.md](PERFORMANCE.md), full shape set: 1.9–3.4 s; probed on
  JDK 21: 3.3 s). The old plan's 1.5 s is out of date.
- Later renders: small diagrams take about 50–170 ms (probed), with a
  10-vertex warm SVG median of 61–73 ms and 100 vertices about 300–430 ms
  (PERFORMANCE.md). That is slower than JGraphX for small diagrams.
- The calling thread blocks until the result, the timeout (20 s in EAC, 30 s
  in SH; queueing time included) or an interrupt. EAC's `viewImage` already
  does network I/O on that thread, so it must already run off the Swing EDT.
  Check this once in the reader.
- Optional warm-up, not included above: call `toSvg` on a tiny diagram on a
  background thread after startup.
- Memory: the shared GraalJS engine keeps about 32 MiB after GC for the
  lifetime of the JVM. There is no per-render heap cap; size the heap for
  `maxConcurrentRenders` large diagrams.

**Threads and lifecycle:**

- One `DrawioRenderer` per JVM in a lazy holder. Its worker threads are
  daemons, so an unclosed renderer does not block JVM exit. Neither adapter
  closes it; a later `close()` would make every further call fail with
  `IllegalStateException`.
- EAC: 2 workers, 64 queued (default). SH: min(4, CPUs) workers, 64 queued.
  Requests beyond that fail fast with `OVERLOADED`. In EAC that means
  falling back to the authorized preview, and possibly an OTP prompt.
- Every request gets its own JavaScript context. No state crosses requests.

**Confidentiality and network:**

- The renderer never opens URLs (`ResourcePolicy.dataUrisOnly()`). External
  images give `MISSING_RESOURCE image:external`. It cannot trigger
  Confluence, OTP or proxy flows.
- The parser rejects DTDs and entities (`INVALID_XML`) and bounds
  decompression (`LIMIT_EXCEEDED`).
- Messages never contain labels, values or XML (drawio-java guarantee plus
  the adapter's code-only mapping; covered by `messagesNeverContainLabelsOrIds`).

## 7. Out of scope

- SH issue #571 (OTP) lies in the authentication flow, not in the renderer.
- Multi-page selection: only the first page is rendered, as today. The `page`
  in the REST path is the Confluence page id, not a diagram page. A diagram
  page name from the macro could later be passed with `PageSelector.name(...)`.
- `UnsupportedFeaturePolicy.WARN` (partial diagrams) needs a visible
  "incomplete" marker (`RenderResult.isComplete()`). Keep `FAIL` until a UI
  shows that marker.

## 8. Checklist

- [ ] drawio-java release with the stencil split is on Maven Central. Version noted.
- [ ] Owner decision on `drawio-java-stencils` for EAC and SH (default: leave out).
- [ ] EAC: `libs.versions.toml` (`drawioJava`, `drawio-java`; `jgraphx` removed), `source-confluence/build.gradle`, Batik rule in root `build.gradle`.
- [ ] EAC: `DrawioDiagramRenderer.java` replaced. Log text in `ConfluenceRestClient` line 868 changed.
- [ ] EAC: `DrawioDiagramRendererTest` replaced. `ConfluenceViewImageTest` (log line) and `ConfluenceOneTimeCodeTest` (`ext` → `mxgraph.unbekannt.form`) adapted.
- [ ] EAC: `dependencies` shows GraalJS 21.3.20.0.3 and Batik 1.20 only. source-confluence, Mermaid and proxy tests green.
- [ ] EAC: `docs/konfiguration-confluence.md` and `docs/module.md` updated. PR merged; merge commit noted.
- [ ] SH PR A: `sync-from-upstream.sh sync <commit>`, catalog `drawio-java` added, `enterprise-ai-sources/build.gradle` switched, `UPSTREAM.md` updated, Batik rule, `./gradlew build` green (including `verifyUpstreamCopy`).
- [ ] SH PR B: `document-export/build.gradle` switched, `DrawioDiagramRenderer.java` replaced, helpers moved into `DrawioGraph`, `jgraphx` removed from the catalog.
- [ ] SH PR B: new `DrawioDiagramRendererTest`, `DiagramsTest` method renamed, comments and docs (`document-export/UPSTREAM.md`, `docs/CONVERTERS.md`) updated, `./gradlew build` green.
- [x] drawio-java follow-ups: `shape=rect` and `flexArrow`/`link` edges fixed on `main`.
