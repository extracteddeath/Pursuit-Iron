import assert from 'node:assert/strict';
import { sessionSuggestion, prescribeSets, computeCell, resolveStyle, EX_BY_ID, applyCustomProgramSettings, parseStoredData, STORE_VERSION, customProgramSettingsIssues, editableProgramWeeks, canonicalRepRange, parseManualTargets, withManualProgramTargets, programPrescriptionIssues } from '../modules/engine-api.js';

import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
export const program = {id:'m222-custom',custom:true,weeks:10,config:{unit:'lb',weeks:10,goal:'both',experience:'intermediate',progression:'auto',deload:false,equipment:['cable','machine','barbell','bench','rack']},
    days:[{id:'push',label:'Push',primaryIndex:-1,exercises:['cable-fly']}],
    overrides:{'push:0':{sets:4,reps:'10-20',rir:'2',progressionStyle:'double',techOverride:null}}, ss:{'push:0':true}};
export const history = [{id:'fly-last',programId:program.id,dayId:'push',date:2000,weekIndex:1,unit:'lb',perf:{'cable-fly':{weight:25,reps:13,sets:[{w:25,r:19,rir:2},{w:25,r:10,rir:2},{w:20,r:11,rir:2},{w:25,r:10,rir:2}]}}}];
const day=program.days[0],ex=EX_BY_ID['cable-fly'];
const malformedTargets=structuredClone(program);
malformedTargets.overrides['push:0'].reps='10-20oops';
assert.equal(computeCell(malformedTargets,malformedTargets.days[0],ex.id,0,1).missing,true,
    'An invalid authored range must not become a lower single-rep target that could earn progression');
assert.deepEqual(prescribeSets(malformedTargets,malformedTargets.days[0],ex,0,1,'lb',null,{}, {},[],false),[],
    'An invalid authored prescription cannot create guessed working sets');
// Complete target parsing and immutable per-field ownership. Accepted formats are
// canonical counts/ranges; malformed suffixes must never lower the overload bar.
for (const [raw, expected] of [[8,[8,8]], [' 6 – 10 ',[6,10]], ['6—10',[6,10]], [[6,10],[6,10]], [[8],[8,8]], ['08',[8,8]]])
    assert.deepEqual(canonicalRepRange(raw), expected);
for (const invalid of ['', '10-20oops', '10-', '-10', '10-6', '0', '6.5', '6,10', '6-10-12', '1e2', Infinity, true, {}, [], [6,10,12], [[6,10]], [0,10]]) {
    assert.equal(canonicalRepRange(invalid), null, `Reject incomplete rep target ${JSON.stringify(invalid)}`);
    const bad = structuredClone(program); bad.overrides['push:0'].reps = invalid;
    assert.equal(computeCell(bad, day, ex.id, 0, 1).prescriptionInvalid, true);
    assert.equal(prescribeSets(bad, day, ex, 0, 1, 'lb', null, {}, {}, history, false).length, 0);
    assert.equal(programPrescriptionIssues(bad, day, 1)[0].exerciseId, ex.id);
    assert.equal(sessionSuggestion(bad, day, 0, null, 'lb', 1, history),null,'Invalid current targets cannot award progression through a neutral fallback');
}
const manual = {...structuredClone(program), config:{...program.config, progression:'manual'}};
manual.overrides['push:0'] = {...manual.overrides['push:0'], sets:[3,4,5,6], reps:[10,20], rest:123,
    techOverride:'Last set: drop set', prescriptionOwners:{sets:'user',rest:'user',tech:'user'}};
