import assert from 'node:assert/strict';
import fs from 'node:fs';

// B987: fixed visual geometry belongs to M3 theme-aware CSS, not React.
// Keep numeric plate height and outcome colours dynamic and accessible.
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const progress=app.slice(app.indexOf('function ProgressDetail('),app.indexOf('function OneRepMaxCalc('));
const calc=app.slice(app.indexOf('function OneRepMaxCalc('),app.indexOf('// In-app diagnostics:'));
assert.ok(progress.length>3500&&calc.length>2000,'Both strength panels must remain present');
for(const name of ['rm-card','rm-field-label','rm-input','rm-summary','rm-plate','rm-load','rm-explainer',
 'strength-detail','strength-detail-stat','strength-detail-plateau','strength-detail-level',
 'strength-detail-meter','strength-detail-chip','strength-detail-session-pr']){
 assert.ok(app.includes('wpb-'+name),'React must use the '+name+' style owner');
 assert.equal(css.split('#root .wpb .wpb-'+name+'{').length-1,1,'Exactly one CSS source for '+name);
}
assert.ok(!css.slice(css.indexOf('/* B987 —')).includes('!important'),
 'New CSS must use normal cascade, not priority overrides');
assert.equal((calc.match(/style: \\{/g)||[]).length,4,
 'Only plate height and existing eyebrow options remain calculated or theme-tied');
assert.ok(calc.includes('style: { height: 22 + (p / maxP) * 32 }'),'Plate height must still scale by plate value');
assert.ok(calc.includes('font')===true&&calc.includes('round(rmAt(orm, n))'),
 'All 1RM and rep target calculations remain owned by the calculator');
assert.ok(css.includes('.wpb-rm-input{')&&css.includes('font-size:16px'),
 'Mobile 1RM inputs must not trigger focus zoom');
assert.ok(progress.includes('wpb-strength-detail-meter--on')&&
 progress.includes('x.e1rm === s.best ? "mono wpb-strength-detail-chip'),
 'Strength-level and best-set conditional states retain their live semantics');
assert.ok((app.match(/style: \\{/g)||[]).length<=1224,
 'Retired static presentation styles cannot return');
console.log('PASS B987 strength details: '+38+' inline presentation owners migrated, responsive input sizing and dynamic calculations preserved.');
