import assert from 'node:assert/strict';
import { adjustWorkoutReadiness, READINESS_CHOICES, readinessLabel } from '../modules/workout-readiness.js';
const ex = { id: 'gym-stack' }, exFor = id => id === ex.id ? ex : null;
const snap = (_, weight) => Math.max(5, Math.floor(weight / 5 + 1e-9) * 5);
const owned = row => row.valueOwner === 'user' || row.auto === false;
const adjust = (rows, factor) => adjustWorkoutReadiness(rows, factor,
    { exerciseFor: exFor, snapLoad: snap, ownsSet: owned, unit: 'lb' });
const source = [{ id: ex.id, sets: [
    { weight: '100', reps: '8', done: false, target: { w: '100', reps: '6-10', rir: 2 }, valueOwner: 'prescription' },
    { weight: '90', reps: '8', done: true, valueOwner: 'prescription' },
    { weight: '95', reps: '10', done: false, valueOwner: 'user' },
    { weight: '40', reps: '5', warm: true, valueOwner: 'prescription' },
    { weight: '30', sub: true, valueOwner: 'prescription' },
    { weight: '100', reps: '8', retuneSrc: 0, autoTuned: true, valueOwner: 'prescription' }
]}];
const original = structuredClone(source);
const light = adjust(source, .95);
assert.equal(light[0].sets[0].weight,'95');
assert.equal(light[0].sets[0].target.w,'95');
assert.equal(light[0].sets[0].target.rir,2,'target effort is never retuned by readiness');
assert.equal(light[0].sets[0].reps,'8');
assert.equal(light[0].sets[0].valueOwner,'prescription');
assert.equal(light[0].sets[0].readinessBaseWeight,100);
assert.deepEqual(light[0].sets.slice(1), source[0].sets.slice(1),'logged, manual, warmup, extensions and live-retuned sets survive');
assert.equal(adjust(light,.95),light,'same choice is idempotent and returns the same snapshot');
assert.equal(adjust(light,.88)[0].sets[0].weight,'85','readiness changes come from original load, not prior adjusted load');
assert.equal(adjust(adjust(light,.88),1)[0].sets[0].weight,'100','normal readiness restores the original load');
assert.deepEqual(source,original,'the input prescription is immutable');
const small=adjust([{id:ex.id,sets:[{weight:'5',reps:'8',valueOwner:'prescription'}]}],.88);
assert.equal(small[0].sets[0].weight,'5','no zero or imaginary weights below the rack minimum');
const inventory=(rows,f)=>adjustWorkoutReadiness(rows,f,{exerciseFor:exFor,unit:'lb',
    snapLoad:(_,w)=>[25,35,50,65].filter(v=>v<=w).at(-1)??25,ownsSet:owned});
assert.equal(inventory([{id:ex.id,sets:[{weight:'50',valueOwner:'prescription'}]}],.95)[0].sets[0].weight,'35',
    'the equipment inventory determines a supported step');
assert.equal(adjust([{id:ex.id,sets:[{weight:'100',done:false,auto:false}]}],.88)[0].sets[0].weight,'100');
assert.throws(()=>adjust(source,.5),/Unsupported readiness/);
assert.equal(readinessLabel(null),'Not set');
assert.equal(readinessLabel({label:'—'}),'Not set');
assert.deepEqual(READINESS_CHOICES.map(x=>x.factor),[1,.95,.88]);
console.log('PASS B944 readiness: idempotent, reversible, inventory-aligned, never overwrites user, history or live autoregulation.');
