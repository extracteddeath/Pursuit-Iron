import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const root=process.cwd();
const read=file=>fs.readFileSync(path.join(root,file),'utf8');
const sha=file=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,file))).digest('hex');
const walkJs=dir=>{
  const out=[];
  for(const entry of fs.readdirSync(path.join(root,dir),{withFileTypes:true})){
    const rel=path.posix.join(dir,entry.name);
    if(entry.isDirectory()) out.push(...walkJs(rel));
    else if(entry.name.endsWith('.js')) out.push(rel);
  }
  return out;
};

const manifest=JSON.parse(read('RELEASE_MANIFEST.json'));
const profile=JSON.parse(read('BUILD_PROFILE.json'));
const index=read('index.html');
const sw=read('sw.js');
const app=read('modules/App.js');
const engineConfig=read('modules/next-engine/config.js');
const css=read('app.css');
const changelog=read('CHANGELOG.md');
const shellMatch=sw.match(/const SHELL=\[(.*?)\];/s);
assert.ok(shellMatch,'service-worker shell list must exist');
const shell=new Set([...shellMatch[1].matchAll(/"(\.\/[^\"]+)"/g)].map(match=>match[1].slice(2)));

assert.equal(manifest.milestone,'M195');
assert.equal(manifest.appVersion,'4.0.0');
assert.equal(manifest.build,787);
assert.equal(manifest.engineVersion,'0.64.0');
assert.equal(manifest.cache,'pursuit-iron-production-v4-0-0');
assert.equal(manifest.uiMilestone,'Pursuit Iron 4.0 Production Release');
assert.equal(manifest.candidateStatus,'ci_verified_device_test_pending');
assert.equal(profile.milestone,'M195');
assert.equal(profile.cache,manifest.cache);
assert.equal(profile.uiMilestone,manifest.uiMilestone);
assert.match(profile.engine,/0\.64\.0/);
assert.match(engineConfig,/ENGINE_VERSION\s*=\s*["']0\.64\.0["']/);
assert.match(sw,/const CACHE="pursuit-iron-production-v4-0-0"/);
assert.ok(app.includes("const __APP_VERSION__='4.0.0'; const __BUILD__='787';"),'App version/build must identify Pursuit Iron 4.0');
assert.match(index,/build:'787'/,'startup diagnostics must identify build 787');
assert.match(index,/build='787'/,'boot health must identify build 787');
assert.match(changelog,/## Pursuit Iron 4\.0\.0 — M195/,'4.0 changelog entry missing');
assert.ok(manifest.localCandidate?.name?.includes('4.0'),'local candidate must identify Pursuit Iron 4.0');

const nextFiles=walkJs('modules/next-engine').sort();
const shadowFiles=walkJs('modules/shadow-engine').sort();
const vendorFiles=walkJs('vendor').sort();
const runtimeFiles=[...nextFiles,...shadowFiles,...vendorFiles].sort();
assert.equal(profile.nextRuntimeModules,nextFiles.length);
assert.equal(profile.shadowRuntimeModules,shadowFiles.length);
assert.deepEqual(Object.keys(manifest.runtimeFiles).sort(),runtimeFiles,'runtime manifest must exhaustively cover shipped JS modules');
for(const file of runtimeFiles) assert.equal(manifest.runtimeFiles[file],sha(file),`runtime hash mismatch: ${file}`);
const aggregateBlob=runtimeFiles.map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join('');
assert.equal(manifest.runtimeAggregate,crypto.createHash('sha256').update(aggregateBlob).digest('hex'),'runtime aggregate mismatch');

const uiFiles=['modules/App.js','index.html','sw.js','BUILD_PROFILE.json','CHANGELOG.md','app.css'];
assert.deepEqual(Object.keys(manifest.uiFiles).sort(),uiFiles.sort(),'UI manifest coverage mismatch');
for(const file of uiFiles) assert.equal(manifest.uiFiles[file],sha(file),`UI hash mismatch: ${file}`);

for(const required of ['index.html','app.css','modules/main.js','RELEASE_MANIFEST.json'])
  assert.ok(shell.has(required),`offline shell missing ${required}`);
for(const file of [...nextFiles,...shadowFiles])
  assert.ok(shell.has(file),`offline shell missing runtime module ${file}`);

for(const required of [
  'modules/next-engine/progression-style.js',
  'modules/next-engine/workout-history-adapter.js',
  'modules/next-engine/phase-transition.js',
  'modules/next-engine/cycle-runtime-adapter.js',
  'modules/next-engine/simulation.js'
]) assert.ok(manifest.runtimeFiles[required],`4.0 progression runtime missing from manifest: ${required}`);

for(const token of ['--pi-radius-sm','--pi-radius-md','--pi-radius-lg','--pi-motion-fast','--pi-motion-standard','--pi-ease'])
  assert.ok(css.includes(token),`missing premium UI token ${token}`);
assert.match(css,/prefers-reduced-motion:reduce/,'reduced-motion contract regressed');
assert.match(css,/:focus-visible/,'focus visibility regressed');

console.log(`PASS Pursuit Iron 4.0 release integrity: ${runtimeFiles.length} runtime files + ${uiFiles.length} UI files match app 4.0.0 / build 787 / Engine 0.64.0.`);
