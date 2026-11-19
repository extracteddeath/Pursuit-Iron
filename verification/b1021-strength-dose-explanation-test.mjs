import assert from 'node:assert/strict';
import fs from 'node:fs';
import { describeStrengthDoseLimit } from '../modules/cycle-strength-dose-surface.js';

const source = {
  comparison:'last_hypertrophy_week_to_first_strength_week',
  retention:.6, status:'limited',
  requested:{upper_back:6,side_delts:5,core:3},
  target:{upper_back:4,side_delts:5,core:3},
  actual:{upper_back:3.5,side_delts:4.5,core:3},
  unresolved:['upper_back','side_delts'],
  limits:[
    {region:'upper_back',requested:6,feasible:4,actual:3.5,reasonCode:'SHARED_MUSCLE_CEILING'},
    {region:'side_delts',requested:5,feasible:5,actual:4.5,reasonCode:'NO_SAFE_ADDITION'}
  ]
};
const before=JSON.stringify(source),limited=describeStrengthDoseLimit(source);
assert.equal(limited.status,'limited');
assert.equal(limited.heading,'Strength volume · 2 areas below goal');
assert.deepEqual(limited.details,[
  {region:'upper_back',label:'Upper back',requested:'6',feasible:'4',actual:'3.5',reason:'Shared muscle recovery limit'},
  {region:'side_delts',label:'Side delts',requested:'5',feasible:'5',actual:'4.5',reason:'No safe addition fits this block'}
]);
assert.match(limited.caveat,/Original generated first-week estimate/);
assert.ok(!limited.explanation.includes('add sets'),'The view must not prescribe unaudited new volume');
assert.equal(JSON.stringify(source),before,'read-only explanation never alters generated engine evidence');
const met={...source,status:'met',unresolved:[],limits:[],requested:{lats:4},target:{lats:4},actual:{lats:4}};
assert.equal(describeStrengthDoseLimit(met).heading,'Strength volume · planned goal covered');
assert.equal(describeStrengthDoseLimit(met).details.length,0);
assert.equal(describeStrengthDoseLimit(null),null);
assert.equal(describeStrengthDoseLimit({...source,comparison:'week_to_week'}),null);
assert.equal(describeStrengthDoseLimit({...source,actual:null}),null);
assert.equal(describeStrengthDoseLimit({...source,actual:{upper_back:NaN,side_delts:4.5}}).missing,1);
const unknown=describeStrengthDoseLimit({...source,limits:[{region:'upper_back',reasonCode:'unrecognized'}]});
assert.equal(unknown.details[0].reason,'Constraint not specified','unknown code must not invent gym or medical limitations');

// Both actual cycle routes must use the same saved program record.
// Existing muscle change and saved-week summaries remain separate.
const app=fs.readFileSync(new URL('../modules/App.js',import.meta.url),'utf8');
const css=fs.readFileSync(new URL('../app.css',import.meta.url),'utf8');
const component=fs.readFileSync(new URL('../modules/cycle-strength-dose-react.js',import.meta.url),'utf8');
assert.match(app,/import \{ CycleStrengthDoseSummary \} from '\.\/cycle-strength-dose-react\.js';/);
assert.match(component,/export function CycleStrengthDoseSummary\(\{ program \}\)/);
assert.match(component,/describeStrengthDoseLimit\(program\?\.nextEngine\?\.strengthEntryDose\)/);
assert.match(app,/_jsx\(CycleStrengthDoseSummary, \{ program \}\)/);
assert.match(app,/_jsx\(CycleStrengthDoseSummary, \{ program: next \}\)/);
assert.match(component,/data-strength-dose-status/);
assert.match(css,/\.wpb-cycle-week-outlook-detail\{/);
assert.match(css,/\.wpb-cycle-dose-caption\{/);
assert.match(component,/report\.caveat/);
console.log('PASS B1021 original-vs-feasible Strength retention explanation, route ownership and accessible read-only disclosure.');
