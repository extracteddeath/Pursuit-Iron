import assert from 'node:assert/strict';
import { buildSessionReview } from '../modules/session-tools.js';
import { withSessionPlanChoice } from '../modules/session-plan.js';
import { sessionSuggestion, prescribeSets } from '../modules/training-domain/prescriptions.js';
import { computeCell } from '../modules/training-domain/records.js';
import { EX_BY_ID, EXERCISES } from '../modules/training-domain/catalog.js';
import { planShorterSession } from '../modules/engine-api.js';
import { generateNextCycleForShell } from '../modules/next-engine/cycle-runtime-adapter.js';
import fs from 'node:fs';

export const program={id:'b904',name:'Session review',custom:true,weeks:6,config:{unit:'kg',goal:'both',experience:'intermediate',progression:'double',deload:false},
 days:[{id:'upper',label:'Upper',primaryIndex:0,exercises:['bb-bench','bb-row']}],overrides:{'upper:0':{sets:3,reps:'6-8',rir:'2',rest:90},'upper:1':{sets:3,reps:'8-12',rir:'2',rest:90}},progStyle:{'bb-bench':'double','bb-row':'dynamic'}};
export const history=[{id:'source',date:Date.now()-604800000,programId:program.id,dayId:'upper',dayLabel:'Upper',weekIndex:1,unit:'kg',
 perf:Object.fromEntries(['bb-bench','bb-row'].map(id=>[id,{weight:id==='bb-bench'?80:90,reps:8,sets:[1,2,3].map(()=>({w:id==='bb-bench'?80:90,r:8,rir:2,rirReported:true,tr:2}))}]))}];
const day=program.days[0],before=JSON.stringify({program,history});
const review=buildSessionReview(program,day,2,'kg',history),bench=review.exercises[0];
assert.equal(review.changes.length,1,'routine rep gains are not plan changes');assert.equal(review.changes[0].id,'bb-bench');assert.equal(review.exercises[1].change.changed,false,'row earned just a rep');assert.equal(bench.proposed[0].weight,'82.5');assert.equal(bench.proposed[0].reps,'6');
assert.match(bench.reason,/top of the rep range/);assert.equal(bench.change.canUndo,true);assert.equal(bench.opportunity,null,'planned RIR never inflates estimated record');
assert.equal(review.exercises[1].opportunity.type,'e1rm');assert.equal(review.exercises[1].opportunity.estimated,117);
const held=JSON.parse(JSON.stringify(withSessionPlanChoice(program,bench.change,true)));
const rereview=buildSessionReview(held,day,2,'kg',history);
assert.equal(rereview.exercises[0].change.undone,true);
assert.equal(rereview.exercises[0].proposedReason,bench.reason,'undo retains the original reason in the audit');
assert.equal(buildSessionReview(held,day,2,'kg',history.map(h=>({...h,updatedAt:Date.now()}))).exercises[0].change.undone,true,'sync revision metadata does not consume undo');
assert.equal(rereview.exercises[0].effective[0].weight,'80');assert.equal(rereview.exercises[0].effective[0].reps,'6','a load hold keeps current rep targets');
assert.equal(rereview.exercises[0].effective[0].valueOwner,'user');
assert.deepEqual(rereview.exercises[1].effective,review.exercises[1].effective,'undo is exercise-local');
assert.equal(JSON.stringify({program,history}),before,'history and program are immutable inputs');
assert.deepEqual(held.progStyle,program.progStyle);assert.deepEqual(held.overrides,program.overrides);
assert.equal(buildSessionReview(withSessionPlanChoice(held,bench.change,false),day,2,'kg',history).exercises[0].effective[0].weight,'82.5');
for(const variant of [
 {p:held,h:[{...history[0],updatedAt:Date.now(),perf:{...history[0].perf,'bb-bench':{...history[0].perf['bb-bench'],sets:history[0].perf['bb-bench'].sets.map(s=>({...s,r:7}))}}}],week:2},
 {p:held,h:[...history,{...history[0],id:'new',date:Date.now()}],week:2},
 {p:{...held,overrides:{...held.overrides,'upper:0':{...held.overrides['upper:0'],rir:'1'}}},h:history,week:2},
 {p:held,h:history,week:3}
])assert.equal(buildSessionReview(variant.p,day,variant.week,'kg',variant.h).exercises[0].change.undone,false,'new evidence or prescription expires undo');
assert.equal(buildSessionReview(held,day,2,'lb',history).exercises[0].change.undone,false,'unit/loading context is scoped');
assert.equal(buildSessionReview(program,day,1,'kg',[]).changes.length,0);
assert.ok(buildSessionReview(program,day,1,'kg',[]).exercises.every(row=>!row.opportunity));
assert.equal(buildSessionReview(program,day,2,'kg',history.map(h=>({...h,programId:'other'}))).changes.length,0,'other programs are not progression evidence');
assert.equal(buildSessionReview(program,day,2,'kg',history.map(h=>({...h,dayId:'elsewhere',dayLabel:'Other'}))).changes.length,0,'other days are not progression evidence');
const partial=structuredClone(history);partial[0].perf['bb-row'].sets=[{w:90,r:12,rir:2,rirReported:true},{w:90,r:8,rir:2,rirReported:true},{w:87.5,r:8,rir:2,rirReported:true}];
const sparseReview=structuredClone(history);
sparseReview[0].perf['bb-bench'].sets=[
  {w:80,r:8,rir:2,rirReported:true,tr:2,workingSetIndex:0},
  {w:77.5,r:7,rir:2,rirReported:true,tr:2,workingSetIndex:2}
];
const positional=buildSessionReview(program,day,2,'kg',sparseReview).exercises[0].change.previous;
assert.deepEqual(positional.map(row=>row?.weight??null),[80,null,77.5],
  'Plan Review must not move a completed third set into skipped set two');
