import { classifySessionAdjustment } from '../modules/session-plan.js';
import assert from 'node:assert/strict';
import { sessionSuggestion, lastDayPerf, dayPerfFor, prescribeSets, EX_BY_ID } from '../modules/engine-api.js';

import { linearStalled, plateauSessions, styleOverride } from '../modules/training-domain/prescriptions.js';
import { refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import { previousWorkoutSet } from '../modules/next-engine/history-contract.js';
const id = 'seated-calf';
export const program = { id: 'm220-custom', custom: true, weeks: 10, config: { unit: 'lb', weeks: 10, progression: 'manual', goal: 'both', experience: 'intermediate', deload: false },
    days: [{ id: 'lower', label: 'Lower', primaryIndex: -1, exercises: [id] }, { id: 'legs', label: 'Legs', primaryIndex: -1, exercises: [id] }],
    overrides: { 'lower:0': { sets: 5, reps: '12-20', rir: '2', progressionStyle: 'double' }, 'legs:0': { sets: 3, reps: '12-20', rir: '2', progressionStyle: 'double' } } };
const entry = (dayId, date, weight, reps, programId = program.id) => ({ id: `${dayId}-${date}`, programId, dayId, date, unit: 'lb', weekIndex: 1,
    perf: { [id]: { weight, reps: reps.at(-1), sets: reps.map(r => ({ w: weight, r, rir: 2, tr: 2, done: true })) } } });
export const history = [entry('legs', Date.UTC(2026,8,28), 210, [20,17,15]), entry('lower', Date.UTC(2026,8,23), 205, [20,16,16,12,12])];
const suggest = (day, hs = history) => sessionSuggestion(program, day, 0, null, 'lb', 1, hs);
const lower = suggest(program.days[0]);
assert.equal(lower.weight, 205, 'Lower must use its five-set session, not newer three-set Legs');
assert.equal(lower.action, 'add_reps');
assert.equal(lower.last, history[1].perf[id]);
assert.equal(lastDayPerf(program.days[0], {}, history, program)[id], lower.last, 'LAST and advice share the same raw session');
const rows = prescribeSets(program, program.days[0], EX_BY_ID[id], 0, 1, 'lb', lower, {}, {}, history, false);
assert.equal(rows.length, 5); assert.ok(rows.every(r => r.weight === '205' && r.target.reps === '12-20'));
assert.equal(suggest(program.days[1]).weight, 210, 'Legs retains its own working load');
const cappedOther = [entry('legs', Date.UTC(2026,8,29), 210, [20,20,20]), ...history];
assert.equal(suggest(program.days[0], cappedOther).weight, 205, 'a capped three-set session cannot advance the five-set day');
const cappedLower = [entry('lower', Date.UTC(2026,9,1), 205, [20,20,20,20,20]), ...history];
assert.equal(suggest(program.days[0], cappedLower).weight, 210, 'all five sets at the ceiling earn the next load');
assert.equal(suggest(program.days[0], [entry('lower', Date.UTC(2026,9,1), 205, [20,20,20]), ...history]).weight, 205, 'three sets do not satisfy a five-set prescription');
const fallback = suggest(program.days[0], [history[0]]);
assert.equal(fallback.weight, 210, 'a never-trained day may initialize from the same program');
assert.equal(lastDayPerf(program.days[0], {}, [history[0]], program)[id], undefined, 'M226: another day is a load reference, not LAST for this day');
assert.equal(fallback.last, history[0].perf[id]);
assert.equal(fallback.referenceOnly, true);
assert.equal(fallback.action, 'initial', 'a cross-day reference cannot earn progression');
const unrelated = entry('lower', Date.UTC(2026,9,2), 400, [20,20,20,20,20], 'other-program');
assert.equal(suggest(program.days[0], [unrelated, ...history]).weight, 205, 'same day IDs in other programs cannot replace this program evidence');
assert.equal(lastDayPerf(program.days[0], {}, [unrelated, ...history], program)[id], lower.last);
assert.equal(suggest(program.days[0], [...history].reverse()).weight, 205, 'date ordering is independent of input order');
assert.equal(suggest(program.days[0], [entry('lower', Date.UTC(2026,9,1), 90, [20,20,20,20,20], 'other')]).weight, 90, 'M226: another program may initialize load but cannot earn an increase');
assert.deepEqual(history[1].perf[id].sets.map(s => s.w), [205,205,205,205,205], 'logged evidence is immutable');
const onlyLegs = [entry('legs', Date.UTC(2026,9,3), 210, [20,20,20])];
assert.deepEqual(plateauSessions(onlyLegs, id, 'lower', 8, program), [],
    'adaptive style detection cannot borrow stall evidence from another authored day');
const migratedLower = { ...entry('retired-lower', Date.UTC(2026,9,4), 205, [15,15,15,15,15]), dayLabel: 'Lower' };
assert.equal(plateauSessions([migratedLower], id, 'lower', 8, program).length, 1,
    'a unique stable day label still recovers migrated adaptive-style history');
const duplicateLabelProgram = structuredClone(program);
duplicateLabelProgram.days.forEach(day => { day.label = 'Lower'; });
assert.deepEqual(plateauSessions([{ ...migratedLower, dayLabel: 'Lower' }], id, 'lower', 8, duplicateLabelProgram), [],
    'duplicate labels keep stale adaptive-style history unresolved instead of guessing');
const flatOtherDay = Array.from({ length: 5 }, (_, i) => entry('legs', Date.UTC(2026, 9, 1 + i), 210, [15,15,15]));
const targetPerf = { [id]: { weight: 210, reps: 15 } };
assert.equal(linearStalled(targetPerf, EX_BY_ID[id], flatOtherDay, program, 'lower'), false,
    'beginner linear-stall detection cannot graduate a different authored day');
assert.notEqual(styleOverride(program, EX_BY_ID[id], false, 1, targetPerf, flatOtherDay, 'lower')?.kind, 'plateau',
    'hard plateau override cannot borrow a different day through a non-program-aware stall call');
const flatLower = Array.from({ length: 5 }, (_, i) => entry('lower', Date.UTC(2026, 9, 1 + i), 210, [15,15,15,15,15]));
assert.equal(linearStalled(targetPerf, EX_BY_ID[id], flatLower, program, 'lower'), true,
    'same-day flat beginner history still triggers the intended linear-stall graduation');
assert.equal(styleOverride(program, EX_BY_ID[id], false, 1, targetPerf, flatLower, 'lower')?.kind, 'plateau',
    'same-day flat intermediate history still triggers the intended plateau override');
console.log('PASS M220: screenshot Lower/Legs regression, separate day loads, set-count qualification, earned progression, scoped fallbacks, shared LAST/advice and immutable history.');

const automatic = { weight: '210', reps: '20', auto: true, done: false, valueOwner: 'prescription', target: { w: '210', reps: '12-20', prefillReps: '20' } };
const pending = [automatic, { ...automatic, done: true }, { ...automatic, valueOwner: 'user', auto: false }, { ...automatic, weight: '212.5' }, { ...automatic, added: true }, { ...automatic, sub: true }];
const before = structuredClone(pending);
const refreshed = refreshPendingSetTargets(pending, [{...automatic,weight:'205',target:{...automatic.target,w:'205'}}]);
assert.equal(refreshed[0].weight, '205'); assert.equal(refreshed[0].target.w, '205');
const unproven = { ...automatic, target: { w: '210', reps: '12-20' } };
const missingProof = [unproven];
assert.equal(refreshPendingSetTargets(missingProof,
    [{...automatic,weight:'205',target:{...automatic.target,w:'205'}}]),missingProof,
    'legacy rows without original auto-prefill metadata must not regenerate numbers');
for (let i = 1; i < pending.length; i++) assert.equal(refreshed[i], pending[i], 'completed, manual, edited, added and sub rows are protected');
assert.deepEqual(pending, before);
assert.equal(refreshPendingSetTargets(refreshed, [{...automatic,weight:'205',target:{...automatic.target,w:'205'}}]), refreshed, 'unchanged targets preserve identity');
console.log('PASS M220 recovery: stale automatic custom loads refresh; completed/manual/edited/added/sub values and original snapshot remain intact.');

// Generated programs already evaluate day-specific workouts, but their returned LAST/changed-week
// reference also has to come from that same day rather than a newer occurrence elsewhere.
const { default: fs } = await import('node:fs');
const { EXERCISES } = await import('../modules/engine-api.js');
const { generateNextProgramForShell, markUserPrescriptionOverride } = await import('../modules/next-engine/app-shell-adapter.js');
const { nextWorkoutSuggestionForShell } = await import('../modules/next-engine/workout-history-adapter.js');
const fixture = JSON.parse(fs.readFileSync('verification/m205-generation-parity-results.json')).rows[3];
const generated = generateNextProgramForShell({ config: fixture.config, legacyExercises: EXERCISES, seed: fixture.seed, makeId: () => 'm220-next' }).program;
const [first, second] = generated.days, slot = 1, lift = first.exercises[slot];
second.exercises[slot] = lift;
for (const [day, sets] of [[first,5],[second,3]]) {
    const key = `${day.id}:${slot}`;
    for (const [field, value] of [['sets',sets], ['reps','12-20'], ['rir','2'], ['progressionStyle','double']])
        generated.overrides[key] = markUserPrescriptionOverride(generated.overrides[key] || {}, field, value);
}
const nextEntry = (day, date, weight, reps) => ({ id: `next-${date}`, programId: generated.id, dayId: day.id, date, weekIndex: 1, unit: 'lb',
    perf: { [lift]: { weight, reps: reps.at(-1), sets: reps.map(r => ({w:weight,r,rir:2,tr:2,done:true})) } } });
const oldDay = nextEntry(first, 1000, 205, [20,16,16,12,12]);
const newOtherDay = nextEntry(second, 2000, 210, [20,17,15]);
const generatedSuggestion = nextWorkoutSuggestionForShell(generated, [newOtherDay,oldDay], EXERCISES, first, slot, 1);
assert.equal(generatedSuggestion.last, oldDay.perf[lift], 'generated suggestion metadata shares its evaluator day');
assert.equal(generatedSuggestion.weight, 205);
assert.equal(generatedSuggestion.action, 'add_reps');
const otherProgramSameDay = { ...oldDay, id: 'other-program-same-day', programId: 'other-program',
    date: 3000, perf: { [lift]: { weight: 400, reps: 20, sets: [{ w: 400, r: 20, rir: 2, tr: 2, done: true }] } } };
assert.equal(lastDayPerf(first, {}, [otherProgramSameDay, oldDay], generated)[lift], oldDay.perf[lift],
    'LAST cannot be stolen by the same day id from another program');
assert.equal(dayPerfFor(first, {}, [otherProgramSameDay, oldDay], generated)[lift], oldDay.perf[lift],
    'best-of-recent anchor cannot use another program with a colliding day id');
console.log('PASS M220 generated bridge: decision, LAST and anchor metadata stay program/day scoped even when another occurrence or program is newer.');

// The October 8 squat report needs an older stronger anchor as well as the latest incomplete ramp.
export const squatProgram = generateNextProgramForShell({ config: fixture.config, legacyExercises: EXERCISES,
    seed: fixture.seed, makeId: () => 'squat-history-regression' }).program;
export const squatDay = squatProgram.days.find(d => d.exercises[0] === 'back-squat');
for (const [field, value] of [['sets',4], ['reps','5-8'], ['rir','2'], ['progressionStyle','double']])
    squatProgram.overrides[`${squatDay.id}:0`] = markUserPrescriptionOverride(squatProgram.overrides[`${squatDay.id}:0`] || {}, field, value);
const squatEntry = (date, sets) => ({ id:`squat-${date}`, programId:squatProgram.id, dayId:squatDay.id,
    date, weekIndex:1, unit:'lb', perf:{'back-squat':{weight:Math.max(...sets.map(s=>s.w)),reps:sets.at(-1).r,sets}} });
export const squatHistory = [
    squatEntry(3000, [{w:205,r:3,rir:2,tr:2,rirReported:false},{w:210,r:2,rir:2,tr:2,rirReported:false},{w:215,r:3,rir:0,rirReported:true}]),
    squatEntry(2000, [{w:200,r:3,rir:4},{w:205,r:5,rir:4},{w:210,r:5,rir:4},{w:215,r:5,rir:4}])
];
assert.equal(dayPerfFor(squatDay, {}, squatHistory, squatProgram)['back-squat'], squatHistory[1].perf['back-squat'],
    'older stronger progression anchor differs from the most recent workout in this regression');
assert.equal(lastDayPerf(squatDay, {}, squatHistory, squatProgram)['back-squat'], squatHistory[0].perf['back-squat']);
const rawSquat = squatHistory[0].perf['back-squat'], originalSquat = structuredClone(rawSquat);
assert.deepEqual([0,1,2].map(i=>previousWorkoutSet(rawSquat,i)).map(s=>[s.w,s.r,s.rir]),
    [[205,3,null],[210,2,null],[215,3,0]],'history references retain raw values and only observed effort');
assert.equal(previousWorkoutSet(rawSquat,3),null,'a new fourth set must not borrow the final prior row');
const noisy = {sets:[{w:45,r:10,warm:true},null,...rawSquat.sets,{w:100,r:10,sub:true},{w:225,r:8,done:false}]};
assert.deepEqual([0,1,2,3].map(i=>previousWorkoutSet(noisy,i)),[0,1,2,3].map(i=>previousWorkoutSet(rawSquat,i)),
    'warmups, extensions, nulls and pending rows do not shift working-set references');
assert.deepEqual(previousWorkoutSet({weight:'200',reps:'5'},3),{w:200,r:5,rir:null,summary:true});
assert.equal(previousWorkoutSet({weight:200,reps:5,sets:[null]},0),null,'malformed per-set records do not invent a summary result');
assert.deepEqual(rawSquat,originalSquat,'display references do not calibrate or mutate history');
for (const candidate of [squatProgram, {...squatProgram,engineSource:undefined,custom:true}]) {
    const advice = sessionSuggestion(candidate,squatDay,0,null,'lb',1,squatHistory);
    assert.equal(advice.action,'hold_load','Double progression holds last weight until the lifter chooses otherwise');
    assert.equal(advice.weight,205); assert.equal(advice.optionalLowerLoad,190);
    const sets = prescribeSets(candidate,squatDay,EX_BY_ID['back-squat'],0,1,'lb',advice,{}, {},squatHistory,false);
    assert.deepEqual(sets.map(s=>[s.weight,s.reps]),[['205','5'],['210','5'],['215','5'],['205','5']]);
}
console.log('PASS M220 squat regression: explicit double keeps its prior weight; fatigue reduction remains available for acceptance.');

// M338 / 148338.png: a mild final-set miss must not turn a held ramp into four top sets.
export const inclineProgram={id:'m338-incline',custom:true,engineV:4,weeks:6,
    config:{weeks:6,unit:'lb',goal:'strength',progression:'double',experience:'intermediate',deload:false},
    days:[{id:'incline',label:'Upper',primaryIndex:0,exercises:['low-inc-bb-bench']}],
    overrides:{'incline:0':{sets:4,reps:'6-8',rir:'2'}}};
export const inclineHistory=[{id:'m338-last',programId:inclineProgram.id,dayId:'incline',weekIndex:1,date:1,unit:'lb',
    perf:{'low-inc-bb-bench':{weight:210,reps:5,sets:[195,200,205,210].map((w,i)=>({w,r:[10,8,6,5][i],done:true,rir:null}))}}}];
const inclineBefore=structuredClone(inclineHistory);
const inclineAdvice=sessionSuggestion(inclineProgram,inclineProgram.days[0],0,null,'lb',1,inclineHistory);
assert.equal(inclineAdvice.action,'hold_load');
assert.equal(inclineAdvice.optionalLowerLoad,190);
assert.deepEqual(inclineAdvice.setTargets.map(s=>s.weight),[195,200,205,210]);
assert.match(inclineAdvice.reason,/build reps/);
const inclineRows=prescribeSets(inclineProgram,inclineProgram.days[0],EX_BY_ID['low-inc-bb-bench'],0,1,'lb',inclineAdvice,{}, {},inclineHistory,false);
assert.deepEqual(inclineRows.map(s=>[s.weight,s.reps]),[['195','6'],['200','6'],['205','6'],['210','6']]);
assert.equal(inclineRows[0].loadAdvice.toLoad,190,'Recovery drop is an opt-in recommendation');
assert.deepEqual(inclineHistory,inclineBefore);
const generatedRamp=structuredClone(squatProgram);
generatedRamp.overrides[`${squatDay.id}:0`]=markUserPrescriptionOverride(generatedRamp.overrides[`${squatDay.id}:0`], 'reps', '6-8');
const generatedRampHistory=[{...inclineHistory[0],programId:generatedRamp.id,dayId:squatDay.id,
    perf:{'back-squat':inclineHistory[0].perf['low-inc-bb-bench']}}];
const generatedAdvice=sessionSuggestion(generatedRamp,generatedRamp.days.find(d=>d.id===squatDay.id),0,null,'lb',1,generatedRampHistory);
assert.equal(generatedAdvice.action,'hold_load'); assert.equal(generatedAdvice.optionalLowerLoad,190);
assert.deepEqual(generatedAdvice.setTargets.map(s=>s.weight),[195,200,205,210],
    'Generated and authored programs share the same fatigue-aware straight-set calibration');


// B943: rep-only progression is ordinary training, not an undoable plan change.
const classify = (previous, targets, suggestion = { action: 'add_reps' }, setDelta = null) =>
    classifySessionAdjustment({ previous, targets, suggestion, setDelta, unit: 'lb' });
const held = [{ weight: 200, reps: 6 }];
const extraRep = classify(held, [{ weight: 200, reps: 7 }]);
assert.equal(extraRep.changed, false, 'one more rep must not create a Home change');
assert.equal(extraRep.loadChanged, false);
const heavier = classify(held, [{ weight: 205, reps: 6 }], { action: 'increase_load' });
assert.equal(heavier.category, 'Load increased');
assert.equal(heavier.changed, true);
const plateau = classify(held, [{ weight: 190, reps: 6 }],
    { action: 'decrease_load', reason: 'Plateau reset after repeated stalls' });
assert.equal(plateau.category, 'Plateau response');
const moreSets = classify(held, [{ weight: 200, reps: 7 }], { action: 'add_reps' }, { before: 3, after: 4 });
assert.equal(moreSets.category, 'Sets updated');
assert.equal(moreSets.summary, '3 → 4 planned sets');
console.log('PASS B943: ordinary reps are not changes; material load, plateau and saved planned-set changes are distinguished.');


export const rearProgram = { id:'b1002-rear-fly',custom:true,engineV:4,weeks:6,
        config:{weeks:6,unit:'lb',goal:'both',progression:'double',experience:'intermediate',deload:false},
        days:[{id:'upper',label:'Upper',primaryIndex:-1,exercises:['rear-fly']}],
        overrides:{'upper:0':{sets:3,reps:'15-25',rir:'2',progressionStyle:'double'}} };
export const rearHistory = [{id:'b1002-prior',programId:rearProgram.id,dayId:'upper',date:Date.UTC(2026,9,28),
        weekIndex:1,unit:'lb',perf:{'rear-fly':{weight:60,reps:20,
            sets:[0,1,2].map(() => ({w:60,r:20,rir:2,tr:2,done:true}))}}}];

// B1002 / 148555.png: Rear Delt Fly, three prior 60x20 sets. An unexplained
// third-set 55x21 is neither a rep-first advance nor a user-approved deload.
{
    const { retainDoubleProgressionSetLoads } = await import('../modules/training-domain/prescriptions.js');
    const prior = rearHistory; const before = structuredClone(prior);
    const advice = sessionSuggestion(rearProgram,rearProgram.days[0],0,null,'lb',1,prior);
    assert.equal(advice.action,'add_reps');
    assert.deepEqual(advice.setTargets.map(t=>[t.weight,t.reps]),[[60,21],[60,21],[60,21]],
        'equal previous loads progress reps at 60 lb, not an arbitrary 55 lb on set 3');
    const rows = prescribeSets(rearProgram,rearProgram.days[0],EX_BY_ID['rear-fly'],0,1,'lb',
        advice,{}, {},prior,false);
    assert.deepEqual(rows.map(row => row.target.decisionTrace?.source?.load),[60,60,60],
        'Each visible target must identify its own historical load');
    assert.deepEqual(rows.map(row => row.target.decisionTrace?.source?.reps),[20,20,20]);
    assert.deepEqual(rows.map(row => row.target.decisionTrace?.target?.load),[60,60,60]);
    assert.deepEqual(rows.map(row => row.target.decisionTrace?.evaluation?.action),['add_reps','add_reps','add_reps']);
    assert.equal(rows[2].target.decisionTrace.source.historyEntryId,prior[0].id,
        'Later-set advice must identify its exact source workout');
    assert.deepEqual(rows.map(s=>[s.weight,s.reps]),[['60','21'],['60','21'],['60','21']],
        'Workout row defaults and canonical recommendation must agree');
    const stray = {...advice,setTargets:advice.setTargets.map((t,i)=>i===2?{...t,weight:55}:t)};
    const fixed = retainDoubleProgressionSetLoads(stray,[60,60,60]);
    assert.deepEqual(fixed.setTargets.map(t=>[t.weight,t.reps]),[[60,21],[60,21],[60,21]],
        'rep-only progression cannot smuggle a load drop into one set');
    assert.equal(fixed.weight,60);
    assert.equal(retainDoubleProgressionSetLoads(fixed,[60,60,60]),fixed,
        'clean suggestions preserve identity and remain stable through rerenders');
    const recovered = refreshPendingSetTargets([
        {auto:true,done:false,valueOwner:'prescription',weight:'55',reps:'21',
            target:{w:'55',reps:'15-25',prefillReps:'21'}}
    ],[rows[2]]);
    assert.equal(recovered[0].weight,'60','untouched pending rows may be repaired on resume');
    const action = retainDoubleProgressionSetLoads({...stray,action:'decrease_load'},[60,60,60]);
    assert.equal(action.setTargets[2].weight,55,'an explicit correction remains independently owned');
    const increased = retainDoubleProgressionSetLoads({...stray,action:'increase_load'},[60,60,60]);
    assert.equal(increased.setTargets[2].weight,55,'earned load changes must not be blocked');
    const mixed = retainDoubleProgressionSetLoads(stray,[60,60,55]);
    assert.deepEqual(mixed.setTargets.map(t=>t.weight),[60,60,55],
        'genuine prior mixed-load work retains its individual set load');
    assert.deepEqual(prior,before,'history remains immutable throughout recommendation and render');
}
console.log('PASS B1002: classic double per-set load invariants, 60x20 to 60x21, opt-in corrections and resume.');

// B1003 skipped-middle working set: preserving ordinal identity must survive
// Workout -> logger -> JSON history -> LAST -> both progression evaluators.
{
    const { loggedWorkoutPerformance } = await import('../modules/training-domain/prescriptions.js');
    const { evaluateWorkoutProgression } = await import('../modules/next-engine/performance.js');
    const base = prescribeSets(rearProgram, rearProgram.days[0], EX_BY_ID['rear-fly'],
        0, 1, 'lb', { weight: 60, target: 20, action: 'initial' }, {}, {}, [], false);
    const loggedRows = base.map((row, i) => ({
        ...row, done: i !== 1, weight: i === 2 ? '55' : '60',
        reps: i === 2 ? '18' : '20', actualRIR: i === 1 ? null : 2
    }));
    const saved = loggedWorkoutPerformance(rearProgram, rearProgram.days[0],
        [{ id:'rear-fly', slot:0, sets:loggedRows }], 1, 'lb', {}, []);
    const prior = JSON.parse(JSON.stringify(saved['rear-fly']));
    assert.deepEqual(prior.sets.map(row => row.decisionTrace?.workingSetIndex),[0,2],
        'Completed working sets retain the exact original per-position trace');
    assert.deepEqual(prior.sets.map(row => row.decisionTrace?.applied?.load),[60,55],
        'History stores the actual applied load in the same record as the original target');
    assert.deepEqual(prior.sets.map(row => row.workingSetIndex),[0,2],
        'Logger must not silently compact skipped set #2 into set #1');
    assert.deepEqual(previousWorkoutSet(prior,0).r,20);
    assert.equal(previousWorkoutSet(prior,1),null,
        'LAST cannot show completed set #3 under skipped set #2');
    assert.equal(previousWorkoutSet(prior,2).w,55);
    const actual = prior.sets.map(row => ({
        exerciseId:'rear-fly',setIndex:row.workingSetIndex,load:row.w,
        reps:row.r,rir:row.rirReported ? row.rir : null
    }));
    const session = { exercises:[{ exerciseId:'rear-fly', name:'Rear Delt Fly',
        role:'hypertrophy_isolation',sets:3,progressionStyle:'double',
        prescription:{reps:[15,25],rir:[2,2]} }]};
    const decision = evaluateWorkoutProgression(session,actual)[0];
    assert.equal(decision.outcome,'incomplete');
    assert.notEqual(decision.action,'increase_load');
    assert.deepEqual(decision.setTargets.map(row=>row.weight),[60,60,55],
        'Progression must retain the actual third-set weight at ordinal 2');
    const entry={id:'b1003-history',programId:rearProgram.id,
        dayId:'upper',date:Date.UTC(2026,9,28),weekIndex:1,
        unit:'lb',perf:saved};
    const advice=sessionSuggestion(rearProgram,rearProgram.days[0],0,null,'lb',1,[entry]);
    assert.notEqual(advice.action,'increase_load');
    const next=prescribeSets(rearProgram,rearProgram.days[0],
        EX_BY_ID['rear-fly'],0,1,'lb',advice,{}, {},[entry],false);
    assert.deepEqual(next.map(row=>Number(row.weight)),[60,60,55],
        'Saved custom history must preserve set ordinal through next actual Workout targets');
    const manual = loggedWorkoutPerformance(rearProgram,rearProgram.days[0],
        [{id:'rear-fly',slot:0,sets:base.map((row,i)=>({
            ...row,done:i===2,weight:i===2?'50':row.weight,
            reps:i===2?'19':row.reps,valueOwner:i===2?'user':row.valueOwner,
            auto:i===2?false:row.auto
        }))}],1,'lb',{},[]);
    assert.equal(manual['rear-fly'].sets[0].decisionTrace.target.load,60,
        'User edits must not rewrite what the engine originally prescribed');
    assert.equal(manual['rear-fly'].sets[0].decisionTrace.applied.load,50);
    assert.equal(manual['rear-fly'].sets[0].decisionTrace.applied.owner,'user');
    const duplicates=evaluateWorkoutProgression(session,[
        {...actual[0],setIndex:0,reps:25},
        {...actual[0],setIndex:1,reps:25},
        {...actual[0],setIndex:1,reps:25}])[0];
    assert.notEqual(duplicates.action,'increase_load',
        'Three records are not three completed sets when a position is duplicated');
}
console.log('PASS B1003 skipped-middle: logger, history, LAST, progression and Workout positions match.');
