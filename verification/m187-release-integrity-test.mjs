import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const root=process.cwd();
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex');
const walkJs=dir=>{
  const out=[];
  for(const entry of fs.readdirSync(path.join(root,dir),{withFileTypes:true})){
    const rel=path.posix.join(dir,entry.name);
    if(entry.isDirectory()) out.push(...walkJs(rel));
    else if(entry.name.endsWith('.js')) out.push(rel);
  }
  return out;
};

const profile=JSON.parse(read('BUILD_PROFILE.json'));
const manifest=JSON.parse(read('RELEASE_MANIFEST.json'));
const sw=read('sw.js');
const shellMatch=sw.match(/const SHELL=\[(.*?)\];/s);
assert.ok(shellMatch,'service-worker shell list must exist');
const shell=new Set([...shellMatch[1].matchAll(/"(\.\/[^\"]+)"/g)].map(match=>match[1].slice(2)));

assert.equal(profile.milestone,'M187');
assert.equal(manifest.milestone,'M187');
assert.equal(manifest.uiMilestone,profile.uiMilestone);
assert.equal(manifest.cache,profile.cache);
assert.match(sw,new RegExp(`const CACHE="${profile.cache.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')}"`));

const nextFiles=walkJs('modules/next-engine').sort();
const shadowFiles=walkJs('modules/shadow-engine').sort();
const vendorFiles=walkJs('vendor').sort();
const runtimeFiles=[...nextFiles,...shadowFiles,...vendorFiles].sort();
assert.equal(profile.nextRuntimeModules,nextFiles.length,'BUILD_PROFILE next runtime module count must be current');
assert.equal(profile.shadowRuntimeModules,shadowFiles.length,'BUILD_PROFILE shadow runtime module count must be current');
assert.deepEqual(Object.keys(manifest.runtimeFiles??{}).sort(),runtimeFiles,'release manifest must account for every runtime JS module');
for(const file of runtimeFiles) assert.equal(manifest.runtimeFiles[file],hash(file),`runtime hash mismatch: ${file}`);
const aggregateBlob=runtimeFiles.map(file=>`${file}:${manifest.runtimeFiles[file]}\n`).join('');
assert.equal(manifest.runtimeAggregate,crypto.createHash('sha256').update(aggregateBlob).digest('hex'),'runtime aggregate must match current runtime');

for(const file of [...nextFiles,...shadowFiles])
  assert.ok(shell.has(file),`offline shell is missing runtime module: ${file}`);

const requiredUi=['modules/App.js','index.html','sw.js','BUILD_PROFILE.json','CHANGELOG.md','app.css'];
assert.deepEqual(Object.keys(manifest.uiFiles??{}).sort(),requiredUi.sort(),'UI manifest must cover the complete release-facing shell');
for(const file of requiredUi) assert.equal(manifest.uiFiles[file],hash(file),`UI hash mismatch: ${file}`);

assert.ok(manifest.localCandidate?.name?.includes('M187'),'local candidate must identify M187');
assert.equal(manifest.candidateStatus,'ci_verified_device_test_pending');
assert.ok(shell.has('RELEASE_MANIFEST.json'),'release manifest must be available offline');
assert.ok(shell.has('app.css'),'premium UX stylesheet must be available offline');

console.log(`PASS M187 release integrity: ${nextFiles.length} next-engine modules, ${shadowFiles.length} shadow modules, ${runtimeFiles.length} runtime JS files hashed; offline shell and UI manifest complete.`);
