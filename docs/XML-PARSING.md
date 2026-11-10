# Secure Draw.io document parsing

Implementation: [Issue #8](https://github.com/aresstack/drawio-java/issues/8).

## Supported inputs

The Java 8 `DrawioDocumentParser` accepts:

- A direct, uncompressed `<mxGraphModel>` document.
- An `<mxfile>` containing one or more `<diagram>` pages.
- An uncompressed `<mxGraphModel>` nested in a `<diagram>`.
- Escaped XML text containing an `<mxGraphModel>` inside a `<diagram>`.
- Draw.io-compressed page payloads: Base64 of **raw Deflate**
  (no zlib header) applied to a URL-encoded UTF-8 graph model.

Each page retains its id, name, zero-based index and a normalized
`mxGraphModel` XML string. The parsed `DrawioDocument` offers an
immutable page list, `firstPage()`, `pageByIndex(int)` and
`pageByName(String)`. Duplicate matching names are reported as
`AMBIGUOUS_PAGE`, not chosen arbitrarily.

## Java 8 usage

```java
DrawioDocumentParser parser = new DrawioDocumentParser();
DrawioDocument document = parser.parse(drawioXml);

DiagramPage page = document.pageByName("Overview");
String graphModelXml = page.getGraphModelXml();
```

The parser is independent of GraalJS, Java2D, desktop UI, Confluence,
network access, file IO and credentials. It produces data for the future
graph model/layout renderer (#9), but **does not render diagrams**.

## Default security limits

- Input XML: at most 16 MiB of Java `char`s.
- Inflated URL-encoded payload: at most 16 MiB of bytes.
- XML depth: at most 128 nested elements.
- XML elements: at most 100,000 per parsed XML document.
- Pages per `mxfile`: at most 100.
- Base64: strict decoder; no silent acceptance of invalid characters.
- Deflate: fail on incomplete/corrupt streams or no forward progress.
- UTF-8: reject malformed/unmappable byte sequences.
- XML: disable DTD/DOCTYPE, external entities, XInclude and external
  DTD/stylesheet resolution. Validate element depth/count through a
  streaming SAX pass *before* constructing a DOM document.
- Output: limit serialized graph XML. Do not expose input contents in
  top-level validation error messages.

These are **initial fixed defaults**; policy/configurability can be
extracted into the public RenderOptions or parser options in #12.

## Error handling

`DiagramFormatException` exposes stable `Reason` values:

- `INVALID_XML`: absent, malformed or disallowed XML.
- `UNSUPPORTED_FORMAT`: not an `mxfile` or `mxGraphModel`.
- `INVALID_DIAGRAM`: a page does not contain a valid graph model.
- `INVALID_COMPRESSION`: corrupt Base64/Deflate/URL-encoding or UTF-8.
- `LIMIT_EXCEEDED`: input, decompression, XML complexity or page cap.
- `PAGE_NOT_FOUND`: no page for the requested name/index.
- `AMBIGUOUS_PAGE`: more than one page has that name.

The parser intentionally **does not** convert unknown shapes,
reformat styles, access external resources or drop unrecognized pages.

## Tests

The parser test corpus covers direct models, compressed and
uncompressed mxfile pages, page selection, duplicate names,
malformed XML, unknown roots, invalid compressed streams,
DOCTYPE/external entities, deep XML structures, the 100-page
limit, and a high-compression-ratio input that would expand
past the 16 MiB cap.

The older parser in Space Harvester's `DrawioDiagramRenderer`
was reviewed for compatibility. Unlike that implementation,
this parser is a stand-alone, multi-page component with explicit
security/resource limits and typed errors. The old renderer is
not modified by this ticket.
