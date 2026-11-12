import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const manifestPath = join(projectRoot, 'js-bundle', 'upstream.json');
const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));

function normalizeLineEndings(content) {
    return content.replace(/\r\n?/g, '\n');
}

function verifySourceHash(path, content, expectedHash) {
    const actualHash = createHash('sha256').update(content, 'utf8').digest('hex');
    if (actualHash !== expectedHash) {
        throw new Error('Pinned source hash mismatch: ' + path);
    }
}

async function buildBundle(sources, outputPath) {
    const verifiedSources = [];
    for (const source of sources) {
        const sourceText = normalizeLineEndings(
            await readFile(join(projectRoot, source.path), 'utf8')
        );
        verifySourceHash(source.path, sourceText, source.sha256NormalizedLf);
        verifiedSources.push(sourceText.trimEnd());
    }

    const banner = [
        '/*',
        ' * Bundle original draw.io scripts from pinned upstream sources.',
        ' * Upstream commit: ' + manifest.upstreamCommit,
        ' * Apache-2.0; see upstream/LICENSE and THIRD-PARTY-NOTICES.md.',
        ' */'
    ].join('\n');

    const bundle = banner + '\n' + verifiedSources.join('\n;\n') + '\n';
    await writeOrVerify(bundle, outputPath);
}

async function writeOrVerify(bundle, outputPath) {
    const bundlePath = join(projectRoot, outputPath);

    if (process.argv.includes('--verify')) {
        const committedBundle = normalizeLineEndings(await readFile(bundlePath, 'utf8'));
        if (committedBundle !== bundle) {
            throw new Error('Draw.io bundle differs from pinned upstream sources: ' + outputPath);
        }
        process.stdout.write('Verified pinned Draw.io bundle: ' + outputPath + '\n');
    } else {
        await writeFile(bundlePath, bundle, 'utf8');
        process.stdout.write('Generated ' + outputPath + ' (' + bundle.length + ' characters)\n');
    }
}

/*
 * Copy selected top-level members of the original js/grapheditor/Graph.js
 * verbatim: every 'Graph.prototype.<name> = function ... };' or
 * 'Graph.<name> = ...;' statement that starts at column 0. The members are
 * the helpers that the original shape library (Shapes.js) calls on the
 * graph, for example the table line painting. Nothing is rewritten.
 */
function extractGraphMember(sourceText, name) {
    const qualified = name.startsWith('Graph.') ? name : 'Graph.prototype.' + name;
    const start = sourceText.indexOf('\n' + qualified + ' = ');
    if (start < 0 || sourceText.indexOf('\n' + qualified + ' = ', start + 1) >= 0) {
        throw new Error('Graph.js member not found exactly once at column 0: ' + qualified);
    }
    const end = sourceText.indexOf('\n};', start);
    const statementEnd = sourceText.indexOf(';\n', start);
    const isFunction = /^ = function/.test(sourceText.slice(start + 1 + qualified.length));
    const stop = isFunction ? end + 3 : statementEnd + 1;
    if (stop <= start) {
        throw new Error('Graph.js member has no end: ' + qualified);
    }
    return sourceText.slice(start + 1, stop);
}

async function buildGraphPorts(ports) {
    const sourceText = normalizeLineEndings(await readFile(join(projectRoot, ports.source.path), 'utf8'));
    verifySourceHash(ports.source.path, sourceText, ports.source.sha256NormalizedLf);
    const members = ports.members.map((name) => extractGraphMember(sourceText, name));
    // Whole top-level IIFE blocks, by line range of the pinned (hash-checked)
    // source: each range must start with '(function()' and end with '})();'.
    const lines = sourceText.split('\n');
    for (const range of ports.blocks || []) {
        const block = lines.slice(range[0] - 1, range[1]);
        if (block[0] !== '(function()' || block[block.length - 1] !== '})();') {
            throw new Error('Graph.js block ' + range.join('-') + ' is not a top-level IIFE');
        }
        members.push('// Graph.js lines ' + range[0] + '-' + range[1] + '\n' + block.join('\n'));
    }
    const banner = [
        '/*',
        ' * Members copied verbatim from the original draw.io js/grapheditor/Graph.js',
        ' * (' + ports.source.upstreamPath + ').',
        ' * Upstream commit: ' + manifest.upstreamCommit,
        ' * Apache-2.0; see upstream/LICENSE and THIRD-PARTY-NOTICES.md.',
        ' * Loaded after drawio-headless.js, which defines the headless Graph.',
        ' */'
    ].join('\n');
    await writeOrVerify(banner + '\n' + members.join('\n\n') + '\n', ports.bundle);
}

