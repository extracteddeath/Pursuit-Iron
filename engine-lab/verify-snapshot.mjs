import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const read=f=>fs.readFileSync(path.join(root,f));
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const snapshot=JSON.parse(read('snapshot.json'));
const runtimeOnly=process.argv.includes('--runtime-only');
if(!runtimeOnly)for(const [file,hash] of Object.entries(snapshot.files))assert.equal(sha(read(file)),hash,`Changed snapshot: ${file}`);
const coverage=JSON.parse(read('coverage.json'));
const release=JSON.parse(read('RELEASE_MANIFEST.json'));
if(!runtimeOnly)for(const name of coverage.engineModules)assert.equal(sha(read('modules/next-engine/'+name)),release.runtimeFiles['modules/next-engine/'+name],`Production engine mismatch: ${name}`);
const entry=await import(new URL('../index.js',import.meta.url));
assert.equal(coverage.schemaVersion, 2, 'canonical source coverage schema');
assert.deepEqual(coverage.entryRoots, ['modules/engine-api.js', 'modules/engine-shell.js'], 'canonical engine entry roots');
if(!runtimeOnly)for(const file of coverage.canonicalRuntimeFiles)assert.equal(sha(read(file)),release.runtimeFiles[file],`Canonical runtime mismatch: ${file}`);
for(const file of coverage.canonicalRuntimeFiles){
 const text=read(file).toString();
 assert.doesNotMatch(text,/from\s*['"](?:react|react\/|lucide-react)/,file);
 await import(new URL('../'+file,import.meta.url));
}
if(!runtimeOnly)assert.equal(entry.ENGINE_VERSION,coverage.release.engineVersion);
for(const name of coverage.domainExports)assert.ok(name in entry.shell,`Missing canonical domain export: ${name}`);
console.log(`PASS ${runtimeOnly?'headless imports':'snapshot hashes and canonical production identity'}: ${coverage.engineModules.length} engine modules, ${coverage.domainExports.length} domain exports, zero UI/reference source copies.`);
