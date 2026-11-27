import assert from 'node:assert/strict';
import { EX_BY_ID, feasibleDropLoad, growMyoSets, setGymLimits, syncSubSets } from '../modules/engine-api.js';

// Regression: editing only a myo mini's reps used to mark its entire row
// user-owned. Subsequent activation-load changes left stale 30/25 lb
// minis beside a 47.5 lb activation set, visually acting like drops.
const ex = EX_BY_ID['lat-raise'];
assert.ok(ex, 'test uses a real loadable exercise');
const activation = { weight: '47.5', reps: '13', done: true, warm: false };
const mini = (weight, targetWeight = weight) => ({
    weight, reps: '5', warm: false, sub: true, kind: 'myo',
    myo: true, done: false, auto: false, valueOwner: 'user',
    target: { w: targetWeight, reps: '3-5', myo: true }
});

const stale = [activation, mini('47.5'), mini('30'), mini('25')];
const fixed = syncSubSets(stale, ex, 'lb');
assert.deepEqual(fixed.slice(1).map(s => s.weight), ['47.5', '47.5', '47.5'],
    'rep-only user ownership may not turn myo-reps into descending drop loads');
assert.deepEqual(fixed.slice(1).map(s => s.target.w), ['47.5', '47.5', '47.5'],
    'displayed targets and editable weights must agree');
assert.deepEqual(fixed.slice(1).map(s => s.reps), ['5', '5', '5'],
    'repair must not rewrite the athlete\'s reps');
assert.equal(syncSubSets(fixed, ex, 'lb'), fixed,
    'a synchronized session must remain referentially stable on re-renders');
assert.deepEqual(stale.slice(1).map(s => s.weight), ['47.5', '30', '25'],
    'repair must be immutable');

// A user deliberately changing the mini's LOAD is not a rep-only edit.
// Explicit load choices and logged facts always beat generated targets.
const manual = mini('30', '47.5');
const logged = { ...mini('25'), done: true };
const protectedSets = syncSubSets([activation, manual, logged, mini('25')], ex, 'lb');
assert.equal(protectedSets[1], manual, 'manual load stays untouched');
assert.equal(protectedSets[2], logged, 'logged actual load stays untouched');
assert.equal(protectedSets[3].weight, '47.5', 'remaining automatic mini follows activation');
const sameValueManual = { ...mini('47.5'), weightOwner: 'user' };
const repOnly = mini('47.5');
const adjusted = syncSubSets([{ ...activation, weight: '50' }, sameValueManual, repOnly], ex, 'lb');
assert.equal(adjusted[1], sameValueManual,
    'explicit weight edits remain owned even when they originally matched the target');
assert.equal(adjusted[2].weight, '50',
    'a reps-only edit must continue following the activation load');

// The common sequence starts with unchanged minis; editing the activation
// set later must carry every unlogged mini even when reps were manually set.
const later = syncSubSets([
    { ...activation, weight: '50' },
    mini('47.5'), mini('47.5'), mini('47.5')
], ex, 'lb');
assert.deepEqual(later.slice(1).map(s => s.weight), ['50', '50', '50']);

// Growth is still 3-5 minis, inherits activation load and uses the 3-rep
// floor. It is not a new independently progressing/drop-loaded set.
const completedThird = { ...fixed[3], done: true, reps: '5' };
const completedPrefix = [fixed[0], ...fixed.slice(1,3).map(row=>({...row,done:true}))];
const extended = syncSubSets(growMyoSets([...completedPrefix, completedThird]), ex, 'lb');
assert.equal(extended.length, 5, 'successful third mini may add a fourth');
assert.equal(extended[4].weight, '47.5', 'grown mini uses activation weight');
assert.equal(growMyoSets([...completedPrefix, { ...completedThird, reps: '2' }]).length, 4,
    'failed mini stops growth');

// Drop sets remain separate, preserving their explicit fractions.
const drops = syncSubSets([activation,
    { weight: '0', sub: true, kind: 'drop', dropF: 0.8, auto: true, target: { w: '0' } },
    { weight: '0', sub: true, kind: 'drop', dropF: 0.6, auto: true, target: { w: '0' } }
], ex, 'lb');
assert.ok(Number(drops[1].weight) < 47.5 && Number(drops[1].weight) > Number(drops[2].weight),
    'only drop sets should reduce load across extension rows');