const dd=buildSessionReview(program,day,2,'kg',partial).exercises[1];
assert.ok(dd.proposed[0].weight>90);assert.equal(dd.proposed[1].weight,'90');
const heldDD=buildSessionReview(withSessionPlanChoice(program,dd.change,true),day,2,'kg',partial).exercises[1];
assert.deepEqual(heldDD.effective.map(s=>Number(s.weight)),[90,90,87.5],'positional dynamic-double load hold');
assert.deepEqual(heldDD.effective.map(s=>Number(s.reps)),dd.proposed.map(s=>Number(s.reps)),'current per-set reps remain prescribed');
const percentage={...program,config:{...program.config,percentScheme:'531'},tm:{'bb-bench':100}};
const percentReview=buildSessionReview(percentage,day,2,'kg',history);
assert.equal(percentReview.exercises[0].change.canUndo,false,'authored percentage lift remains owned');
assert.equal(percentReview.exercises[1].change.canUndo,percentReview.exercises[1].change.loadChanged,'accessories can decline material load adjustments, not rep-only gains');
const deload={...program,weeks:1};assert.ok(buildSessionReview(deload,day,2,'kg',history).exercises.every(row=>!row.change.canUndo));
const short=planShorterSession(program,day,2,20,{history});const shortDay={...day,sessionLengthPlan:short};
const shortReview=buildSessionReview(program,shortDay,2,'kg',history);
for(const row of shortReview.exercises)assert.equal(row.effective.length,computeCell(program,shortDay,row.id,row.slot,2).sets);
const low=structuredClone(history);low[0].perf['bb-bench'].sets=low[0].perf['bb-bench'].sets.map(s=>({...s,r:3}));
assert.equal(buildSessionReview(program,day,2,'kg',low).exercises[0].change.canUndo,false,'undo cannot reintroduce reps outside the planned range');
const badGrid=structuredClone(history);badGrid[0].perf['bb-bench'].sets=badGrid[0].perf['bb-bench'].sets.map(s=>({...s,w:79.5}));
assert.equal(buildSessionReview(program,day,2,'kg',badGrid).exercises[0].change.canUndo,false,'undo cannot reintroduce an unavailable load');
const cfg=JSON.parse(fs.readFileSync(new URL('./m205-generation-parity-results.json',import.meta.url))).rows[3].config;let serial=0;
const generated=generateNextCycleForShell({templateId:'powerbuilding',config:cfg,legacyExercises:EXERCISES,seed:904,makeId:()=>`review-${++serial}`});
const gp=generated.allBlocks?.[0]||generated.blocks[0],gd=gp.days[0];
const gh=[{id:'generated-source',date:history[0].date,programId:gp.id,dayId:gd.id,dayLabel:gd.label,unit:'lb',weekIndex:1,
 perf:Object.fromEntries(gd.exercises.map((id,slot)=>{const cell=computeCell(gp,gd,id,slot,1);const range=String(cell.range||cell.reps).split(/[-–]/).map(Number);return [id,{weight:100,reps:range.at(-1),sets:Array.from({length:cell.sets},()=>({w:100,r:range.at(-1),rir:2,rirReported:true,tr:2}))}];}))}];
for(const row of buildSessionReview(gp,gd,2,'lb',gh).exercises){
 const sug=sessionSuggestion(gp,gd,row.slot,{},'lb',2,gh);
 const actual=prescribeSets(gp,gd,EX_BY_ID[row.id],row.slot,2,'lb',sug,{}, {},gh,false).filter(s=>!s.warm&&!s.sub);
 assert.deepEqual(row.effective,actual,'generated review and logger have one prescription');
 if(row.change.canUndo){const p=withSessionPlanChoice(gp,row.change,true);assert.equal(buildSessionReview(p,gd,2,'lb',gh).exercises.find(r=>r.id===row.id).change.undone,true);}
}
console.log('PASS B904 session review: actual reasons, positional undo/restore, stale-choice expiry, custom/generated logger parity, source/units, protected protocols and record semantics.');

