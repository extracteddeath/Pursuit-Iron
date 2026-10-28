import assert from 'node:assert/strict';
import { EX_BY_ID } from '../modules/training-domain/catalog.js';
import { setExLoadInc, setLoadInc } from '../modules/training-domain/records.js';
import { setGymLimits, loadableAtOrBelow, prescribeSets } from '../modules/training-domain/prescriptions.js';
import { buildRuntimeSetTargets, refreshPendingSetTargets } from '../modules/next-engine/workout-runtime.js';
import {
    defaultLoadingInventoryFor, loadingInventoryWithExerciseSteps, availableLoadAtOrBelow,
    nextAvailableLoad
} from '../modules/next-engine/loading.js';
import { evaluateWorkoutProgression } from '../modules/next-engine/performance.js';

const id = 'machine-shoulder', ex = EX_BY_ID[id], increment = { [id]: { v: 5, unit: 'lb' } };
assert.ok(ex && ex.name === 'Machine Shoulder Press');
const cell = { sets: 3, reps: [8, 12], rir: [1, 2], role: 'hypertrophy_compound', progressionStyle: 'dynamic' };
try {
    setLoadInc(0, null);
    setExLoadInc(increment);
    setGymLimits(null);
    assert.equal(loadableAtOrBelow(ex, 115.5, 'lb'), 115);
    assert.equal(loadableAtOrBelow(ex, 99, 'lb'), 95);
    const inventory = loadingInventoryWithExerciseSteps(defaultLoadingInventoryFor('kg'), 'lb', increment);
    assert.equal(inventory.machine.increment, 5, 'factory unit switching must retain the native 5 lb machine notch');
    assert.equal(inventory.exerciseOverrides[id].increment, 5, 'per-exercise configured increment must reach the evaluator');
    assert.equal(nextAvailableLoad(id, 120, inventory, ex.equip), 125);
    assert.equal(availableLoadAtOrBelow(id, 115.5, inventory, ex.equip), 115);

    const fromScreenshot = [115.5, 110, 99];
    const setTargets = fromScreenshot.map((weight, i) => ({ weight, reps: i === 1 ? 12 : 8 }));
    const targetRows = buildRuntimeSetTargets({
        exerciseId: id, cell, workingLoad: 115.5, setTargets,
        loadingInventory: inventory, equipmentAvailable: ['machineshoulder', 'machine'],
        snapLoad: value => loadableAtOrBelow(ex, value, 'lb'),
        includeWarmups: false
    });
    assert.deepEqual(targetRows.map(s => s.weight), [115, 110, 95],
        'every working set—not just warmups—must obey actual machine notches');
    assert.deepEqual(targetRows.map(s => s.reps), [8, 12, 8]);

    const day = { id: 'upper', label: 'Upper', primaryIndex: 0, exercises: [id] };
    const program = { id: 'b946-custom', custom: true, config: {
        unit: 'lb', goal: 'hypertrophy', experience: 'advanced', weeks: 10, progression: 'dynamic'
    }, exLoadInc: increment, days: [day], overrides: {}, slotBias: {}, autoBias: {} };
    const correction = prescribeSets(program, day, ex, 0, 2, 'lb',
        { weight: 115.5, setTargets, action: 'hold', target: 8 },
        null, {}, [], false);
    assert.deepEqual(correction.filter(s => !s.warm && !s.sub).map(s => Number(s.weight)), [115, 110, 95],
        'real workout prescription must use the same loadable increments as engine evaluation');
    for(const row of correction) {
        assert.equal(Number(row.weight)%5,0);
        assert.equal(Number(row.target.w),Number(row.weight), 'visible target and actual input must match');
    }

    // A previous 120 lb exposure that met its rep floor and effort should never
    // become 115.5 merely through conversion or per-set progression bookkeeping.
    const past = [8,9,8].map((reps,i)=>({ exerciseId: id, setIndex:i, load:120, reps, rir:2 }));
    const decision = evaluateWorkoutProgression({ exercises: [{
        exerciseId:id, sets:3, progressionStyle:'dynamic',
        prescription:{reps:[8,12],rir:[1,2]}
    }] },past,{loadingInventory:inventory,equipmentAvailable:['machineshoulder','machine']})[0];
    assert.equal(decision.action,'add_reps');
    assert.deepEqual(decision.setTargets.map(t=>t.weight),[120,120,120],
        'successful in-range work retains last session load; only actual performance can justify reduction');

    const held = prescribeSets(program,day,ex,0,2,'lb',{weight:120,
        setTargets: decision.setTargets, action:decision.action,target:9},null,{},[],false);
    assert.ok(held.filter(s=>!s.warm&&!s.sub).every(s=>Number(s.weight)===120));
    // A resumed session repairs only untouched automatic prefills, not user-edited or completed sets.
    const stale = [
        {weight:'115.5',reps:'8',done:false,auto:true,valueOwner:'prescription',target:{w:'115.5',prefillReps:'8'}},
        {weight:'110',reps:'12',done:false,auto:true,valueOwner:'user',target:{w:'110',prefillReps:'12'}},
        {weight:'99',reps:'8',done:true,auto:true,valueOwner:'prescription',target:{w:'99',prefillReps:'8'}}
    ];
    const fresh = [
        {weight:'115',reps:'8',target:{w:'115',prefillReps:'8'}},
        {weight:'110',reps:'12',target:{w:'110',prefillReps:'12'}},
        {weight:'95',reps:'8',target:{w:'95',prefillReps:'8'}}
    ];
    const restored = refreshPendingSetTargets(stale, fresh);
    assert.equal(restored[0].weight,'115');
    assert.equal(restored[1],stale[1], 'manual ownership is immutable');
    assert.equal(restored[2],stale[2], 'completed performance is immutable');

    const stocked = defaultLoadingInventoryFor('lb');
    stocked.exerciseOverrides[id] = {availableLoads:[105,115,120,125]};
    const rack = loadingInventoryWithExerciseSteps(stocked,'lb',increment);
    assert.equal(availableLoadAtOrBelow(id,119,rack,ex.equip),115,
        'finite known exercise rack stays authoritative over default increments');
    assert.equal(availableLoadAtOrBelow(id,120,rack,ex.equip),120);
    assert.equal(nextAvailableLoad(id,120,rack,ex.equip),125);
    assert.equal(loadingInventoryWithExerciseSteps(defaultLoadingInventoryFor('kg'), 'kg',
        { [id]:{v:2.5,unit:'kg'} }).exerciseOverrides[id].increment,2.5);
    console.log('PASS B946 machine grid: 115.5/110/99 corrections, previous 120 preservation, per-ex engine inventory, resume, finite racks and kg');
} finally {
    setExLoadInc({});
    setLoadInc(0,null);
    setGymLimits(null);
}
