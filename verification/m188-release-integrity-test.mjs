import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const root=path.resolve(new URL('..',import.meta.url).pathname);
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
assert.match(index,/meta name="app-version" content="3\.230\.0"/);
assert.match(index,/meta name="app-build" content="786"/);
assert.match(index,/\?v=786/);
assert.match(index,/CURRENT_BUILD=786/);
assert.match(app,/__APP_VERSION__\s*=\s*['"]3\.230\.0['"]/);
assert.match(app,/APP_BUILD\s*=\s*786/);

const runtimeFiles=[...walkJs('modules/next-engine'),...walkJs('modules/shadow-engine'),...walkJs('vendor')].sort();
assert.equal(profile.nextRuntimeModules,walkJs('modules/next-engine').length);
assert.equal(profile.shadowRuntimeModules,walkJs('modules/shadow-engine').length);
assert.deepEqual(Object.keys(manifest.runtimeFiles).sort(),runtimeFiles,'runtime manifest must exhaustively cover shipped JS modules');
for(const file of runtimeFiles) assert.equal(manifest.runtimeFiles[file],sha(file),`runtime hash mismatch: ${file}`);
const aggregateBlob=runtimeFiles.map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join('');
assert.equal(manifest.runtimeAggregate,crypto.createHash('sha256').update(aggregateBlob).digest('hex'),'runtime aggregate mismatch');

const uiFiles=['modules/App.js','index.html','sw.js','BUILD_PROFILE.json','CHANGELOG.md','app.css'];
assert.deepEqual(Object.keys(manifest.uiFiles).sort(),uiFiles.sort(),'UI manifest coverage mismatch');
for(const file of uiFiles) assert.equal(manifest.uiFiles[file],sha(file),`UI hash mismatch: ${file}`);

for(const required of ['./index.html','./app.js','./app.css','./modules/main.js'])
  assert.ok(sw.includes(JSON.stringify(required)),`offline shell missing ${required}`);
for(const file of [...walkJs('modules/next-engine'),...walkJs('modules/shadow-engine')])
  assert.ok(sw.includes(JSON.stringify(`./${file}`)),`offline shell missing runtime module ${file}`);

console.log(`PASS M188 release integrity: ${runtimeFiles.length} runtime files + ${uiFiles.length} UI files match the 3.230.0 / build 786 / Engine 0.63.4 candidate.`);
