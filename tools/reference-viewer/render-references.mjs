#!/usr/bin/env node
// Development-only reference generator for the drawio-java corpus (#13).
//
// Renders every page of every corpus fixture with the OFFICIAL draw.io
// viewer (viewer-static.min.js) of the pinned upstream commit inside a
// local headless Chromium, with all network access blocked. Writes the
// viewer's SVG and a PNG screenshot per page. Library consumers never need
// this script, Node.js or a browser.
//
// Usage:
//   node tools/reference-viewer/render-references.mjs \
//     --viewer /path/to/viewer-static.min.js \
//     [--corpus src/test/resources/drawio/corpus] [--out <corpus>/reference] [--only id]
//
// Requires the 'playwright' package with a Chromium build (dev machine only).
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';

// SHA-256 of viewer-static.min.js at upstream commit
// c72c9a98b7a6ce392556ed04c05e0ff19f43f5c3: LF bytes as served by GitHub raw,
// and the CRLF variant produced by a Windows checkout with core.autocrlf=true.
const PINNED_VIEWER_SHA256 = new Set([
  '35b8ef54369e938ad2399d3c8ce3945591d4b200a01f6942d0e39743bea1f1a1',
  '1ed96a05a7cbe8158896bf2d52043434d648655a0577cd973da5e143eecadcac',
]);

function arg(name, fallback) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : fallback;
}

const viewerPath = arg('--viewer');
if (!viewerPath) {
  console.error('missing --viewer /path/to/viewer-static.min.js');
  process.exit(2);
}
const corpusDir = path.resolve(arg('--corpus', 'src/test/resources/drawio/corpus'));
const outDir = path.resolve(arg('--out', path.join(corpusDir, 'reference')));
const only = arg('--only');

const viewer = readFileSync(viewerPath);
const viewerSha = createHash('sha256').update(viewer).digest('hex');
if (!PINNED_VIEWER_SHA256.has(viewerSha)) {
  console.error(`viewer SHA-256 ${viewerSha} does not match the pinned upstream viewer`);
  process.exit(3);
}

const require = createRequire(import.meta.url);
let chromium;
try {
  ({ chromium } = require('playwright'));
} catch (e) {
  ({ chromium } = await import('playwright'));
}

function pageNames(xml) {
  const names = [];
  const re = /<diagram\b[^>]*\bname="([^"]*)"/g;
  let m;
  while ((m = re.exec(xml)) !== null) names.push(m[1]);
  return names.length ? names : [null];
}

function escapeHtmlAttr(s) {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// Batik cannot parse CSS light-dark(); the viewer writes it next to plain
// fill/stroke attributes, so dropping those declarations is lossless for
// light mode (same normalization as golden/REFERENCE.md).
function normalizeSvg(svg) {
  let out = svg.replace(/(fill|stroke|color|background-color|border-color):\s*light-dark\([^;"]*\)\s*;?/g, '');
  out = out.replace(/\sstyle=""/g, '');
  if (!out.startsWith('<?xml')) out = '<?xml version="1.0" encoding="UTF-8"?>\n' + out;
  return out + '\n';
}

const fixtures = readdirSync(corpusDir).filter((f) => f.endsWith('.drawio')).sort();
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1400, height: 1200 }, deviceScaleFactor: 1, locale: 'en-US' });
await context.route('**/*', (route) => {
  const url = route.request().url();
  if (url.startsWith('data:') || url.startsWith('about:') || url.startsWith('http://drawio-java.local/')) {
    return route.continue();
  }
  return route.abort('blockedbyclient');
});

const blocked = [];
for (const file of fixtures) {
  const id = file.replace(/\.drawio$/, '');
  if (only && only !== id) continue;
  const xml = readFileSync(path.join(corpusDir, file), 'utf8');
  const names = pageNames(xml);
  for (let index = 0; index < names.length; index++) {
    const config = { xml, page: index, border: 0, zoom: 1 };
    const html = `<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#ffffff">
<div class="mxgraph" data-mxgraph="${escapeHtmlAttr(JSON.stringify(config))}"></div>
<script src="http://drawio-java.local/viewer-static.min.js"></script>
</body></html>`;
    const page = await context.newPage();
    page.on('requestfailed', (r) => blocked.push(`${id}#${index}: ${r.url().slice(0, 120)}`));
    await page.route('http://drawio-java.local/**', (route) => {
      const u = route.request().url();
      if (u.endsWith('/viewer-static.min.js')) return route.fulfill({ body: viewer, contentType: 'application/javascript; charset=utf-8' });
      return route.fulfill({ body: html, contentType: 'text/html; charset=utf-8' });
    });
    await page.goto('http://drawio-java.local/index.html');
    page.on('pageerror', (err) => console.log('pageerror:', err.message));
    await page.waitForSelector('.mxgraph svg', { timeout: 60000, state: 'attached' });
    await page.waitForTimeout(500);
    const svg = await page.$eval('.mxgraph svg', (el) => {
      const clone = el.cloneNode(true);
      clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      clone.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
      return new XMLSerializer().serializeToString(clone);
    });
    const suffix = names.length > 1 ? `.page${index}` : '';
    writeFileSync(path.join(outDir, `${id}${suffix}.svg`), normalizeSvg(svg));
    const element = await page.$('.mxgraph svg');
    await element.screenshot({ path: path.join(outDir, `${id}${suffix}.png`), omitBackground: false });
    await page.close();
    console.log(`rendered ${id}${suffix}`);
  }
}
await browser.close();
if (blocked.length) {
  console.log('blocked network requests (expected for external resources):');
  for (const b of blocked) console.log('  ' + b);
}
console.log(`viewer sha256 ${viewerSha}`);
