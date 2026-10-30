import assert from 'node:assert/strict';
import { applyLiveAutoregulation, rollbackLiveAutoregulation, liveEffortFeedback, reconcileRestoredLiveAdjustments } from '../modules/next-engine/live-autoregulation.js';
import { loggedWorkoutPerformance, EX_BY_ID, sessionSuggestion } from '../modules/training-domain.js';
import { progressionExposureContext } from '../modules/next-engine/history-contract.js';

const cell = { sets: 3, reps: [8, 12], rir: [1, 2], role: 'hypertrophy_isolation', progressionStyle: 'double' };
const pending = () => ({ weight: '100', reps: '8', done: false, auto: true, valueOwner: 'prescription', target: { w: 100, reps: [8, 12], rir: 1, prefillReps: '8' } });
const sets = [{ ...pending(), reps: '6', actualRIR: 0, done: true }, { ...pending(), reps: '7', actualRIR: 0, done: true }, pending()];
const snapLoad = x => Math.floor(x / 5) * 5, input = { sets, sourceIndex: 1, cell, snapLoad };
const before = structuredClone(sets), adjusted = applyLiveAutoregulation(input);
assert.deepEqual(sets, before);
assert.equal(adjusted.decision.action, 'suggest_pending_load_reduction');
assert.equal(adjusted.decision.earnedProgression, false);
assert.equal(adjusted.sets[0], sets[0]); assert.equal(adjusted.sets[1], sets[1]);
assert.equal(adjusted.sets[2].weight, '100'); assert.equal(adjusted.sets[2].loadAdvice.toLoad,95); assert.equal(adjusted.sets[2].reps, sets[2].reps);
assert.deepEqual(adjusted.sets[2].target.reps, sets[2].target.reps);
assert.equal(adjusted.sets[2].recoveryLimited, undefined);
assert.equal(applyLiveAutoregulation({ ...input, sets: adjusted.sets }).sets, adjusted.sets, 'same event never compounds');
for (const source of [0, 1]) assert.deepEqual(rollbackLiveAutoregulation(adjusted.sets, source), sets);
const restored = JSON.parse(JSON.stringify(adjusted.sets));
assert.deepEqual(applyLiveAutoregulation({ ...input, sets: restored }).sets, restored, 'interruption/reload does not compound');
assert.deepEqual(rollbackLiveAutoregulation(restored, 0), sets);
const cleared = adjusted.sets.map((s, i) => i === 0 ? { ...s, actualRIR: null } : s);
assert.equal(applyLiveAutoregulation({ ...input, sets: cleared, sourceIndex: 0 }).sets[2].weight, '100', 'removing evidence rolls back its pending change');
for (const field of [{ valueOwner: 'user' }, { auto: false }, { added: true }, { weight: '103' }, { reps: '9' }, { done: true }]) {
    const protectedRows = sets.map((s, i) => i === 2 ? { ...s, ...field } : s);
    assert.equal(applyLiveAutoregulation({ ...input, sets: protectedRows }).sets, protectedRows);
}
for (const context of [{ interrupted: true }, { prescriptionEdited: true }, { manualOverride: true }])
    assert.equal(applyLiveAutoregulation({ ...input, context }).sets, sets);
for (const c of [{ ...cell, role: 'primary_strength' }, { ...cell, role: 'secondary_strength' }, { ...cell, progressionStyle: 'wave' }, { ...cell, setTargets: [] }])
    assert.equal(applyLiveAutoregulation({ ...input, cell: c }).sets, sets);
for (const mutate of [s => ({ ...s, actualRIR: null }), s => ({ ...s, actualRIR: undefined }), s => ({ ...s, actualRIR: 3 }), s => ({ ...s, reps: '12', actualRIR: 2 }),
    s => ({ ...s, weight: '-100' }), s => ({ ...s, actualRIR: 'bad' }), s => ({ ...s, prescriptionEdited: true })]) {
    const rows = sets.map((s, i) => i < 2 ? mutate(s) : s);
    assert.equal(applyLiveAutoregulation({ ...input, sets: rows }).sets, rows);
}
assert.equal(applyLiveAutoregulation({ ...input, snapLoad: () => 80 }).sets, sets, 'large/coarse steps are refused');
assert.equal(applyLiveAutoregulation({ ...input, snapLoad: () => NaN }).sets, sets);
const ex = EX_BY_ID['bb-bench'], program = { id: 'live-232', custom: true, weeks: 6, config: { unit: 'lb', progression: 'double' },
    days: [{ id: 'd', label: 'Accessory', primaryIndex: -1, exercises: [ex.id] }], overrides: { 'd:0': { sets: 3, reps: '8-12', rir: '1-2', progressionStyle: 'double' } } };
