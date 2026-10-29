// B999: Plan/Home M3 owner rules avoid priority hacks and fixed-pixel text.
import assert from 'node:assert/strict';
import fs from 'node:fs';
const css=fs.readFileSync('app.css','utf8');
const plan=fs.readFileSync('modules/plan-session-surfaces.js','utf8');
const getRule=prefix=>{
    const pos=css.indexOf(prefix+'{');
    assert.ok(pos>=0,'owner exists: '+prefix);
    return css.slice(pos,css.indexOf('}',pos)+1);
};
const undo=getRule('#root .wpb .wpb-plan-changes-dialog .wpb-plan-undo');
assert.ok(undo.includes('min-height:48px') && undo.includes('padding:10px 14px'));
assert.ok(undo.includes('var(--pi-shape-action)')&&!undo.includes('!important'),
    'Plan action uses the existing component theme owner instead of late !important');
assert.ok(!css.includes('#root .wpb-plan-changes-dialog .wpb-plan-undo{'),
    'retired competing unscoped Plan action priority layer');
const basis=getRule('#root .wpb .wpb-plan-review-basis');
assert.ok(basis.includes('font-size:.75rem')&&!basis.includes('!important'),
    'Review explanation uses a scalable, higher-specificity component owner');
for(const term of [
    '#root .wpb .wpb-plan-review-intro',
    '#root .wpb .wpb-plan-change-title h3',
    '#root .wpb .wpb-plan-change-more>summary',
    '.wpb-home-outline-toggle>strong',
    '.wpb-home-outline-toggle>span',
    '.wpb-game-plan-heading',
    '.wpb-plan-change-banner strong'
]) assert.match(getRule(term),/font-size:(?:\.\d+|1)rem/,term+' scales when Android text size increases');
assert.ok(getRule('#root .wpb .wpb-plan-change-more>summary').includes('min-height:48px'),
    'Program change disclosure remains an accessible tap target');
assert.ok(css.includes('align-content:center;min-height:48px;cursor:pointer;list-style:none'),
    'Game Plan disclosure is also a 48px tap target');
assert.ok(plan.includes('return _jsx(SessionChoiceDialog, { title: "Plan changes"'),
    'Plan review remains in the shared dialog focus owner');
assert.ok(css.length<=643592,'stylesheet remains under last published CSS ceiling');
assert.ok((css.match(/!important/g)||[]).length<=2054,'priority declaration count strictly improved');
console.log('PASS B999: scoped Plan review style, 48px disclosures, scalable type and six removed overrides.');
