#!/usr/bin/env python3
"""Authoring aid for the drawio-java corpus fixtures (#13).

The generated .drawio files are committed; this script is only a convenience
for the initial authoring. Compressed pages use the draw.io format:
base64(rawDeflate(encodeURIComponent(xml))).
"""
import base64
import os
import sys
import urllib.parse
import zlib
from xml.sax.saxutils import escape, quoteattr

OUT = sys.argv[1]
PNG = ("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAJElEQVR42mNQSjsDR//PMMARLnGGQaiBGEXI4oNRw2g8"
       "DAoNAA1sj5DxNUmbAAAAAElFTkSuQmCC")


def v(cid, value, style, x, y, w, h, parent="1", extra=""):
    return ('<mxCell id=%s value=%s style=%s vertex="1" parent=%s%s>'
            '<mxGeometry x="%s" y="%s" width="%s" height="%s" as="geometry"/></mxCell>'
            % (quoteattr(cid), quoteattr(value), quoteattr(style), quoteattr(parent), extra, x, y, w, h))


def e(cid, value, style, src=None, tgt=None, parent="1", points=None, src_pt=None, tgt_pt=None):
    attrs = ""
    if src:
        attrs += ' source=%s' % quoteattr(src)
    if tgt:
        attrs += ' target=%s' % quoteattr(tgt)
    geo = '<mxGeometry relative="1" as="geometry">'
    if src_pt:
        geo += '<mxPoint x="%s" y="%s" as="sourcePoint"/>' % src_pt
    if tgt_pt:
        geo += '<mxPoint x="%s" y="%s" as="targetPoint"/>' % tgt_pt
    if points:
        geo += '<Array as="points">' + "".join('<mxPoint x="%s" y="%s"/>' % p for p in points) + '</Array>'
    geo += '</mxGeometry>'
    return ('<mxCell id=%s value=%s style=%s edge="1" parent=%s%s>%s</mxCell>'
            % (quoteattr(cid), quoteattr(value), quoteattr(style), quoteattr(parent), attrs, geo))


def model(cells, w=850, h=1100):
    return ('<mxGraphModel dx="1000" dy="800" grid="1" gridSize="10" guides="1" tooltips="1" connect="1" '
            'arrows="1" fold="1" page="0" pageScale="1" pageWidth="%d" pageHeight="%d" math="0" shadow="0">'
            '<root><mxCell id="0"/><mxCell id="1" parent="0"/>%s</root></mxGraphModel>' % (w, h, "".join(cells)))


def compress(xml):
    quoted = urllib.parse.quote(xml, safe="~()*!.'-_")  # encodeURIComponent
    c = zlib.compressobj(9, zlib.DEFLATED, -15)
    raw = c.compress(quoted.encode("ascii")) + c.flush()
    return base64.b64encode(raw).decode("ascii")


def mxfile(pages):
    out = ['<mxfile host="drawio-java-corpus" agent="synthetic fixture" version="24.7.17">']
    for pid, name, xml, compressed in pages:
        body = compress(xml) if compressed else xml
        out.append('<diagram id=%s name=%s>%s</diagram>' % (quoteattr(pid), quoteattr(name), body))
    out.append('</mxfile>')
    return "\n".join(out) + "\n"


def write(name, content):
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


EVENT = ("shape=mxgraph.bpmn.event;html=1;verticalLabelPosition=bottom;labelBackgroundColor=#ffffff;"
         "verticalAlign=top;align=center;perimeter=ellipsePerimeter;outlineConnect=0;aspect=fixed;")
GATEWAY = ("shape=mxgraph.bpmn.gateway2;html=1;verticalLabelPosition=bottom;labelBackgroundColor=#ffffff;"
           "verticalAlign=top;align=center;perimeter=rhombusPerimeter;outlineConnect=0;")
TASK = "rounded=1;whiteSpace=wrap;html=1;arcSize=10;fillColor=#ffffff;strokeColor=#000000;"
FLOW = "edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;endFill=1;"