const finished = adjusted.sets.map(s => ({ ...s, done: true, reps: '12', actualRIR: 2 }));
const perf = loggedWorkoutPerformance(program, program.days[0], [{ id: ex.id, slot: 0, note: '', sets: finished }], 1, 'lb', {}, []);
assert.equal(perf[ex.id].sets[2].recoveryLimited, undefined, 'Unaccepted advice must not count as lowered work');
assert.equal(progressionExposureContext(perf[ex.id].sets).badDay, false);
const history = [{ id: 'completed', programId: program.id, dayId: 'd', date: 1, unit: 'lb', perf }];
assert.equal(sessionSuggestion(program, program.days[0], 0, null, 'lb', 1, history).action, 'increase_load', 'Unaccepted advice cannot masquerade as completed reduced work');
const accepted = finished.map((s, i) => i === 2 ? { ...s, weight: '95', recoveryLimited: true, valueOwner: 'user' } : s);
const acceptedPerf = loggedWorkoutPerformance(program, program.days[0], [{ id: ex.id, slot: 0, note: '', sets: accepted }], 1, 'lb', {}, []);
assert.equal(progressionExposureContext(acceptedPerf[ex.id].sets).badDay, true, 'An explicitly accepted lower set remains recovery evidence');
assert.notEqual(sessionSuggestion(program, program.days[0], 0, null, 'lb', 1, [{ ...history[0], perf: acceptedPerf }]).action, 'increase_load', 'Lowered work must not automatically earn a new load');
console.log('PASS M232: two observed misses, one bounded loadable pending reduction, completed/manual/protocol protection, idempotent resume/undo and no earned progression from eased work.');

