import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const owners=[...css.matchAll(/#root \.wpb \.(wpb-b964-layout-\d{2}),#root \.\1\{([^}]+)\}/g)];
assert.equal(owners.length,35,'B964 must retain all 35 canonical static layout classes');
assert.equal(new Set(owners.map(m=>m[1])).size,35,'layout selectors have one CSS owner');
for(const [,name,body] of owners){
 assert.ok(body && !body.includes('!important'),'B964 does not introduce priority overrides: '+name);
 assert.ok((app.match(new RegExp(name,'g'))||[]).length>=1,'retired layout must use its class: '+name);
 assert.equal((css.match(new RegExp('#root \\.wpb \\.'+name+',','g'))||[]).length,1,'one CSS declaration per class: '+name);
}
assert.ok((app.match(/style: \{/g)||[]).length<=1433,'B964 must never restore its 36 retired JSX inline style objects');
const release=JSON.parse(fs.readFileSync('RELEASE_MANIFEST.json','utf8'));
assert.ok(release.build>=963,'B964 has a valid release lineage');
console.log('PASS B964: 36 literal layout objects retired to 35 CSS-owned selectors without priority overrides.');