# --- 1. BPMN approval flow (synthetic stand-in for the Space Harvester gateway2 case)
approval = model([
    v("start", "Start", EVENT + "outline=standard;symbol=general;", 40, 95, 50, 50),
    v("check", "Antrag prüfen", TASK, 140, 80, 120, 80),
    v("gw", "Genehmigt?", GATEWAY + "gwType=exclusive;", 320, 95, 50, 50),
    v("approve", "Antrag freigeben", TASK, 440, 10, 120, 80),
    v("reject", "Antrag ablehnen", TASK, 440, 150, 120, 80),
    v("done", "Erledigt", EVENT + "outline=end;symbol=terminate;", 620, 25, 50, 50),
    v("rejected", "Abgelehnt", EVENT + "outline=end;symbol=error;", 620, 165, 50, 50),
    e("f1", "", FLOW, "start", "check"),
    e("f2", "", FLOW, "check", "gw"),
    e("f3", "ja", FLOW + "exitX=0.5;exitY=0;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;", "gw", "approve"),
    e("f4", "nein", FLOW + "exitX=0.5;exitY=1;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;", "gw", "reject"),
    e("f5", "", FLOW, "approve", "done"),
    e("f6", "", FLOW, "reject", "rejected"),
])
write("bpmn-approval-flow.drawio", mxfile([("approval", "Genehmigung", approval, False)]))
write("bpmn-approval-flow-compressed.drawio", mxfile([("approval", "Genehmigung", approval, True)]))

# --- 2. Multi-page: uncompressed, compressed, empty
overview = model([
    v("a", "Eingang", "rounded=0;whiteSpace=wrap;html=1;", 20, 20, 120, 60),
    v("b", "Verarbeitung", "rounded=0;whiteSpace=wrap;html=1;", 220, 20, 120, 60),
    e("ab", "", "endArrow=classic;html=1;", "a", "b"),
])
details = model([
    v("split", "", GATEWAY + "gwType=parallel;", 20, 40, 60, 60),
    v("t1", "Teil A", TASK, 140, 0, 100, 50),
    v("t2", "Teil B", TASK, 140, 90, 100, 50),
    v("join", "", GATEWAY + "gwType=parallel;", 300, 40, 60, 60),
    e("s1", "", FLOW, "split", "t1"), e("s2", "", FLOW, "split", "t2"),
    e("j1", "", FLOW, "t1", "join"), e("j2", "", FLOW, "t2", "join"),
])
write("multipage-mixed.drawio", mxfile([
    ("p-overview", "Übersicht", overview, False),
    ("p-details", "Details", details, True),
    ("p-empty", "Leer", model([]), False),
]))

# --- 3. Labels and Unicode
L = "text;html=1;align=center;verticalAlign=middle;whiteSpace=wrap;rounded=0;strokeColor=#000000;"
labels = model([
    v("umlaut", "Grüße aus Köln – ÄÖÜ äöü ß", "rounded=0;whiteSpace=wrap;html=0;", 20, 20, 220, 50),
    v("html", "<b>Fett</b> und <i>kursiv</i><br>zweite Zeile", "rounded=0;whiteSpace=wrap;html=1;", 260, 20, 200, 50),
    v("multiline", "Zeile 1\nZeile 2\nZeile 3", "rounded=0;whiteSpace=wrap;html=0;", 20, 90, 220, 70),
    v("cjk", "日本語 / 中文 / 한국어", "rounded=0;whiteSpace=wrap;html=1;", 260, 90, 200, 70),
    v("greek", "Ελληνικά · Кириллица · Ünïcödé", "rounded=0;whiteSpace=wrap;html=1;", 20, 180, 220, 50),
    v("symbols", "✓ ✗ → ★ ≤ ≥ €", "rounded=0;whiteSpace=wrap;html=1;fontSize=16;", 260, 180, 200, 50),
    v("xmlchars", "A < B && C > D \"quoted\"", "rounded=0;whiteSpace=wrap;html=0;", 20, 250, 220, 50),
    v("vertical", "Vertikal", "rounded=0;whiteSpace=wrap;html=1;horizontal=0;", 260, 250, 50, 120),
    v("topleft", "oben links", "rounded=0;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=4;", 330, 250, 130, 50),
    v("bold18", "Groß & fett", "rounded=0;whiteSpace=wrap;html=1;fontSize=18;fontStyle=1;", 330, 320, 130, 50),
    v("italic", "kursiv", L + "fontStyle=2;fontColor=#CC0000;", 20, 320, 100, 40),
    v("underline", "unterstrichen", L + "fontStyle=4;fontFamily=Courier New;", 140, 320, 100, 40),
    v("wrap", "Ein sehr langer Beschriftungstext, der innerhalb der Form umgebrochen werden muss.",
      "rounded=0;whiteSpace=wrap;html=1;", 20, 390, 160, 70),
    v("nowrap", "Ohne Umbruch, ragt hinaus", "rounded=0;html=1;", 200, 400, 100, 40),
])
write("labels-unicode.drawio", mxfile([("labels", "Beschriftungen", labels, False)]))