// B901: a history-derived 9 / 11 / 9 prefill must yield to today's 8 @ 2 observation.
const repRows = () => [
    { ...pending(), reps: '8', done: true, actualRIR: 2 },
    { ...pending(), reps: '11', target: { ...pending().target, rir: '1-2', prefillReps: '11' } },
    { ...pending(), reps: '9', target: { ...pending().target, prefillReps: '9' } }
];
const repInput = { sets: repRows(), sourceIndex: 0, cell, snapLoad };
const capped = applyLiveAutoregulation(repInput);
assert.equal(capped.decision.action, 'cap_pending_reps');
assert.equal(capped.sets[1].reps, '8');assert.equal(capped.sets[1].weight, '100');
assert.equal(capped.sets[1].target.prefillReps, '8');assert.equal(capped.sets[1].target.reps, repInput.sets[1].target.reps);
assert.equal(capped.sets[1].target.rir, '1-2');assert.equal(capped.sets[0], repInput.sets[0]);assert.equal(capped.sets[2], repInput.sets[2]);
assert.deepEqual(repInput.sets, repRows(), 'never mutate actual or planned inputs');
assert.deepEqual(applyLiveAutoregulation({...repInput, sets:capped.sets}).sets, capped.sets, 'a repeated rating cannot compound a rep cap');
assert.deepEqual(rollbackLiveAutoregulation(capped.sets, 0), repInput.sets);
for (const actualRIR of [null, undefined, '', 'bad', -1, 11]) {
 const rows=capped.sets.map((s,i)=>i===0?{...s,actualRIR}:s);
 assert.equal(applyLiveAutoregulation({...repInput,sets:rows}).sets[1].reps,'11','cleared/invalid evidence restores automatic baseline');
}
const rerated=capped.sets.map((s,i)=>i===0?{...s,actualRIR:3}:s);
assert.equal(applyLiveAutoregulation({...repInput,sets:rerated}).sets[1].reps,'9','rerating recalculates from original, not already capped, reps');
for (const change of [{valueOwner:'user'},{auto:false},{added:true},{done:true},{reps:'10'},{weight:'105'}]) {
 const rows=repRows().map((s,i)=>i===1?{...s,...change}:s);
 assert.equal(applyLiveAutoregulation({...repInput,sets:rows}).sets,rows,'never overwrite manual/done/changed next row or skip to a later row');
}
for(const extra of [{cell:{...cell,percentageProtocol:{}}},{cell:{...cell,setTargets:[]}},{cell:{...cell,progressionStyle:'wave'}}, {cell:{...cell,reps:[8,8]}},
 {sets:repRows().map((s,i)=>i===1?{...s,target:{...s.target,nextAction:'percentage'}}:s)},
 {sets:repRows().map((s,i)=>i===1?{...s,typeOverride:'failure'}:s)},
 {cell:{...cell,reps:[8,8]}, sets:repRows().map(s=>({...s,target:{...s.target,reps:'8'}}))},
 {context:{interrupted:true}},{context:{prescriptionEdited:true}}]) {
 const rows=extra.sets||repRows();assert.equal(applyLiveAutoregulation({...repInput,...extra,sets:rows}).sets,rows);
}
for(const role of ['primary_strength','secondary_strength','hypertrophy_isolation']) {
 const out=applyLiveAutoregulation({...repInput,sets:repRows(),cell:{...cell,role}});
 assert.equal(out.sets[1].reps,'8','range-based doubles can cap reps without changing strength loads');
}
const lowerLoad=repRows().map((s,i)=>i===1?{...s,weight:'95',target:{...s.target,w:'95'}}:s);
assert.equal(applyLiveAutoregulation({...repInput,sets:lowerLoad}).sets,lowerLoad,'do not extrapolate capacity across loads');
const unknown=repRows().map((s,i)=>i===0?{...s,actualRIR:null}:s);
assert.equal(applyLiveAutoregulation({...repInput,sets:unknown}).sets,unknown,'missing effort is never assumed to be failure or spare reps');
const chain=capped.sets.map((s,i)=>i===1?{...s,done:true,reps:'6',actualRIR:0}:i===0?{...s,reps:'6',actualRIR:0}:s);
assert.equal(applyLiveAutoregulation({...repInput,sets:chain,sourceIndex:1}).sets[2].loadAdvice?.toLoad,95,'a previous rep cap cannot block the two-miss load safeguard');
assert.equal(liveEffortFeedback(unknown,0),'Optional · guides suggestions');
assert.equal(liveEffortFeedback(capped.sets,0),'Next set reps adjusted');
assert.equal(liveEffortFeedback(repRows(),0),'Saved · next target unchanged');
assert.equal(liveEffortFeedback([{...pending(),done:true,actualRIR:2}],0),'Saved for future workouts');
console.log('PASS B901 suggestion audit: observed-effort rep caps, per-row intent, truthful feedback, rerating/undo, manual and protocol protection.');

// M359: deliberate session-local effort/protocol choices are not ordinary misses.
// The real set-type control retains automatic value ownership while changing target RIR.
for (const intent of [
    { typeOverride: 'failure', target: { rir: 0, failure: true } },
    { target: { failure: true, rir: 0 } },
    { target: { amrap: true } },
    { target: { nextAction: 'percentage' } }
]) {
    const withIntent = row => ({ ...row, ...intent, target: { ...row.target, ...intent.target } });
    const authoredSources = sets.map((row, i) => i < 2 ? withIntent({ ...row, reps: '8' }) : row);
    const original = structuredClone(authoredSources);
    assert.equal(applyLiveAutoregulation({ ...input, sets: authoredSources }).sets, authoredSources,
        'Two intentionally hard protocol sets cannot lower a later ordinary set');
    assert.deepEqual(authoredSources, original, 'Authored set intent and logged observations remain immutable');
    const authoredPending = sets.map((row, i) => i === 2 ? withIntent(row) : row);
    assert.equal(applyLiveAutoregulation({ ...input, sets: authoredPending }).sets, authoredPending,
        'Two ordinary misses cannot overwrite an explicitly authored pending protocol');
    const oldAdjustment = adjusted.sets.map((row, i) => i < 2 ? withIntent(row) : row);
    const repaired = reconcileRestoredLiveAdjustments(oldAdjustment);
    assert.equal(repaired[2].weight, '100', 'Resume retires an old automatic reduction based on authored sources');
    assert.equal(repaired[0], oldAdjustment[0], 'Resume preserves the actual authored observation');
    assert.equal(reconcileRestoredLiveAdjustments(repaired), repaired, 'Intent repair is idempotent');
    const authoredAfterCap = capped.sets.map((row, i) => i === 1 ? withIntent(row) : row);
    assert.equal(rollbackLiveAutoregulation(authoredAfterCap, 0), authoredAfterCap,
        'Undoing evidence cannot replace a set type explicitly chosen after a live adjustment');
    assert.equal(applyLiveAutoregulation({ ...repInput, sets: authoredAfterCap }).sets, authoredAfterCap,
        'Rerating preserves the later authored target instead of restoring its obsolete RIR');
}
console.log('PASS M359: deliberate failure/AMRAP/percentage sets neither authorize nor receive automatic fatigue changes.');