// Matches one stencil set entry of the original js/stencils.min.js, which
// stores each set as a single line: f['<set>.xml'] = '<base64>';
const STENCIL_ENTRY = /^f\['([A-Za-z0-9_\-\/]+\.xml)'\] = '[A-Za-z0-9+\/=]*';$/;

/*
 * Copy a subset of the original stencils.min.js: its preamble, the selected
 * f['...'] entry lines and its original decoder tail, all verbatim and in the
 * original order. Nothing inside the copied lines is rewritten.
 */
async function buildStencilBundle(stencils) {
    const source = stencils.source;
    const sourceText = normalizeLineEndings(
        await readFile(join(projectRoot, source.path), 'utf8')
    );
    verifySourceHash(source.path, sourceText, source.sha256NormalizedLf);

    const lines = sourceText.split('\n');
    let first = -1;
    let last = -1;
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].startsWith("f['")) {
            if (!STENCIL_ENTRY.test(lines[i])) {
                throw new Error('Unexpected stencil entry format at line ' + (i + 1));
            }
            if (first < 0) {
                first = i;
            } else if (last !== i - 1) {
                throw new Error('Stencil entries are not contiguous at line ' + (i + 1));
            }
            last = i;
        }
    }
    if (first < 0) {
        throw new Error('No stencil entries found in ' + source.path);
    }

    const wanted = new Set(stencils.include);
    if (wanted.size !== stencils.include.length) {
        throw new Error('Duplicate stencil set in manifest include list');
    }
    const selected = [];
    for (let i = first; i <= last; i++) {
        const name = STENCIL_ENTRY.exec(lines[i])[1];
        if (wanted.delete(name)) {
            selected.push(lines[i]);
        }
    }
    if (wanted.size > 0) {
        throw new Error('Stencil sets not found upstream: ' + Array.from(wanted).join(', '));
    }

    const banner = [
        '/*',
        ' * Offline subset of the original draw.io js/stencils.min.js.',
        ' * Upstream commit: ' + manifest.upstreamCommit,
        ' * Included stencil sets: ' + stencils.include.join(', '),
        ' * Preamble, entries and decoder are copied verbatim by js-bundle/build.mjs.',
        ' * Apache-2.0 plus upstream stencil restrictions; see docs/STENCILS.md.',
        ' */'
    ].join('\n');

    const bundle = banner + '\n'
        + lines.slice(0, first).join('\n') + '\n'
        + selected.join('\n') + '\n'
        + lines.slice(last + 1).join('\n');
    await writeOrVerify(bundle, stencils.bundle);
}

await buildBundle(manifest.sources, manifest.bundle);
if (manifest.modelSources && manifest.modelBundle) {
    await buildBundle(manifest.modelSources, manifest.modelBundle);
}
if (manifest.graphSources && manifest.graphBundle) {
    await buildBundle(manifest.graphSources, manifest.graphBundle);
}
for (const resource of manifest.resources || []) {
    const text = normalizeLineEndings(await readFile(join(projectRoot, resource.path), 'utf8'));
    verifySourceHash(resource.path, text, resource.sha256NormalizedLf);
    process.stdout.write('Verified pinned resource: ' + resource.path + '\n');
}
if (manifest.graphPorts) {
    await buildGraphPorts(manifest.graphPorts);
}
if (manifest.shapeLibrarySources && manifest.shapeLibraryBundle) {
    await buildBundle(manifest.shapeLibrarySources, manifest.shapeLibraryBundle);
}
if (manifest.stencils) {
    await buildStencilBundle(manifest.stencils);
}
