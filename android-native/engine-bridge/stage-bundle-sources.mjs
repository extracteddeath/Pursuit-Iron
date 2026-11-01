// Build tooling only; never part of the Android JavaScript dependency graph.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
const snapshot=process.argv[2];
assert.ok(snapshot && process.argv.length===3,'Verified snapshot directory required');
const coverage=JSON.parse(fs.readFileSync(path.join(snapshot,'coverage.json'),'utf8'));
assert.equal(coverage.entryRoot,'modules/engine-api.js');
const sources=JSON.parse(fs.readFileSync(path.join(root,'bundle-sources.json'),'utf8'));
assert.equal(new Set(sources).size,sources.length);
const destination=path.join(snapshot,'android-native/engine-bridge');
fs.mkdirSync(destination,{recursive:true});
for (const name of sources) {
  assert.match(name,/^[a-z0-9-]+\.mjs$/);
  fs.copyFileSync(path.join(root,name),path.join(destination,name));
}
console.log(`PASS staged ${sources.length} allowlisted Android bridge sources`);
