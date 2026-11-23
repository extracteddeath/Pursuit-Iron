import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
assert.ok(app.split('style: {').length-1<=1486,'B961 permanently reduces static JSX style debt');
const overlaySources=app+'\n'+fs.readFileSync('modules/exercise-picker-sheet.js','utf8');
const owners=[...overlaySources.matchAll(/wpb-b961-overlay-(\d{2})/g)].map(x=>x[1]);
const styles=[...css.matchAll(/#root \.wpb \.wpb-backdrop\.wpb-b961-overlay-(\d{2})\{([^{}]+)\}/g)];
// B1028: the exercise picker moved onto UiSheet, retiring overlay-06.
assert.equal(owners.length,24,'24 modal call sites are class-owned');
assert.equal(new Set(owners).size,styles.length,'one CSS owner for each overlay variant');
for(const [_,number,rule] of styles){
 assert.ok(owners.includes(number),'orphan overlay rule '+number);
 for(const required of ['position:fixed','inset:0','z-index:'])assert.ok(rule.includes(required),'overlay lost '+required);
 assert.ok(!rule.includes('!important'),'no priority overrides');
}
assert.equal((app.match(/className: "wpb-b961-leading-icon"/g)||[]).length,11,'11 informational glyphs reuse one offset');
assert.ok(css.includes('svg.wpb-b961-leading-icon{flex-shrink:0;margin-top:1px}'),'icon owner preserves geometry');
assert.ok(!app.includes('style: { flexShrink: 0, marginTop: 1 }'),'old icon styles retired');
console.log('PASS B961: 25 modal overlays, stable z-depth and 11 glyphs retain one CSS owner without priority overrides.');
