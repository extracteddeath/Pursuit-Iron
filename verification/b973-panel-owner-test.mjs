import assert from 'node:assert/strict';
import fs from 'node:fs';
const app=fs.readFileSync('modules/App.js','utf8'),css=fs.readFileSync('app.css','utf8');
const p=app.slice(app.indexOf('function ProgramView('),app.indexOf('function WorkoutSession('));
const w=app.slice(app.indexOf('function WorkoutSession('),app.indexOf('function WorkoutSession(')+350000);
for(const cls of ['wpb-b973-amp-panel','wpb-b973-amp-toggle','wpb-b973-day-note','wpb-b973-day-preview'])assert.ok(p.includes(cls)&&css.includes('.'+cls+'{'),cls);
for(const cls of ['wpb-b973-set-sheet','wpb-b973-set-type','wpb-b973-set-locked','wpb-b973-coach-collapsed','wpb-b973-coach-open'])assert.ok(w.includes(cls)&&css.includes('.'+cls+'{'),cls);
for(const required of ['setTechInfo(open ? null : key)','changeSessionSetType(ei, si, opt.id)','setCoachExpand(true)','setCoachExpand(false)','"aria-pressed": currentType === opt.id'])assert.ok(p.includes(required)||w.includes(required),required);
assert.ok((app.match(/style: \{/g)||[]).length<=1308);
assert.ok((css.match(/!important/g)||[]).length<=2060);
for(const [selector,min] of [
 ['.wpb-b973-set-close','width:48px;height:48px'],
 ['.wpb-b973-coach-collapsed','min-height:48px'],
 ['.wpb-b973-coach-toggle','min-height:48px']
]){
  const start=css.indexOf(selector+'{'),end=css.indexOf('}',start);
  assert.ok(start>=0&&css.slice(start,end).includes(min),
    'Workout set/edit control retains a real 48px CSS owner: '+selector);
}
assert.ok(w.includes('children: ["Based on ", f.evidence]'),'Coach evidence copy must remain syntactically complete');
assert.ok(w.includes('className: "pressable wpb-b973-coach-collapsed"')&&w.includes('className: "pressable wpb-b973-coach-toggle"'),'Coach disclosure keeps existing pressable control semantics');
console.log('PASS B973: Program exercise and workout set-panel ownership');