// M296: an opening snapshot cannot retain a live reduction without its logged sources.
{
const adjusted = applyLiveAutoregulation(input).sets;
assert.equal(adjusted[2].weight, '100'); assert.equal(adjusted[2].loadAdvice.toLoad,95);
assert.equal(reconcileRestoredLiveAdjustments(adjusted), adjusted, 'Real two-miss observations survive a genuine resume');
for (const change of ['unlogged', 'missing_rir', 'self_source', 'missing_source']) {
    const saved = structuredClone(adjusted);
    if (change === 'unlogged') saved[0].done = false;
    if (change === 'missing_rir') saved[0].actualRIR = null;
    if (change === 'self_source') saved[2].liveAdjustment.sourceIndices = [0, 2];
    if (change === 'missing_source') saved[2].liveAdjustment.sourceIndices = [0, 99];
    const before = structuredClone(saved), fixed = reconcileRestoredLiveAdjustments(saved);
    assert.equal(fixed[2].weight, '100', change + ': remove orphaned reduction');
    assert.equal(fixed[2].liveAdjustment, undefined); assert.equal(fixed[2].loadAdvice,undefined);
    assert.deepEqual(saved, before);
    assert.equal(reconcileRestoredLiveAdjustments(fixed), fixed, 'Restore repair is idempotent');
    for (const protectedRow of [{ done: true }, { auto: false }, { valueOwner: 'user' }, { weight: '92.5' }]) {
        const manual = structuredClone(saved);Object.assign(manual[2], protectedRow);
        assert.equal(reconcileRestoredLiveAdjustments(manual), manual, 'Preserve completed/manual/changed rows');
    }
}
const secondSetOrphan = [pending(), { ...adjusted[2], liveAdjustment: { ...adjusted[2].liveAdjustment, pendingIndex: 1, sourceIndices: [0, 1] } }, pending()];
assert.equal(reconcileRestoredLiveAdjustments(secondSetOrphan)[1].weight, '100', 'Set 2 cannot inherit a two-miss reduction at opening');
console.log('PASS M296 restore: orphan reductions are removed; genuine adjustments and user/logged data survive.');

}

assert.equal(reconcileRestoredLiveAdjustments(capped.sets), capped.sets, 'Genuine rep caps survive restore');
for (const kind of ['reps','actualRIR','weight']) {
    const changed = structuredClone(capped.sets);
    if (kind === 'reps') changed[0].reps = '9';
    if (kind === 'actualRIR') changed[0].actualRIR = 4;
    if (kind === 'weight') changed[0].weight = '95';
    const recovered = reconcileRestoredLiveAdjustments(changed);
    assert.equal(recovered[1].reps, '11',
        'Changed source ' + kind + ' invalidates a previously restored rep cap');
    assert.equal(recovered[1].liveAdjustment, undefined);
    assert.equal(recovered[0].done,true,'Never overwrite the lifter\'s completed source set');
    assert.equal(reconcileRestoredLiveAdjustments(recovered),recovered,
        'Rep-cap restoration after source edit must be idempotent');
}
for (const kind of ['reps','actualRIR','weight']) {
    const changed = structuredClone(adjusted.sets);
    if (kind === 'reps') changed[0].reps = '12';
    if (kind === 'actualRIR') changed[0].actualRIR = 4;
    if (kind === 'weight') changed[0].weight = '95';
    const recovered = reconcileRestoredLiveAdjustments(changed);
    assert.equal(recovered[2].loadAdvice, undefined,
        'Changing the evidence ' + kind + ' invalidates the stale suggested lower load');
    assert.equal(recovered[2].liveAdjustment, undefined);
    assert.equal(recovered[2].weight,'100');
}
for (const adjustment of [adjusted.sets, capped.sets]) {
    const legacy=structuredClone(adjustment);
    const target=legacy.find(x=>x.liveAdjustment);
    delete target.liveAdjustment.sourceObservations;
    const repaired=reconcileRestoredLiveAdjustments(legacy);
    assert.equal(repaired.find(x=>x.liveAdjustment),undefined,
        'Old evidence-free automatic suggestions must fail closed during restore');
    assert.deepEqual(reconcileRestoredLiveAdjustments(repaired),repaired);
}
const { captureWorkoutProgressionReplay, auditWorkoutProgressionReplay } =
    await import('../modules/next-engine/workout-replay.js');
