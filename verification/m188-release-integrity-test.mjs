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
const shellMatch=sw.match(/const SHELL=\[(.*?)\];/s);
assert.ok(shellMatch,'service-worker shell list must exist');
const shell=new Set([...shellMatch[1].matchAll(/"(\.\/[^\"]+)"/g)].map(match=>match[1].slice(2)));

assert.equal(manifest.milestone,'M188');
assert.equal(manifest.appVersion,'3.230.0');
assert.equal(manifest.build,786);
assert.equal(manifest.engineVersion,'0.63.4');
assert.equal(manifest.cache,'pursuit-iron-production-m188-torture-certification');
assert.equal(manifest.uiMilestone,'M188 Production Torture + Certification');
assert.equal(manifest.candidateStatus,'ci_verified_device_test_pending');
assert.equal(profile.milestone,'M188');
assert.equal(profile.cache,manifest.cache);
assert.equal(profile.uiMilestone,manifest.uiMilestone);
assert.match(profile.engine,/0\.63\.4/);
assert.match(engineConfig,/ENGINE_VERSION\s*=\s*["']0\.63\.4["']/);
assert.match(sw,/const CACHE="pursuit-iron-production-m188-torture-certification"/);
assert.ok(app.includes("const __APP_VERSION__='3.230.0'; const __BUILD__='786';"),'App version/build must identify M188');
assert.match(index,/build:'786'/,'startup diagnostics must identify build 786');
assert.match(index,/build='786'/,'boot health must identify build 786');

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
assert.ok(manifest.localCandidate?.name?.includes('M188'),'local candidate must identify M188');

console.log(`PASS M188 release integrity: ${runtimeFiles.length} runtime files + ${uiFiles.length} UI files match the 3.230.0 / build 786 / Engine 0.63.4 candidate.`);
