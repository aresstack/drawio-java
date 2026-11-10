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

const verifiedSources = [];
for (const source of manifest.sources) {
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
const bundlePath = join(projectRoot, manifest.bundle);

if (process.argv.includes('--verify')) {
    const committedBundle = normalizeLineEndings(await readFile(bundlePath, 'utf8'));
    if (committedBundle !== bundle) {
        throw new Error('Draw.io bundle differs from pinned upstream sources. Regenerate it.');
    }
    process.stdout.write('Verified pinned Draw.io bundle (' + bundle.length + ' characters)\n');
} else {
    await writeFile(bundlePath, bundle, 'utf8');
    process.stdout.write('Generated ' + manifest.bundle + ' (' + bundle.length + ' characters)\n');
}
