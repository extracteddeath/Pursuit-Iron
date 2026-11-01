import assert from 'node:assert/strict';
import { EX_BY_ID, sessionSuggestion, prescribeSets, loadableAtOrBelow, setGymLimits,
  auditSetDecisionTraces, oneShotDeloadFactor, consumeOneShotDeload, setLifterProfile } from '../modules/engine-api.js';

const program={id:'deload-program',custom:true,updatedAt:500,weeks:6,
  config:{unit:'lb',weeks:6,goal:'both',experience:'intermediate',progression:'auto',deload:false,
    equipment:['cable','machine','barbell','bench','rack']},
  days:[{id:'push',label:'Push',primaryIndex:-1,exercises:['cable-fly']}],
  overrides:{'push:0':{sets:3,reps:'8-12',rir:'2',progressionStyle:'double',techOverride:null}}};
const day=program.days[0],ex=EX_BY_ID['cable-fly'];
const history=[{id:'last',programId:program.id,dayId:day.id,date:100,weekIndex:1,unit:'lb',
  perf:{[ex.id]:{weight:100,reps:8,sets:[{w:100,r:8,rir:2},{w:100,r:8,rir:2},{w:100,r:8,rir:2}]}}}];
const armed={...program,deloadNext:{[ex.id]:.9,'bb-bench':.9}};
const before=JSON.stringify({program,armed,history});
const rows=(p,s=sessionSuggestion(p,day,0,null,'lb',1,history),hs=history)=>
  prescribeSets(p,day,ex,0,1,'lb',s,{}, {},hs,true);
const baseline=rows(program),suggestion=sessionSuggestion(armed,day,0,null,'lb',1,history);
assert.equal(suggestion.action,'deload');
assert.equal(suggestion.reasonCode,'user_one_shot_deload');
const reduced=rows(armed,suggestion);
assert.equal(reduced.length,baseline.length);
for(const [i,row] of reduced.entries()) {
  assert.equal(Number(row.weight),loadableAtOrBelow(ex,Number(baseline[i].weight)*.9,'lb'));
  assert.equal(row.reps,baseline[i].reps);
  assert.equal(row.target.rir,baseline[i].target.rir);
}
assert.deepEqual(auditSetDecisionTraces(reduced),[]);
assert.ok(reduced.filter(row=>!row.warm).every(row=>
  row.target.decisionTrace.evaluation.action==='deload' && row.target.nextAction==='deload'));
assert.deepEqual(rows(armed),reduced,'reopening the same pending source does not compound the reduction');
assert.deepEqual(rows(armed,sessionSuggestion(program,day,0,null,'lb',1,history)),reduced,
  'the row boundary also handles an unadjusted suggestion');

const consumed=consumeOneShotDeload(armed,{[ex.id]:{sets:[{w:90,r:8}]}},500);
assert.equal(consumed.updatedAt,501,'a same-time completion still wins the per-record merge');
assert.deepEqual(consumed.deloadNext,{'bb-bench':.9},'untrained exercises keep their choice');
assert.equal(consumeOneShotDeload(armed,{},1000),armed,'skipping the exercise cannot consume it');
assert.equal(consumeOneShotDeload(consumed,{[ex.id]:{}},1000),consumed,'consumption is idempotent');
assert.equal(consumeOneShotDeload({...armed,deloadNext:{[ex.id]:.9}}, {[ex.id]:{}},1000).deloadNext,undefined);
assert.equal(JSON.stringify({program,armed,history}),before,'targets and completion cannot mutate the source');
for(const invalid of [true,false,null,undefined,'0.9',0,1,-1,NaN,Infinity])
  assert.equal(oneShotDeloadFactor({...program,deloadNext:{[ex.id]:invalid}},ex.id),null);
assert.equal(oneShotDeloadFactor({deloadNext:{'assisted-pullup':.9}},'assisted-pullup'),null);

// A real equipment rack and a coarse stack step remain authoritative.
setGymLimits({unit:'lb',inventory:{machine:[25,50,75,100]},limits:{}});
try {
  const rack=rows(armed);
  assert.ok(rack.filter(row=>!row.warm).every(row=>row.weight==='75'));
  assert.deepEqual(auditSetDecisionTraces(rack),[]);
} finally { setGymLimits(null); }
for(const technique of ['myo-reps','drop set']) {
  const p=structuredClone(armed);p.overrides['push:0'].techOverride=technique;
  const targets=rows(p),work=targets.filter(row=>!row.warm&&!row.sub),extensions=targets.filter(row=>row.sub);
  assert.ok(extensions.length>0);
  for(const extension of extensions) assert.equal(Number(extension.weight),extension.kind==='myo'
    ? Number(work.at(-1).weight) : loadableAtOrBelow(ex,Number(work.at(-1).weight)*extension.dropF,'lb'));
}
// The final row boundary also owns first-use estimates and percentage targets,
// neither of which necessarily uses the session suggestion's load.
setLifterProfile({bodyweight:180,bwUnit:'lb',sex:'male',experience:'intermediate'});
try {
  const firstBase=rows(program,null,[]),firstDeload=rows(armed,null,[]);
  assert.ok(firstBase.some(row=>Number(row.weight)>0));
  for(const [i,row] of firstDeload.entries())
    assert.equal(Number(row.weight),loadableAtOrBelow(ex,Number(firstBase[i].weight)*.9,'lb'));
  assert.deepEqual(auditSetDecisionTraces(firstDeload),[]);
} finally { setLifterProfile(null); }
const percentDay={id:'percent',label:'Upper',primaryIndex:0,exercises:['bb-bench']};
const percent={id:'percent-program',custom:true,weeks:4,days:[percentDay],config:{unit:'lb',weeks:4,goal:'strength',percentScheme:'531'},
  trainingMax:{'bb-bench':200},overrides:{}};
const percentRows=p=>prescribeSets(p,percentDay,EX_BY_ID['bb-bench'],0,1,'lb',null,{}, {},[],false);
const percentBase=percentRows(percent),percentReduced=percentRows({...percent,deloadNext:{'bb-bench':.9}});
assert.ok(percentBase.some(row=>Number(row.weight)>0));
assert.equal(percentReduced.length,percentBase.length);
for(const [i,row] of percentReduced.entries()) {
  assert.equal(Number(row.weight),loadableAtOrBelow(EX_BY_ID['bb-bench'],Number(percentBase[i].weight)*.9,'lb'));
  assert.equal(row.reps,percentBase[i].reps);
  assert.equal(row.target.amrap,percentBase[i].target.amrap);
}
assert.deepEqual(auditSetDecisionTraces(percentReduced),[]);
console.log('PASS shared one-shot deload: equipment loads, once-only prescription, traces, extensions, untrained choices and monotonic consumption');
