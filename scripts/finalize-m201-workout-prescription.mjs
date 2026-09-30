import fs from 'node:fs';
import crypto from 'node:crypto';

const cache = 'pursuit-iron-production-v4-0-0-m201-workout-prescription-b791';
const read = p => fs.readFileSync(p, 'utf8');
const write = (p, s) => fs.writeFileSync(p, s);
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
write('modules/App.js', read('modules/App.js').replace(/const __BUILD__='\d+'/g, "const __BUILD__='791'"));
write('index.html', read('index.html').replace(/build(:|=)'\d+'/g, "build$1'791'"));
write('modules/next-engine/config.js', read('modules/next-engine/config.js').replace(/ENGINE_VERSION = '[^']+'/, "ENGINE_VERSION = '0.64.2'"));
write('sw.js', read('sw.js').replace(/const CACHE="[^"]+"/, `const CACHE="${cache}"`));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
Object.assign(profile, { milestone: 'M201', source: 'M199 baseline + completed M200 regional dose + custom workout prescription integrity',
    engine: '0.64.2 with pending rep target reconciliation and expanded exercise variations', cache,
    uiMilestone: 'Pursuit Iron 4.0 Production Release · M201 Workout Prescription' });
profile.nextRuntimeModules = fs.readdirSync('modules/next-engine').filter(f => f.endsWith('.js')).length;
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\n');
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
Object.assign(manifest, { milestone: 'M201', build: 791, engineVersion: '0.64.2', cache, uiMilestone: profile.uiMilestone,
    candidateStatus: 'verification_enforced_by_ci', runtimeHotfix: 'M201 restore implicit custom intensifiers, reconcile pending automatic reps, and register eight exercise variations' });
for (const file of Object.keys(manifest.runtimeFiles)) manifest.runtimeFiles[file] = hash(fs.readFileSync(file));
manifest.runtimeAggregate = hash(Object.keys(manifest.runtimeFiles).sort().map(f => `${f}:${manifest.runtimeFiles[f]}\n`).join(''));
for (const file of Object.keys(manifest.uiFiles)) manifest.uiFiles[file] = hash(fs.readFileSync(file));
write('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\n');
console.log(`M201 build 791 finalized: Engine 0.64.2; ${cache}`);
