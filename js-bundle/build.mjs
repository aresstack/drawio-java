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
if (manifest.stencils) {
    await buildStencilBundle(manifest.stencils);
}
