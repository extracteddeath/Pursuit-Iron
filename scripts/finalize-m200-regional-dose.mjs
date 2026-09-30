import fs from 'node:fs';
import crypto from 'node:crypto';

const cache = 'pursuit-iron-production-v4-0-0-m200-regional-dose-b790';
const hash = text => crypto.createHash('sha256').update(text).digest('hex');
const read = p => fs.readFileSync(p, 'utf8');
const write = (p, s) => fs.writeFileSync(p, s);
write('modules/App.js', read('modules/App.js').replace("const __BUILD__='789'", "const __BUILD__='790'"));
write('index.html', read('index.html').replaceAll("'789'", "'790'"));
write('modules/next-engine/config.js', read('modules/next-engine/config.js').replace("ENGINE_VERSION = '0.64.0'", "ENGINE_VERSION = '0.64.1'"));
write('sw.js', read('sw.js').replace(/const CACHE="[^"]+"/, 'const CACHE="' + cache + '"'));
const profile = JSON.parse(read('BUILD_PROFILE.json'));
Object.assign(profile, {"milestone":"M200","source":"Verified M199 volume repair + regional dose preservation during generation","engine":"0.64.1 with accumulation regional reserve protected during final dose cleanup","cache":"pursuit-iron-production-v4-0-0-m200-regional-dose-b790","uiMilestone":"Pursuit Iron 4.0 Production Release · M200 Regional Dose"});
write('BUILD_PROFILE.json', JSON.stringify(profile, null, 2) + '\n');
const manifest = JSON.parse(read('RELEASE_MANIFEST.json'));
Object.assign(manifest, { milestone: 'M200', build: 790, engineVersion: '0.64.1', cache, uiMilestone: profile.uiMilestone,
    candidateStatus: 'verification_enforced_by_ci', runtimeHotfix: 'M200 preserve achieved public regional dose during final generation cleanup' });
for (const file of Object.keys(manifest.runtimeFiles)) manifest.runtimeFiles[file] = hash(fs.readFileSync(file));
manifest.runtimeAggregate = hash(Object.keys(manifest.runtimeFiles).sort().map(f => f + ':' + manifest.runtimeFiles[f] + '\n').join(''));
for (const file of Object.keys(manifest.uiFiles)) manifest.uiFiles[file] = hash(fs.readFileSync(file));
write('RELEASE_MANIFEST.json', JSON.stringify(manifest, null, 2) + '\n');
console.log('M200 build 790 finalized: Engine 0.64.1; ' + cache);