const manualInput = structuredClone(manual);
for (const raw of ['', 0, 21, 1.5, '2oops', '1e1', Infinity, true]) {
    const result = withManualProgramTargets(manual, day.id, 0, 2, {sets:raw,reps:'10-20'});
    assert.equal(result.program, manual); assert.ok(result.errors.sets);
}
assert.equal(parseManualTargets({sets:'20',reps:'6–10'}).valid,true);
assert.equal(parseManualTargets({sets:'2',reps:'10-20oops'}).valid,false);
const same = withManualProgramTargets(manual, day.id, 0, 2, {sets:'4',reps:'10–20'});
assert.equal(same.program, manual, 'Semantic no-op retains weekly arrays and object identity');
const targets = withManualProgramTargets(manual, day.id, 0, 2, {sets:'4',reps:'8–12'}).program;
assert.deepEqual(manual,manualInput,'Manual edits cannot mutate their source');
assert.equal(targets.days,manual.days); assert.equal(targets.overrides['push:0'].sets,manual.overrides['push:0'].sets);
assert.equal(targets.overrides['push:0'].rest,123); assert.equal(targets.overrides['push:0'].rir,'2');
assert.equal(targets.overrides['push:0'].techOverride,manual.overrides['push:0'].techOverride);
assert.deepEqual(targets.overrides['push:0'].prescriptionOwners,{...manual.overrides['push:0'].prescriptionOwners,reps:'user'});
for(let week=1;week<=4;week++) {
    const cell=computeCell(targets,day,ex.id,0,week);
    assert.equal(cell.sets,week+2); assert.equal(cell.reps,'8-12');
    assert.equal(prescribeSets(targets,day,ex,0,week,'lb',null,{}, {},[],false).filter(s=>!s.sub&&!s.warm).length,week+2);
}
const setOnly = withManualProgramTargets(manual,day.id,0,2,{sets:'7',reps:'10-20'}).program;
assert.equal(setOnly.overrides['push:0'].sets,7);assert.equal(setOnly.overrides['push:0'].reps,manual.overrides['push:0'].reps);
assert.equal(setOnly.overrides['push:0'].prescriptionOwners.reps,undefined);
assert.equal(withManualProgramTargets(manual,day.id,0,1,{sets:'3',reps:'8'},'wrong-exercise').program,manual);
assert.equal(withManualProgramTargets(program,day.id,0,1,{sets:'3',reps:'8'}).program,program,'Auto targets cannot be silently edited through the manual transaction');
assert.deepEqual(programPrescriptionIssues(program,day,1),[]);
export function rowsFor(p=program,hs=history){const s=sessionSuggestion(p,p.days[0],0,null,'lb',1,hs);return {suggestion:s,rows:prescribeSets(p,p.days[0],ex,0,1,'lb',s,{}, {},hs,false)};}
const damagedHistory=structuredClone(history);
damagedHistory[0].perf[ex.id].prescription={schemaVersion:1,exerciseId:ex.id,sets:4,reps:'10-20oops',rir:[2,2],rest:90};
assert.deepEqual({...rowsFor(program,damagedHistory).suggestion,last:undefined},{...rowsFor(program,history).suggestion,last:undefined},
    'A malformed historical target cannot replace the complete current target during progression');
const fly=rowsFor();assert.deepEqual(fly.rows.map(s=>[s.weight,s.reps]),[['25','20'],['25','11'],['20','12'],['25','11']],
    'Rep-only progression must not raise the lighter working set to the session maximum');