# --- 4. Groups, containers, nested and relative geometry
groups = model([
    v("g1", "", "group", 20, 20, 200, 120, extra=' connectable="0"'),
    v("ga", "A", "rounded=0;whiteSpace=wrap;html=1;", 0, 0, 80, 40, parent="g1"),
    v("gb", "B", "rounded=0;whiteSpace=wrap;html=1;", 120, 80, 80, 40, parent="g1"),
    v("lane", "Abteilung", "swimlane;whiteSpace=wrap;html=1;startSize=23;", 260, 20, 240, 200),
    v("lc", "C", "rounded=0;whiteSpace=wrap;html=1;", 20, 40, 80, 40, parent="lane"),
    v("g2", "", "group", 20, 110, 200, 70, parent="lane", extra=' connectable="0"'),
    v("gd", "D", "ellipse;whiteSpace=wrap;html=1;", 0, 0, 80, 40, parent="g2"),
    v("ge", "E", "ellipse;whiteSpace=wrap;html=1;", 120, 30, 80, 40, parent="g2"),
    ('<mxCell id="badge" value="1" style="ellipse;whiteSpace=wrap;html=1;fillColor=#CC0000;fontColor=#FFFFFF;'
     'strokeColor=none;" vertex="1" parent="lc"><mxGeometry x="1" y="0" width="20" height="20" relative="1" '
     'as="geometry"><mxPoint x="-10" y="-10" as="offset"/></mxGeometry></mxCell>'),
    e("ac", "quer", "edgeStyle=orthogonalEdgeStyle;html=1;endArrow=classic;", "ga", "lc"),
    e("de", "", "endArrow=classic;html=1;", "gd", "ge", parent="g2"),
])
write("groups-nested.drawio", mxfile([("groups", "Gruppen", groups, False)]))

# --- 5. Edges and arrows
rows = []
styles = [
    ("classic", "endArrow=classic;html=1;"),
    ("block-oval-dashed", "endArrow=block;endFill=1;startArrow=oval;startFill=0;dashed=1;html=1;"),
    ("open-thick", "endArrow=open;strokeWidth=3;strokeColor=#0066CC;html=1;"),
    ("diamond-none", "endArrow=diamond;endFill=1;startArrow=none;html=1;"),
    ("none", "endArrow=none;html=1;"),
    ("elbow", "edgeStyle=elbowEdgeStyle;elbow=vertical;endArrow=classic;html=1;"),
]
cells = []
for i, (name, style) in enumerate(styles):
    y = 20 + 70 * i
    cells.append(v("s%d" % i, "Quelle %d" % i, "rounded=0;whiteSpace=wrap;html=1;", 20, y, 100, 40))
    cells.append(v("t%d" % i, "Ziel %d" % i, "rounded=0;whiteSpace=wrap;html=1;", 300, y + (30 if i == 5 else 0), 100, 40))
    cells.append(e("e%d" % i, name, style, "s%d" % i, "t%d" % i))
cells.append(v("o1", "Orthogonal", "rounded=0;whiteSpace=wrap;html=1;", 480, 20, 100, 40))
cells.append(v("o2", "mit Wegpunkt", "rounded=0;whiteSpace=wrap;html=1;", 640, 160, 100, 40))
cells.append(e("ortho", "", "edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=classic;"
               "exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0.5;entryY=0;entryDx=0;entryDy=0;",
               "o1", "o2", points=[(610, 40), (610, 100), (690, 100)]))
cells.append(v("c1", "Kurve", "ellipse;whiteSpace=wrap;html=1;", 480, 260, 80, 40))
cells.append(v("c2", "", "ellipse;whiteSpace=wrap;html=1;", 660, 330, 80, 40))
cells.append(e("curve", "", "curved=1;endArrow=classic;html=1;", "c1", "c2", points=[(600, 250), (640, 380)]))
cells.append(e("dangling", "frei", "endArrow=classic;html=1;", src_pt=(480, 420), tgt_pt=(700, 440)))
cells.append(v("loop", "Schleife", "rounded=0;whiteSpace=wrap;html=1;", 480, 480, 100, 40))
cells.append(e("self", "", "edgeStyle=orthogonalEdgeStyle;endArrow=classic;html=1;", "loop", "loop"))
write("edges-arrows.drawio", mxfile([("edges", "Kanten", model(cells), False)]))