const replayOf = rows => captureWorkoutProgressionReplay({
    program, day:program.days[0], row:{id:ex.id,slot:0,sets:rows},
    suggestion:null,cell,history:[],weekIndex:1,unit:'lb',readiness:{factor:1}
});
assert.deepEqual(auditWorkoutProgressionReplay(replayOf(capped.sets)),[],
    'Real pending rep cap with matching evidence must be explainable in an opt-in export');
const stale=structuredClone(capped.sets);stale[0].actualRIR=4;
assert.ok(auditWorkoutProgressionReplay(replayOf(stale))
    .some(issue=>issue.code==='stale_or_unverifiable_live_adjustment'),
    'Independent replay audit must detect changed effort evidence');
console.log('PASS B994 source fingerprint: edited completed work cannot authorize stale live caps or suggested loads after resume.');
const orphanCap = structuredClone(capped.sets);orphanCap[0].done = false;
orphanCap[1].suggested = { weight: '95', reps: '8' };
const repairedCap = reconcileRestoredLiveAdjustments(orphanCap);
assert.equal(repairedCap[1].weight, '100');assert.equal(repairedCap[1].reps, '11');
assert.equal(repairedCap[1].suggested, undefined, 'Retired tuning cannot leave a lower target in the UI');


// B994 follow-up: the independent, local-only replay must catch a 60 -> 55
// rep-only load change even when a legacy target lacks a decision trace.
{
    const { spawnSync } = await import('node:child_process');
    const { mkdtempSync, writeFileSync, rmSync } = await import('node:fs');
    const { tmpdir } = await import('node:os');
    const { join } = await import('node:path');
    const scope = mkdtempSync(join(tmpdir(), 'pursuit-replay-'));
    const prior = { id:'replay-history',programId:program.id,dayId:program.days[0].id,
        date:1,weekIndex:1,unit:'lb',perf:{[ex.id]:{
            sets:[0,1,2].map(workingSetIndex=>({
                w:60,r:20,rir:2,rirReported:true,workingSetIndex,done:true
            }))
        }}};
    const fixture = proposed => captureWorkoutProgressionReplay({
        program,day:program.days[0],weekIndex:1,unit:'lb',readiness:{factor:1},
        history:[prior],cell,
        suggestion:{action:'add_reps',setTargets:[0,1,2].map(i=>({weight:i===2?proposed:60,reps:21}))},
        row:{id:ex.id,slot:0,sets:[0,1,2].map(i=>({
            ...pending(),weight:String(i===2?proposed:60),reps:'21',
            target:{w:i===2?proposed:60,reps:'15-25',prefillReps:'21'}
        }))}
    });
    try {
        const file=join(scope,'diagnostics.json');
        const run=payload=>{
            writeFileSync(file,JSON.stringify(payload));
            return spawnSync(process.execPath,['scripts/audit-workout-replay.mjs',file],
                {cwd:process.cwd(),encoding:'utf8'});
        };
        const valid=run(fixture(60));
        assert.equal(valid.status,0,'Unchanged three-position double loads pass independent replay: '+valid.stdout+valid.stderr);
        const unearned=run(fixture(55));
        assert.equal(unearned.status,1,'55-lb third-set prescription fails closed');
        assert.match(unearned.stdout,/unearned_double_progression_history_load/);
        assert.match(unearned.stdout,/unearned_double_progression_suggestion_load/);
    } finally { rmSync(scope,{recursive:true,force:true}); }
}
console.log('PASS B994 exported replay: rep-only 60x20 history cannot silently prescribe 55x21 on set 3.');
