import fs from 'node:fs';
import crypto from 'node:crypto';

const cache = 'pursuit-iron-production-v4-0-0-m199-volume-repair-b789';
const hash = text => crypto.createHash('sha256').update(text).digest('hex');
const read = p => fs.readFileSync(p, 'utf8');
const write = (p, s) => fs.writeFileSync(p, s);
write('modules/App.js', read('modules/App.js').replace("const __BUILD__='788'", "const __BUILD__='789'"));
write('index.html', read('index.html').replaceAll("'788'", "'789'"));
let sw = read('sw.js').replace(/const CACHE="[^"]+"/, `const CACHE="${cache}"`);
sw = sw.replace(/\/\* Pursuit Iron 4\.0 production release[^\n]*\*\//, '/* Pursuit Iron 4.0 — M199 verified volume repair and canonical persistence. */');
if (!sw.includes('"./modules/next-engine/volume-repair.js"'))
    sw = sw.replace('const SHELL=[', 'const SHELL=[\n  "./modules/next-engine/volume-repair.js",');
write('sw.js', sw);
const profile = JSON.parse(read('BUILD_PROFILE.json'));
Object.assign(profile, { milestone: 'M199', source: 'Current M199 wizard fixes + verified volume repair and canonical persistence',
    engine: '0.64.0 with shared live-volume accounting and verified set reallocation', cache,
    uiMilestone: 'Pursuit Iron 4.0 Production Release · M199 Volume Repair',
    nextRuntimeModules: fs.readdirSync('modules/next-engine').filter(f => f.endsWith('.js')).length });
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\n');
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
Object.assign(manifest, { milestone: 'M199', build: 789, cache, uiMilestone: profile.uiMilestone,
    candidateStatus: 'ci_verified_device_test_pending', runtimeHotfix: 'M199 live-volume repair, productive set reallocation, and canonical save' });
manifest.runtimeFiles['modules/next-engine/volume-repair.js'] = '';
for (const file of Object.keys(manifest.runtimeFiles)) manifest.runtimeFiles[file] = hash(fs.readFileSync(file));
manifest.runtimeAggregate = hash(Object.keys(manifest.runtimeFiles).sort().map(f => `${f}:${manifest.runtimeFiles[f]}\n`).join(''));
for (const file of Object.keys(manifest.uiFiles)) manifest.uiFiles[file] = hash(fs.readFileSync(file));
write('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\n');
console.log(`M199 build 789 finalized: ${profile.nextRuntimeModules} engine modules; ${cache}.`);
