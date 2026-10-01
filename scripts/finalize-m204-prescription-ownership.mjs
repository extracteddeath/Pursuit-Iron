import fs from 'node:fs';
import crypto from 'node:crypto';

const cache = 'pursuit-iron-production-v4-0-0-m204-prescription-ownership-b794';
const read = p => fs.readFileSync(p, 'utf8');
const write = (p, s) => fs.writeFileSync(p, s);
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');

write('modules/App.js', read('modules/App.js').replace(/const __BUILD__='\d+'/g, "const __BUILD__='794'"));
write('index.html', read('index.html').replace(/build(:|=)'\d+'/g, "build$1'794'"));
write('modules/next-engine/config.js', read('modules/next-engine/config.js').replace(/ENGINE_VERSION = '[^']+'/, "ENGINE_VERSION = '0.64.4'"));
write('sw.js', read('sw.js')
  .replace(/^\/\* Pursuit Iron 4\.0[^\n]*\*\//, '/* Pursuit Iron 4.0 — M204 explicit prescription ownership and single-writer persistence. */')
  .replace(/const CACHE="[^"]+"/, `const CACHE="${cache}"`));

const profile = JSON.parse(read('BUILD_PROFILE.json'));
Object.assign(profile, {
  milestone: 'M204',
  source: 'M203 production baseline + explicit prescription/live-value ownership and single-writer persistence hardening',
  engine: '0.64.4 with field-level prescription ownership, non-mirroring volume repair, and explicit live-row ownership',
  cache,
  uiMilestone: 'Pursuit Iron 4.0 Production Release · M204 Prescription Ownership'
});
profile.nextRuntimeModules = fs.readdirSync('modules/next-engine').filter(f => f.endsWith('.js')).length;
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\n');

const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
Object.assign(manifest, {
  milestone: 'M204',
  build: 794,
  engineVersion: '0.64.4',
  cache,
  uiMilestone: profile.uiMilestone,
  candidateStatus: 'verification_enforced_by_ci',
  runtimeHotfix: 'M204 make generated prescriptions single-owned, track explicit user/manual field ownership, prevent volume-repair override mirroring, and make live-set/program persistence ownership explicit'
});
manifest.localCandidate = {
  name: 'Pursuit Iron 4.0 Production Release · M204',
  base: 'M204 / app 4.0.0 build 794 / Engine 0.64.4',
  validation: 'M204 enforces one generated prescription owner, explicit per-field user ownership, explicit live-row ownership, and a pure open/saved program commit boundary.'
};
for (const file of Object.keys(manifest.runtimeFiles)) manifest.runtimeFiles[file] = hash(fs.readFileSync(file));
manifest.runtimeAggregate = hash(Object.keys(manifest.runtimeFiles).sort().map(f => `${f}:${manifest.runtimeFiles[f]}\n`).join(''));
for (const file of Object.keys(manifest.uiFiles)) manifest.uiFiles[file] = hash(fs.readFileSync(file));
write('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\n');
console.log(`M204 build 794 finalized: Engine 0.64.4; ${cache}`);
