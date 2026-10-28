import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const total=(app.match(/style: \{/g)||[]).length;
assert.ok(total<=1522,'B960 retained utility owners must not regress to a higher inline-style count; later builds may retire more');
const owners=[...css.matchAll(/#root \.wpb \.(wpb-s960-[a-z0-9-]+),#root \.\1\{([^}]+)\}/g)];
assert.equal(owners.length,71,'B960 CSS owners must remain one declaration per exact property/value');
const names=new Set(owners.map(m=>m[1]));
assert.equal(names.size,owners.length,'no competing CSS utility owners');
for(const name of names){
 assert.ok(app.includes(name),'new static style owner must be used: '+name);
 assert.equal((css.match(new RegExp(name+'\\{','g'))||[]).length,1,'single shared CSS declaration: '+name);
}
assert.ok(owners.every(o=>!o[2].includes('!important')),'static migration adds no priority overrides');
for(const preserved of ['style: { background: C.', 'style: { color: C.', 'style: { transform:']){
 assert.ok(app.includes(preserved),'dynamic theme/motion must remain owned by JSX: '+preserved);
}
const runner=fs.readFileSync('scripts/verify-engine-contracts.mjs','utf8');
assert.ok(runner.includes('CONTRACT_FAILURE')&&runner.includes('finally {')&&runner.includes('failures.length'),'Contract runner must enumerate failures instead of terminating on the first test');
console.log('PASS B960: 133 static presentations use 71 CSS owners with runtime styles preserved.');