console.log('PASS B1025: myo activation-load invariant, rep-only ownership, manual/logged preservation, growth, and drops');

// Wrong-exercise correction: all earned extensions must retract when their
// source work is undone, and re-logging can earn them again normally.
const firstThree=[activation,...[0,1,2].map(()=>({weight:'47.5',reps:'5',done:true,sub:true,kind:'myo',prescribed:true,auto:true,valueOwner:'prescription',target:{w:'47.5',reps:'3-5'}}))];
const fourth=growMyoSets(firstThree);
assert.equal(fourth.length,5);
const fifth=growMyoSets(fourth.map((row,i)=>i===4?{...row,done:true}:row));
assert.equal(fifth.length,6);
const allLogged=fifth.map(row=>({...row,done:true}));
assert.equal(growMyoSets(allLogged),allLogged,'never grow beyond five');
const allUndone=allLogged.map(row=>({...row,done:false}));
const reset=growMyoSets(allUndone);
assert.equal(reset.length,4,'activation plus exactly three initial minis after undo');
assert.ok(reset.every(row=>!row.done));
assert.equal(growMyoSets(reset),reset,'reset remains stable on reload');
const replay=growMyoSets(reset.map(row=>({...row,done:true})));
assert.equal(replay.length,5,'fourth mini can be re-earned after correction');
const gap=fifth.map((row,i)=>i===2?{...row,done:false}:row);
assert.equal(growMyoSets(gap).length,5,'unearned pending fifth removed while logged fourth is protected');
const manualFifth={...fifth[5],valueOwner:'user',auto:false,weightOwner:'user'};
const preserved=growMyoSets([...allUndone.slice(0,5),manualFifth]);
assert.equal(preserved[5],manualFifth,'explicitly edited mini remains protected');
assert.equal(growMyoSets([activation,...firstThree.slice(1).map((row,i)=>({...row,done:i===2}))]).length,4,'out-of-order third cannot earn extra work');
assert.equal(growMyoSets([{...activation,done:false},...firstThree.slice(1)]).length,4,'undone activation cannot earn extra work');
console.log('PASS myo undo, replay, out-of-order protection and earned mini limits.');

// The two planned drops must be genuinely lower even on a coarse rack.
const coarse = (weight, fraction, done = false) => ({
    weight: String(weight), reps: '8', sub: true, kind: 'drop', dropF: fraction,
    auto: true, done, target: { w: String(weight) }
});
try {
    setGymLimits({unit:'lb', inventory:{dumbbell:[10,20,30]}});
    assert.equal(feasibleDropLoad(ex,30,30,.8,'lb'),20);
    assert.equal(feasibleDropLoad(ex,30,20,.6,'lb'),10);
    assert.equal(feasibleDropLoad(ex,10,10,.8,'lb'),null);
    assert.equal(feasibleDropLoad(ex,30,10,.6,'lb'),null);
    const prior = {...activation,weight:'30'};
    const logged = coarse(10,.8,true);
    const corrected = syncSubSets([prior,logged,coarse(20,.6)],ex,'lb');
    assert.equal(corrected[1],logged,'completed lower setting is factual');
    assert.equal(corrected[2].weight,'','no automatic repeat or increase after actual first drop');
    assert.equal(corrected[2].target.w,'—');
    assert.equal(corrected[2].reps,'8');
    assert.equal(syncSubSets(corrected,ex,'lb'),corrected,'no repair loop');
    const unresolved = syncSubSets([prior,{...prior,weight:'',done:false},coarse(20,.8)],ex,'lb');
    assert.equal(unresolved.at(-1).weight,'','never borrow an earlier activation');
    assert.equal(feasibleDropLoad(EX_BY_ID['assisted-pullup'],30,30,.8,'lb'),null);
} finally { setGymLimits(null); }
console.log('PASS strict physical drop chain, actual prior reduction, unresolved activation and no impossible targets.');
