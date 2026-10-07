import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
const index=fs.readFileSync(new URL('../index.html',import.meta.url),'utf8');
const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const sw=fs.readFileSync(new URL('../sw.js',import.meta.url),'utf8');
const manifest=JSON.parse(fs.readFileSync(new URL('../RELEASE_MANIFEST.json',import.meta.url),'utf8'));
const build=String(manifest.build);
assert.ok(index.includes(`build='${build}'`),'boot-health must use the current release build as its single comparison token');
assert.ok(index.includes('v.build===build'),'boot-health attempts must accumulate only within the current build');
assert.equal(index.includes("v.build==='774'"),false,'stale M168 build comparison must not survive');
assert.ok(index.includes(`build:'${build}'`),'startup diagnostics must record the current build');
assert.ok(index.includes('viewport-fit=cover'));
assert.equal(/user-scalable\s*=\s*no/i.test(index),false,'pinch zoom must stay available');
assert.ok(css.includes('input,textarea{font-size:16px}'),'mobile input focus must retain anti-auto-zoom sizing');
assert.ok(css.includes('overscroll-behavior:contain'),'inner scrollers must contain overscroll');
assert.ok(css.includes('height:var(--app-h,100dvh)'),'root height must support dynamic viewport recovery');
assert.ok(index.includes('updateViaCache: "none"'),'service-worker checks must bypass stale script cache');
assert.ok(index.includes('if (!window.__pursuitUpdateRequested || reloaded) return;'),'controller takeover must remain user-gated');
assert.ok(sw.includes("e.data.type==='SKIP_WAITING'"),'waiting worker must support explicit restart');
assert.ok(sw.includes("if(r.mode==='navigate')"),'offline navigation fallback must remain present');
const shell=[...sw.matchAll(/"(\.\/[^\"]+)"/g)].map(m=>m[1]);
for(const rel of shell){if(rel==='./')continue;assert.ok(fs.existsSync(new URL('../'+rel.slice(2),import.meta.url)),`precache path missing: ${rel}`);}

// Exercise the real release writer: same-build content repairs must rotate the offline cache,
// while unchanged finalization stays byte-identical and never accumulates revision suffixes.
const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'pursuit-release-revision-'));
try {
    for (const file of ['scripts/finalize-release.mjs', 'RELEASE_MANIFEST.json', 'BUILD_PROFILE.json',
        'modules', 'vendor', 'index.html', 'manifest.webmanifest', 'sw.js', 'gallery.json',
        'app.css', 'CHANGELOG.md', 'icons', 'favicon.ico']) {
        const destination = path.join(fixture, file);
        fs.mkdirSync(path.dirname(destination), { recursive: true });
        fs.cpSync(new URL('../' + file, import.meta.url), destination, { recursive: true });
    }
    const finalize = () => {
        execFileSync(process.execPath, ['scripts/finalize-release.mjs'], { cwd: fixture, stdio: 'pipe' });
        return JSON.parse(fs.readFileSync(path.join(fixture, 'RELEASE_MANIFEST.json'), 'utf8'));
    };
    const finalizedFiles = ['sw.js', 'RELEASE_MANIFEST.json', 'BUILD_PROFILE.json'];
    const snapshot = () => finalizedFiles.map(file => fs.readFileSync(path.join(fixture, file), 'utf8'));
    let release = finalize();
    assert.match(release.cache, /-r[0-9a-f]{16}$/);
    const baseline = snapshot();
    finalize();
    assert.deepEqual(snapshot(), baseline, 'unchanged finalization must be byte-identical');
    for (const [file, suffix] of [['modules/training-domain/analytics.js', '\n// runtime revision probe\n'],
        ['app.css', '\n/* stylesheet revision probe */\n'], ['gallery.json', '\n ']]) {
        const oldCache = release.cache;
        fs.appendFileSync(path.join(fixture, file), suffix);
        release = finalize();
        assert.notEqual(release.cache, oldCache, `${file} changes must rotate the offline cache`);
        assert.equal(release.build, manifest.build, 'source repairs retain the authored build');
        assert.equal(release.engineVersion, manifest.engineVersion, 'source revisions do not invent an engine model version');
        const profile = JSON.parse(fs.readFileSync(path.join(fixture, 'BUILD_PROFILE.json'), 'utf8'));
        assert.equal(profile.cache, release.cache);
        assert.ok(fs.readFileSync(path.join(fixture, 'sw.js'), 'utf8').includes(`const CACHE="${release.cache}"`));
        const changed = snapshot();
        finalize();
        assert.deepEqual(snapshot(), changed, 'repeated finalization cannot change cache identity');
    }
} finally {
    fs.rmSync(fixture, { recursive: true, force: true });
}
console.log(`M173 PWA/mobile source resilience OK for build ${build}; ${shell.length} shell entries verified.`);
console.log('PASS content-addressed offline revisions: runtime, CSS and data asset repairs rotate the cache at the same build; unchanged finalization is stable.');