assert.ok(fly.rows.every(s=>s.target.w===s.weight&&s.target.prefillReps===s.reps));
const dynamic=structuredClone(program);dynamic.progStyle={'cable-fly':'dynamic'};
assert.equal(resolveStyle(dynamic,ex,false,1,{},history,day.id),'dynamic');
assert.equal(computeCell(dynamic,day,ex.id,0,1).progressionStyle,'dynamic');
assert.deepEqual(rowsFor(dynamic).rows.map(s=>[s.weight,s.reps]),[['25','20'],['25','11'],['20','12'],['25','11']],'DDP retains each positional load, and advances its own reps');
const safety=structuredClone(program);safety.overrides['push:0'].reps='10-15';
const hard=structuredClone(history);hard[0].perf['cable-fly'].sets=Array.from({length:4},()=>({w:25,r:8,rir:0}));
const reduced=rowsFor(safety,hard);assert.equal(reduced.suggestion.action,'hold_load');assert.ok(reduced.rows.every(s=>s.weight==='25'&&s.reps==='10'),'rep-first double preserves previous working load');
assert.equal(reduced.suggestion.optionalLowerLoad,20,'Lower load only as advice');
const effort=structuredClone(history);effort[0].perf['cable-fly'].sets=Array.from({length:4},()=>({w:25,r:20,rir:0}));
assert.equal(rowsFor(program,effort).suggestion.weight,25,'failure effort cannot earn a load increase');
const incomplete=structuredClone(effort);incomplete[0].perf['cable-fly'].sets=incomplete[0].perf['cable-fly'].sets.slice(0,3);assert.notEqual(rowsFor(program,incomplete).suggestion.action,'increase_load');
const mixed=structuredClone(history);mixed[0].perf['cable-fly'].sets.forEach(s=>s.r=20);assert.notEqual(rowsFor(program,mixed).suggestion.action,'increase_load','lighter sets cannot qualify the heaviest load');
const relabeledHistory=structuredClone(history);relabeledHistory[0].dayLabel='Push';
const regenerated=structuredClone(program);regenerated.days[0].id='push-regenerated';regenerated.overrides['push-regenerated:0']={...regenerated.overrides['push:0']};
const regeneratedSuggestion=sessionSuggestion(regenerated,regenerated.days[0],0,null,'lb',1,relabeledHistory);
assert.notEqual(regeneratedSuggestion?.referenceOnly,true,'a unique stable day label may recover comparable history after a day-id migration');
const ambiguous=structuredClone(program);ambiguous.days.push({id:'push-2',label:'Push',primaryIndex:-1,exercises:['cable-fly']});
ambiguous.overrides['push-2:0']={...ambiguous.overrides['push:0']};
const crossDay=sessionSuggestion(ambiguous,ambiguous.days[1],0,null,'lb',1,relabeledHistory);
assert.equal(crossDay?.referenceOnly,true,'duplicate labels cannot make another day comparable');
assert.equal(crossDay?.action,'initial');assert.equal(crossDay?.weight,25);
const input=history[0].perf['cable-fly'].sets.map((s,i)=>({exerciseId:ex.id,setIndex:i,load:s.w,reps:s.r,rir:s.rir}));
const engine=evaluateWorkoutProgression({exercises:[{exerciseId:ex.id,name:ex.name,sets:4,role:'accessory',progressionStyle:'double',prescription:{reps:[10,20],rir:[2,2]}}]},input,{loadingInventory:{unit:'lb',exerciseOverrides:{[ex.id]:{increment:5,minimum:0}}}})[0];
assert.deepEqual(fly.suggestion.setTargets,engine.setTargets,'authored and generated cells use identical engine decisions');
const saved=fly.rows.map(s=>({...s,weight:'20',reps:'20',target:{...s.target,w:'20',prefillReps:'20'}}));
saved[0].done=true;saved[1].auto=false;saved[1].valueOwner='user';saved[2].weight='17.5';
const before=structuredClone(saved),refreshed=refreshPendingSetTargets(saved,fly.rows);
assert.equal(refreshed[0],saved[0]);assert.equal(refreshed[1],saved[1]);assert.equal(refreshed[2],saved[2]);assert.equal(refreshed[3].weight,'25');assert.equal(refreshed[3].reps,'11');assert.deepEqual(saved,before);
assert.equal(refreshPendingSetTargets(refreshed,fly.rows),refreshed,'repair is idempotent');
// A pre-M222 snapshot with ownership/auto flags but no recorded original rep prefill
// cannot prove whether the current reps are a user entry. No number may be replaced.
const unproven = structuredClone(saved[3]);
delete unproven.target.prefillReps;
unproven.reps = '17';
const unprovenInput = [unproven];
const legacyRestored = refreshPendingSetTargets(unprovenInput, [fly.rows[3]]);
assert.equal(legacyRestored[0],unproven,
    'missing prefill provenance must preserve the exact saved row, including user-typed reps');
assert.equal(legacyRestored,unprovenInput, 'unprovable restoration should preserve array identity');
// The same row with a documented prefill is safe only while both saved values match.
const originalAuto = { ...saved[3], target: { ...saved[3].target } };
assert.equal(refreshPendingSetTargets([originalAuto],[fly.rows[3]])[0].reps,fly.rows[3].reps,
    'proven automatic rows still update to the new prescription');
const uncertainWeight = { ...originalAuto, weight: '22.5' };
assert.equal(refreshPendingSetTargets([uncertainWeight],[fly.rows[3]])[0],uncertainWeight,
    'manually changed weights must not be overwritten even if reps match');
const tech=structuredClone(program);tech.overrides['push:0'].techOverride='Last set: myo-reps';
const edited=applyCustomProgramSettings(tech,{...tech.config,weeks:8,session:'s40',noSupersets:true,folder:' Custom ',description:'My plan'});
assert.equal(edited.days,tech.days);assert.equal(edited.overrides,tech.overrides);assert.equal(edited.config.noSupersets,true);assert.equal(edited.weeks,8);assert.equal(edited.folder,'Custom');
assert.equal(computeCell(edited,day,ex.id,0,8).tech,'Last set: myo-reps');assert.equal(rowsFor(edited).rows.filter(s=>s.sub&&s.kind==='myo').length,3);
const drop=structuredClone(edited);drop.overrides['push:0'].techOverride='Last set: drop set';assert.equal(rowsFor(drop).rows.filter(s=>s.sub&&s.kind==='drop').length,2);
const restored=parseStoredData(JSON.stringify({v:STORE_VERSION,saved:[edited],history,custom:[],cycles:[],perf:{}}));
assert.equal(restored.saved[0].id,edited.id);assert.equal(restored.saved[0].overrides['push:0'].techOverride,'Last set: myo-reps');
// Launch settings must reject a newly requested firm constraint that the authored
// roster cannot satisfy. Keeping the exact authored program is preferable to a
// silently ignored setting or an unreviewed exercise replacement.
export const constraints={id:'custom-constraints',custom:true,weeks:4,
    config:{weeks:4,goal:'both',progression:'manual'},
    days:[{id:'upper',label:'Upper',primaryIndex:1,exercises:['pushup','bb-bench','ohp']}],overrides:{}};
