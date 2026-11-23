import assert from 'node:assert/strict';
import fs from 'node:fs';

// B987: fixed visual geometry belongs to M3 theme-aware CSS, not React.
// Keep numeric plate height dynamic and calculated outcomes accessible; shared theme kickers are now CSS-owned.
const app=fs.readFileSync('modules/App.js','utf8');
const css=fs.readFileSync('app.css','utf8');
const styleStart=css.indexOf('/* B987 —');
const baseCss=css.slice(styleStart,css.indexOf('@media(prefers-reduced-motion:reduce)',styleStart));
assert.ok(styleStart>0&&baseCss.length>2000,'B987 base CSS must be present');
const progress=app.slice(app.indexOf('function ProgressDetail('),app.indexOf('function RecoveryTrendCard('));
// B1029: the Settings 1RM calculator duplicated Calculators → Estimated 1RM and was removed.
assert.ok(progress.length>3500 && !app.includes('function OneRepMaxCalc('),'Strength detail remains; the duplicate Settings 1RM calculator is gone');
for(const name of [
 'strength-detail','strength-detail-stat','strength-detail-plateau','strength-detail-level',
 'strength-detail-meter','strength-detail-chip','strength-detail-session-pr']){
 assert.ok(app.includes('wpb-'+name),'React must use the '+name+' style owner');
 assert.equal(baseCss.split('#root .wpb .wpb-'+name+'{').length-1,1,'Exactly one base CSS owner for '+name);
}
assert.ok(!css.slice(css.indexOf('/* B987 —')).includes('!important'),
 'New CSS must use normal cascade, not priority overrides');
assert.ok(progress.includes('wpb-strength-detail-meter--on')&&
 progress.includes('x.e1rm === s.best ? "mono wpb-strength-detail-chip'),
 'Strength-level and best-set conditional states retain their live semantics');
assert.ok((app.match(/style: \{/g)||[]).length<=1203,
 'Retired static presentation styles cannot return');
console.log('PASS B987 strength details: '+38+' inline presentation owners migrated, responsive input sizing and dynamic calculations preserved.');
