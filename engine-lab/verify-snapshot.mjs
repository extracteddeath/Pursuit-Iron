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
if(!runtimeOnly) for(const [file,hash] of Object.entries(snapshot.files)) assert.equal(sha(read(file)),hash,`Changed snapshot: ${file}`);
const coverage=JSON.parse(read('coverage.json'));
const shell=read('modules/engine-shell.js');
const app=read('reference/App.production.js');
if(!runtimeOnly) {
  assert.equal(sha(app),coverage.appSourceSha256,'Application boundary source mismatch');
  assert.equal(sha(shell),coverage.canonicalShellSha256,'Canonical shell source mismatch');
}
const release=JSON.parse(read('RELEASE_MANIFEST.json'));
for(const name of coverage.engineModules) assert.equal(sha(read('modules/next-engine/'+name)),release.runtimeFiles['modules/next-engine/'+name],`Production engine mismatch: ${name}`);
assert.equal(sha(shell),release.runtimeFiles['modules/engine-shell.js'],'Production canonical shell mismatch');
assert.doesNotMatch(shell.toString(),/from\s*['"](?:react|react\/|lucide-react)/);
assert.match(app.toString(),/from\s*['"]\.\/engine-shell\.js['"]/);
assert.doesNotMatch(app.toString(),/from\s*['"]\.\/next-engine\//);
for(const name of coverage.engineModules) await import(new URL('../modules/next-engine/'+name,import.meta.url));
const entry=await import(new URL('../index.js',import.meta.url));
if(!runtimeOnly) assert.equal(entry.ENGINE_VERSION,coverage.release.engineVersion);
for(const name of coverage.shellExports) assert.ok(name in entry.shell,`Missing canonical shell export: ${name}`);
console.log(`PASS ${runtimeOnly?'canonical runtime imports':'snapshot hashes and production identity'}: ${coverage.engineModules.length} engine modules, ${coverage.shellExports.length} canonical shell exports, App consumes one engine boundary.`);