# --- 6. Basic shapes and styles
B = "whiteSpace=wrap;html=1;fillColor=#DAE8FC;strokeColor=#6C8EBF;"
shapes = [
    ("rect", "Rechteck", "rounded=0;" + B), ("rounded", "abgerundet", "rounded=1;" + B),
    ("ellipse", "Ellipse", "ellipse;" + B), ("rhombus", "Raute", "rhombus;" + B),
    ("triangle", "Dreieck", "triangle;" + B), ("hexagon", "Sechseck", "shape=hexagon;perimeter=hexagonPerimeter2;" + B),
    ("cloud", "Wolke", "ellipse;shape=cloud;" + B), ("cylinder", "Zylinder", "shape=cylinder;" + B),
    ("actor", "", "shape=actor;" + B), ("double", "Doppelt", "ellipse;shape=doubleEllipse;" + B),
    ("process", "Prozess", "shape=process;" + B), ("parallelogram", "Parallelogramm", "shape=parallelogram;perimeter=parallelogramPerimeter;" + B),
    ("gradient", "Verlauf", "rounded=0;whiteSpace=wrap;html=1;gradientColor=#7EA6E0;gradientDirection=north;fillColor=#FFFFFF;"),
    ("shadow", "Schatten", "rounded=1;whiteSpace=wrap;html=1;shadow=1;"),
    ("opacity", "50 %", "rounded=0;whiteSpace=wrap;html=1;opacity=50;fillColor=#F8CECC;strokeColor=#B85450;"),
    ("rotated", "gedreht", "rounded=0;whiteSpace=wrap;html=1;rotation=30;"),
    ("flipped", "", "triangle;whiteSpace=wrap;html=1;flipH=1;fillColor=#D5E8D4;"),
    ("dashed", "gestrichelt", "rounded=0;whiteSpace=wrap;html=1;dashed=1;dashPattern=8 4;fillColor=none;strokeWidth=2;"),
    ("thick", "dick", "ellipse;whiteSpace=wrap;html=1;strokeWidth=4;strokeColor=#9673A6;fillColor=#E1D5E7;"),
    ("nofill", "ohne Füllung", "rounded=0;whiteSpace=wrap;html=1;fillColor=none;"),
]
cells = []
for i, (cid, label, style) in enumerate(shapes):
    cells.append(v(cid, label, style, 20 + (i % 5) * 140, 20 + (i // 5) * 110, 100, 70))
write("styles-basic-shapes.drawio", mxfile([("shapes", "Formen", model(cells), False)]))

# --- 7. Images: embedded data URI (allowed) and external URL (must not be fetched)
images = model([
    v("embedded", "eingebettet", "shape=image;html=1;verticalLabelPosition=bottom;verticalAlign=top;"
      "imageAspect=0;aspect=fixed;image=data:image/png," + PNG + ";", 20, 20, 64, 64),
    v("external", "extern", "shape=image;html=1;verticalLabelPosition=bottom;verticalAlign=top;"
      "imageAspect=0;aspect=fixed;image=https://example.invalid/drawio-java/logo.png;", 140, 20, 64, 64),
])
write("images-data-uri.drawio", mxfile([("images", "Bilder", images, False)]))

# --- 8. BPMN shape catalog (shapes registered by the pinned mxBpmnShape2.js)
cells = []
gw_types = ["exclusive", "parallel", "complex"]
for i, t in enumerate(gw_types):
    cells.append(v("gw-" + t, t, GATEWAY + "gwType=%s;" % t, 20 + i * 100, 20, 50, 50))
cells.append(v("gw-event", "event", GATEWAY + "gwType=event;outline=catching;symbol=multiple;", 320, 20, 50, 50))
events = [("standard", "general"), ("catching", "message"), ("boundInt", "timer"), ("throwing", "signal"),
          ("end", "terminate"), ("end", "error"), ("eventNonint", "escalation"), ("catching", "conditional")]
for i, (outline, symbol) in enumerate(events):
    cells.append(v("ev-%d" % i, symbol, EVENT + "outline=%s;symbol=%s;" % (outline, symbol), 20 + i * 80, 120, 40, 40))
tasks = ["abstract", "user", "service", "send", "receive", "script"]
TASKSHAPE = "shape=mxgraph.bpmn.task;whiteSpace=wrap;rectStyle=rounded;size=10;html=1;expand=0;collapsible=0;"
for i, m in enumerate(tasks):
    cells.append(v("task-" + m, m, TASKSHAPE + "taskMarker=%s;" % m, 20 + i * 120, 210, 100, 60))
cells.append(v("data", "Daten", "shape=mxgraph.bpmn.data;labelPosition=center;verticalLabelPosition=bottom;"
               "align=center;verticalAlign=top;size=15;html=1;", 20, 310, 40, 60))
cells.append(v("conv", "", "shape=mxgraph.bpmn.conversation;perimeter=hexagonPerimeter2;whiteSpace=wrap;html=1;"
               "aspect=fixed;bpmnConversationType=conv;", 120, 310, 50, 40))
write("bpmn-shapes-catalog.drawio", mxfile([("bpmn", "BPMN-Formen", model(cells), False)]))

# --- Negative / security fixtures
NEG = os.path.join(OUT, "negative")
os.makedirs(NEG, exist_ok=True)


def write_neg(name, content):
    with open(os.path.join(NEG, name), "w", encoding="utf-8", newline="\n") as f:
        f.write(content)


write_neg("xxe-doctype.drawio",
          '<?xml version="1.0"?>\n<!DOCTYPE mxfile [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>\n'
          '<mxfile><diagram id="x" name="XXE">' + escape(model([v("a", "&xxe;", "rounded=0;", 0, 0, 80, 40)])) +
          '</diagram></mxfile>\n')
write_neg("malformed-xml.drawio", '<mxfile><diagram id="m" name="kaputt"><mxGraphModel><root><mxCell id="0"/>\n')
write_neg("unsupported-root.drawio", '<svg xmlns="http://www.w3.org/2000/svg"><rect width="10" height="10"/></svg>\n')
write_neg("invalid-compression.drawio",
          '<mxfile><diagram id="c" name="defekt">AAAA!!!!nicht-base64</diagram></mxfile>\n')
# Decompression bomb: ~17 MiB of whitespace inside a valid model, compressed to a few KiB.
bomb_xml = model([]).replace("<root>", "<root>" + " " * (17 * 1024 * 1024), 1)
c = zlib.compressobj(9, zlib.DEFLATED, -15)
bomb = base64.b64encode(c.compress(bomb_xml.encode("ascii")) + c.flush()).decode("ascii")
write_neg("decompression-bomb.drawio", '<mxfile><diagram id="b" name="Bombe">' + bomb + '</diagram></mxfile>\n')
write_neg("duplicate-page-names.drawio", mxfile([("d1", "Gleich", model([]), False), ("d2", "Gleich", model([]), False)]))
# Valid XML that a renderer must reject or flag rather than silently drop.
write_neg("unknown-stencil.drawio", mxfile([("u", "Unbekannt", model([
    v("known", "bekannt", "rounded=0;whiteSpace=wrap;html=1;", 20, 20, 100, 50),
    v("unknown", "unbekannt", "shape=mxgraph.acme.notARealStencil;whiteSpace=wrap;html=1;", 160, 20, 100, 50),
]), False)]))
write_neg("dangling-edge-reference.drawio", mxfile([("r", "Referenz", model([
    v("only", "einzig", "rounded=0;whiteSpace=wrap;html=1;", 20, 20, 100, 50),
    e("broken", "", "endArrow=classic;html=1;", "only", "does-not-exist"),
]), False)]))
write_neg("active-content-label.drawio", mxfile([("h", "Aktiv", model([
    v("script", "<script>drawioHost.loadResource('x')</script>Text<img src=\"https://example.invalid/t.png\" "
      "onerror=\"alert(1)\">", "rounded=0;whiteSpace=wrap;html=1;", 20, 20, 200, 60),
    v("link", "<a href=\"javascript:alert(1)\">Link</a>", "rounded=0;whiteSpace=wrap;html=1;", 240, 20, 120, 60),
]), False)]))
print("ok")