const constraintsBefore=structuredClone(constraints);
assert.throws(()=>applyCustomProgramSettings(constraints,{...constraints.config,noBodyweight:true}),/bodyweight/i,
    'Skip bodyweight cannot save successfully while Push-Up remains prescribed');
assert.throws(()=>applyCustomProgramSettings(constraints,{...constraints.config,barbellCap:1}),/barbell/i,
    'A one-barbell limit cannot be recorded while two barbell lifts remain');
assert.throws(()=>applyCustomProgramSettings(constraints,{...constraints.config,weeks:53}),/weeks/i,
    'The editor must refuse unsupported durations rather than silently saving 52 weeks');
assert.deepEqual(constraints,constraintsBefore,'Refused settings never mutate an authored plan');
for(const invalid of ['',0,-1,2.5,53,Infinity,Number.MAX_SAFE_INTEGER,'3e1',true]) {
    assert.equal(editableProgramWeeks(invalid),null);
    assert.throws(()=>applyCustomProgramSettings(constraints,{...constraints.config,weeks:invalid}),/weeks/i);
}
for(const duration of [1,2,10,52]) assert.equal(editableProgramWeeks(duration),duration);
assert.throws(()=>applyCustomProgramSettings({...constraints,weeks:1,config:{weeks:1}},{weeks:true}),/weeks/i);
assert.throws(()=>applyCustomProgramSettings({...constraints,weeks:30,config:{weeks:30}},{weeks:'3e1'}),/weeks/i);
const alternating=structuredClone(constraints);
alternating.days[0].exercises=['bb-bench','inc-curl'];alternating.pairs={'upper:1':'ohp'};
const alternateIssues=customProgramSettingsIssues(alternating,{...alternating.config,barbellCap:1});
assert.ok(alternateIssues.some(issue=>issue.code==='barbell_cap'&&issue.week===2),'Even-week barbell choices must count toward the requested limit');
const lateBodyweight=structuredClone(alternating);lateBodyweight.pairs['upper:1']='pullup';
assert.ok(customProgramSettingsIssues(lateBodyweight,{...lateBodyweight.config,noBodyweight:true}).some(issue=>issue.code==='bodyweight'),'Equipment-assisted/weighted bodyweight choices share the generator classification');
const oneWeek={...alternating,weeks:1,config:{...alternating.config,weeks:1}};
assert.equal(customProgramSettingsIssues(oneWeek,{...oneWeek.config,barbellCap:1}).length,0,'An unexecuted alternate does not invent a constraint violation');
const longSaved={...constraints,weeks:72,folder:'Keep',description:'Existing notes',config:{...constraints.config,weeks:72}};
const metadataEdit=applyCustomProgramSettings(longSaved,{folder:' New folder '});
assert.equal(metadataEdit.weeks,72,'Editing metadata cannot silently truncate a previously saved longer program');
assert.equal(metadataEdit.description,longSaved.description);assert.equal(metadataEdit.days,longSaved.days);
const compatible={...constraints,days:[{...constraints.days[0],exercises:['bb-bench','inc-curl']}]};
const constrained=applyCustomProgramSettings(compatible,{...compatible.config,noBodyweight:true,barbellCap:1,noSupersets:true});
assert.equal(constrained.days,compatible.days);assert.equal(constrained.overrides,compatible.overrides);
assert.equal(constrained.config.noSupersets,true);

console.log('PASS M222: Cable Fly row targets; DDP positional loads/reps; reduced-load floor; effort/incomplete/mixed-load gates; per-day custom history ownership; canonical-engine parity; immutable resume repair; authored roster/settings and late-week Myo/Drop techniques; strict target parsing and atomic weekly target edits.');
