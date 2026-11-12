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