const amplified={...program,overrides:{...program.overrides,'upper:1':{...program.overrides['upper:1'],techOverride:'Last set: myo-reps'}}};
const amp=buildSessionReview(amplified,day,2,'kg',history).exercises[1];
assert.equal(amp.protocol.type,'myo_reps');assert.equal(amp.protocol.miniSets.restSeconds,15);assert.equal(amp.effective.length,3,'minis do not become work sets or inflate the review/records');

for(const row of shortReview.exercises.filter(row=>row.change.canUndo)){
 const chosen=withSessionPlanChoice(program,row.change,true);
 const shortChosen=buildSessionReview(chosen,shortDay,2,'kg',history).exercises.find(r=>r.id===row.id);
 assert.equal(shortChosen.change.undone,true);assert.equal(shortChosen.effective.length,row.cell.sets);
}

for(const row of buildSessionReview(program,day,2,'lb',history).exercises.filter(row=>row.opportunity?.type==='e1rm')){
 assert.equal(row.opportunity.best,Math.round(row.opportunity.best*100)/100,'mixed-unit record display is stable');
 assert.ok(row.opportunity.estimated>row.opportunity.best+.01);
}

const { workoutTechniqueRest } = await import('../modules/workout-context.js');
assert.equal(workoutTechniqueRest({kind:'myo'}),15);
assert.equal(workoutTechniqueRest({kind:'drop'}),10);
assert.equal(workoutTechniqueRest({kind:'myo'},1.5),23);
assert.equal(workoutTechniqueRest({kind:'drop'},1.5),15);

// M325: the alternate lift owns its evidence, equipment boundary and one-exposure choice.
export const alternate={...program,id:'m325-alternate',config:{...program.config,unit:'lb'},
 pairs:{'upper:0':'db-bench'},progStyle:{...program.progStyle,'db-bench':'double'}};
const alternateDay=alternate.days[0];
export const alternateHistory=[{id:'alternate-source',date:1700000000000,updatedAt:1700000000000,
 programId:alternate.id,dayId:alternateDay.id,weekIndex:2,unit:'lb',perf:{
  'db-bench':{weight:50,reps:8,sets:Array.from({length:3},()=>({w:50,r:8,rir:2,rirReported:true,tr:2}))},
  'bb-bench':{weight:200,reps:8,sets:Array.from({length:3},()=>({w:200,r:8,rir:2,rirReported:true,tr:2}))}}}];
const alternateBefore=JSON.stringify({alternate,alternateHistory});
const alternateReview=buildSessionReview(alternate,alternateDay,4,'lb',alternateHistory);
const alternateBench=alternateReview.exercises[0];
assert.equal(alternateBench.id,'db-bench');
assert.deepEqual(alternateBench.proposed.map(s=>[s.weight,s.reps]),[['55','6'],['55','6'],['55','6']]);
assert.equal(alternateBench.change.canUndo,true,'effective alternate lift can decline progression');
assert.equal(alternateBench.change.source.id,'alternate-source');
const alternateHeld=JSON.parse(JSON.stringify(withSessionPlanChoice(alternate,alternateBench.change,true)));
const heldAlternateReview=buildSessionReview(alternateHeld,alternateDay,4,'lb',alternateHistory);
assert.equal(heldAlternateReview.exercises[0].change.undone,true);
assert.deepEqual(heldAlternateReview.exercises[0].effective.map(s=>[s.weight,s.reps]),[['50','6'],['50','6'],['50','6']]);
const alternateSuggestion=sessionSuggestion(alternateHeld,alternateDay,0,{},'lb',4,alternateHistory);
const alternateLogged=prescribeSets(alternateHeld,alternateDay,EX_BY_ID['db-bench'],0,4,'lb',alternateSuggestion,{}, {},alternateHistory,false);
assert.deepEqual(alternateLogged.filter(s=>!s.warm&&!s.sub),heldAlternateReview.exercises[0].effective,'review choice is the logger prescription');
assert.equal(buildSessionReview(alternateHeld,alternateDay,3,'lb',alternateHistory).exercises[0].id,'bb-bench');
assert.equal(buildSessionReview(alternateHeld,alternateDay,3,'lb',alternateHistory).exercises[0].change.undone,false,'base week never inherits alternate choice');
assert.equal(buildSessionReview({...alternateHeld,pairs:{}},alternateDay,4,'lb',alternateHistory).exercises[0].change.undone,false,'changed exercise expires choice');
assert.equal(buildSessionReview(alternateHeld,alternateDay,4,'lb',alternateHistory.map(h=>({...h,updatedAt:h.updatedAt+1}))).exercises[0].change.undone,true);
assert.deepEqual(buildSessionReview(withSessionPlanChoice(alternateHeld,alternateBench.change,false),alternateDay,4,'lb',alternateHistory).exercises[0].effective,alternateBench.effective);
assert.equal(JSON.stringify({alternate,alternateHistory}),alternateBefore);
console.log('PASS M325 alternate-week review: actual exercise evidence, load grid, undo/restore, reload, base-week isolation and logger parity.');
