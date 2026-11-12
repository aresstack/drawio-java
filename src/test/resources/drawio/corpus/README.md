# drawio-java reference and regression corpus (#13)

This directory holds the visual and structural regression corpus for the
renderer. `corpus.xml` is the single source of truth. For every file it
records the SHA-256, provenance, license, structural expectations and the
official draw.io reference images.

## Provenance and license

- **All fixtures are synthetic.** They were written for drawio-java
  (`tools/corpus/generate_fixtures.py`) and contain no business,
  authentication or personal data. Nothing was copied from Confluence or any
  internal system. The BPMN approval flow is a synthetic stand-in for the
  Space Harvester `gateway2` failure case.
- Fixtures, manifest and reference images: **CC0-1.0**.
- The reference images were produced by the **official draw.io viewer**
  `src/main/webapp/js/viewer-static.min.js` of
  [jgraph/drawio](https://github.com/jgraph/drawio) at commit
  `c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3`. That is the same commit as the
  bundled draw.io JavaScript (see `UPSTREAM.md`).
  - Viewer SHA-256 with LF line endings (GitHub raw):
    `35b8ef54369e938ad2399d3c8ce3945591d4b200a01f6942d0e39743bea1f1a1`.
  - The same file with CRLF line endings (Windows checkout with
    `core.autocrlf=true`):
    `1ed96a05a7cbe8158896bf2d52043434d648655a0577cd973da5e143eecadcac`.
    This is the value in `golden/REFERENCE.md`.
  - Environment: headless Chromium through Playwright on Linux,
    `deviceScaleFactor` 1, viewer config `{page, border: 0, zoom: 1}`, white
    background, **all network requests blocked**. Created 2026-11-12.
  - The XML stencil packs were not loaded, because the viewer requests them
    from `viewer.diagrams.net` and that request was blocked. Shapes that need
    them, such as the BPMN task markers `user`, `service` and `script`, appear
    without their marker in the reference. The external image in
    `images-data-uri` appears as a broken image.

## Layout

| Path | Content |
|---|---|
| `*.drawio` | Positive fixtures, uncompressed and compressed `mxfile`, one or more pages |
| `negative/*.drawio` | Hostile or invalid input: XXE, malformed XML, decompression bomb, broken compression, duplicate page names, unknown stencil, dangling edge, active HTML |
| `reference/<id>[.pageN].png` | Viewer screenshot. The visual tests compare against this |
| `reference/<id>[.pageN].svg` | Viewer SVG with `light-dark()` CSS removed so Batik can read it (lossless in light mode) |
| `corpus.xml` | Manifest |

## What the tests check

All tests are in `src/test/java/com/aresstack/drawio/corpus/`.

| Test | Checks |
|---|---|
| `CorpusIntegrityTest` | Every file is listed and matches its SHA-256. Provenance attributes are present. No confidential-looking strings |
| `CorpusStructureTest` | The production parser yields the expected pages, names, vertex and edge counts, shapes, labels, parents and terminals. Compressed and plain variants decode to the same model |
| `CorpusNegativeTest` | Parse-stage attacks are rejected with the documented reason, both by the parser and through `DrawioRenderer`. Render-stage cases (`UNSUPPORTED_STENCIL`, `WARN`, `SAFE`) are covered too. Error messages do not echo diagram content |
| `CorpusRenderTest` | Each page is rendered through `DrawioRenderer` and compared with the reference PNG (see below). Every manifest label must appear as text in the SVG output |
| `CorpusDeterminismTest` | The same input gives identical pixels, SVG and warnings (or the same error code) twice in a row and four times in parallel |

### Visual comparison

`ImageComparison` turns both images into ink masks (pixels visibly darker
than white). It crops each mask to its ink bounding box and aligns them at
the top-left corner, so padding and origin offsets do not matter. The score
is the F1 of ink coverage with a 2-pixel tolerance:

- **Precision:** the share of rendered ink that lies near reference ink.
- **Recall:** the share of reference ink that lies near rendered ink.

A missing shape, connector or label lowers recall. Spurious drawing lowers
precision. Font and antialiasing differences between Chromium and Java2D
move ink by a pixel or two and are absorbed by the tolerance. Ink bounding
boxes must also agree within 6 px or 3 %. The text-heavy fixture uses a
lower `minScore`.

### Render status: `match` and `pending`

- `render="match"`: the page must render, reach its `minScore`, agree on
  bounds and contain all labels. Any regression fails the build.
- `render="pending"`: the page is rendered and scored for the report but
  never fails the build. The report flags it **ready to promote** once it
  passes. Promoting means changing the attribute to `match` in
  `corpus.xml`, which turns the fixture into a regression gate.

Every run writes `build/reports/corpus/render.html` and `negative.html`
(and `.md`), with the reference, the rendered image and a diff for each
page. In the diff, grey is matching ink, red is missing ink and blue is
extra ink. CI uploads the directory as the `corpus-report` artifact.

### Determinism and fonts

The test JVM runs headless with locale `en_US` and UTF-8 encoding (see
`build.gradle`). Java2D uses the platform's logical fonts, so glyph shapes
differ between Linux, Windows and Chromium. The tolerance and the SVG label
check absorb this, and nothing compares glyph pixels exactly. CI runs the
corpus on Linux and Windows and on JDK 8, 11, 17 and 21.

## Adding or changing a fixture

1. Write a synthetic diagram. Never use real diagrams from Confluence or
   other internal systems. Either extend `tools/corpus/generate_fixtures.py`
   or save the file from draw.io and strip personal metadata.
2. Generate references with the pinned viewer. This step is for development
   only and needs Node.js and Playwright:
   ```bash
   curl -sSLO https://raw.githubusercontent.com/jgraph/drawio/c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3/src/main/webapp/js/viewer-static.min.js
   node tools/reference-viewer/render-references.mjs --viewer viewer-static.min.js --only <id>
   ```
   The script refuses a viewer whose SHA-256 differs from the pinned one.
   Look at the PNG before you commit it.
3. Add a `<fixture>` entry to `corpus.xml` with the SHA-256 of the fixture,
   PNG and SVG (`sha256sum`), the page expectations and `render="pending"`.
   `CorpusIntegrityTest` prints the actual checksums if any are wrong.
4. Promote it to `render="match"` once the report shows it passing.

Regenerating every fixture with the generator can change the compressed
bytes when the zlib version changes. Check the checksums in that case.
`CorpusStructureTest` still proves the decoded models are unchanged.
