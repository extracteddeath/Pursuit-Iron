import assert from 'node:assert/strict';
import fs from 'node:fs';
import { inspectCascade } from '../scripts/audit-css-cascade-ownership.mjs';

const fixture='.tile{color:red} .tile{color:blue} @media(max-width:600px){.tile{color:green}.tile{opacity:1}}';
const sample=inspectCascade(fixture);
assert.equal(sample.scopedRepeatedSelectors,2,'At-rule scopes must not be mixed');
assert.equal(sample.potentialSamePropertyOverrides,1,'Only matching property conflicts should be flagged');
assert.equal(sample.hotspots[0].repeatedProperties[0],'color','Shared paint override detected');
const css=fs.readFileSync('app.css','utf8');
const result=inspectCascade(css);
assert.equal(result.cssCharacters,css.length,'Source inventory measures current stylesheet');
assert.equal(result.priorityDeclarations,2024,'No new CSS priority debt');
assert.ok(result.rules>3000 && result.scopedRepeatedSelectors>50,
  'Inventory must cover legacy CSS rather than only the new component layer');
for(const key of ['home','plan','settings','progress','profile','workout'])
  assert.ok(result.areas[key].rules>0,'Missing CSS scope for '+key);
assert.ok(result.hotspots.every(h=>h.locations.length===h.blocks),
  'Every collision retains traceable source offsets');
console.log('PASS CSS cascade source inventory: scoped duplicates and per-screen debt measured.');
