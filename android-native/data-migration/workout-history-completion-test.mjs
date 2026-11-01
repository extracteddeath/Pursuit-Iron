import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createHash } from 'node:crypto';
import {
  parseStoredData, previousWorkoutSet, observedHistoryRIR,
  generateNextProgramForShell, EXERCISES, EX_BY_ID, exerciseAt,
  prescribeSets, analyzeShellHistoryForNextEngine, nextWorkoutSuggestionForShell
} from '../../modules/engine-api.js';
import { deriveReadOnlyProgramReference } from './read-only-program-reference.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';

const f = JSON.parse(fs.readFileSync(
  'android-native/domain-contract/src/test/resources/finished-journal-golden.json','utf8'));
const wire = JSON.stringify(f.handoff);
const prepare = (handoff=wire, current=f.rawSnapshot, origin=f.rawSnapshot) =>
  prepareCanonicalWorkoutHistory(origin,current,handoff);
const clone = v => JSON.parse(JSON.stringify(v));
const digest = raw => createHash('sha256').update(raw).digest('hex');
const before = JSON.stringify(f);
const prepared = prepare();
const entry = prepared.historyEntry;
for (const [key, expected] of Object.entries(f.expected)) {
  if (key === 'workingSetIndices' || key === 'observedRir') continue;
  assert.deepEqual(entry[key],expected,key);
}
assert.equal(prepared.persisted,false);
assert.equal(prepared.approvedForWorkoutExecution,false);
assert.equal(prepared.preparedOnly,true);
assert.equal(prepared.expectedSnapshotSha256,digest(f.rawSnapshot));
assert.equal(prepared.candidateSnapshotSha256,digest(prepared.candidateSnapshot));
assert.equal(Object.keys(entry.perf).length,1,'skipped exercise has no fabricated history');
const bench = entry.perf['bb-bench'];
assert.deepEqual(bench.sets.map(row => row.workingSetIndex),f.expected.workingSetIndices);
assert.deepEqual(bench.sets.map(observedHistoryRIR),f.expected.observedRir);
assert.equal(previousWorkoutSet(bench,1),null,'skipped set 2 cannot borrow set 3');
assert.equal(previousWorkoutSet(bench,2).r,6);
assert.equal(bench.sets[0].w,185.5,'user load is retained without automatic rounding');
assert.equal(bench.sets[1].rirReported,false,'target RIR never becomes reported effort');
assert.ok(bench.sets.every(row => !row.decisionTrace),
  'reconstructed targets cannot masquerade as suggestions issued during training');
assert.equal(bench.prescription.sets,3);
assert.equal(bench.prescription.progressionStyle,'double');
const stored = parseStoredData(prepared.candidateSnapshot);
assert.equal(stored.history.length,1);
assert.deepEqual(stored.history[0],JSON.parse(JSON.stringify(entry)));
assert.equal(stored.perf['bb-bench'].weight,185.5);
assert.equal(JSON.stringify(f),before,'preparation cannot mutate source fixtures');

// A process death after the future canonical store commit must be a no-op on
// retry, even when the caller's wall clock or JSONObject key order has changed.
const now = Date.now;
Date.now = () => 999999999;
try { assert.deepEqual(prepare(),prepared); } finally { Date.now = now; }
const retried = prepare(wire,prepared.candidateSnapshot);
assert.equal(retried.alreadyPresent,true);
assert.equal(retried.candidateSnapshot,prepared.candidateSnapshot);
const reordered = JSON.stringify(f.handoff,(_key,value) => value && typeof value === 'object'
  && !Array.isArray(value) ? Object.fromEntries(Object.entries(value).reverse()) : value,2);
assert.equal(prepare(reordered,prepared.candidateSnapshot).alreadyPresent,true);

const rejects = change => {
  const j = clone(f.handoff);
  change(j);
  assert.throws(() => prepare(JSON.stringify(j)),/^TypeError: Canonical workout completion rejected$/);
};
for (const change of [
  j => { j.format=1; }, j => { j.extra=true; }, j => { j.finish=null; },
  j => { j.events.pop(); }, j => { j.events[0].fromRevision=6; },
  j => { j.events[0].toRevision=20; }, j => { j.events[1].operationId=j.events[0].operationId; },
  j => { j.finish.operationId=j.events[0].operationId; },
  j => { j.events[1].slot=clone(j.events[0].slot); },
  j => { j.slots[0].exerciseId='cable-row'; j.events[2].slot.exerciseId='cable-row'; },
  j => { j.slots.reverse(); }, j => { j.session.dayId='absent-day'; },
  j => { j.session.weekIndex=7; }, j => { j.session.unit='kg'; },
  j => { j.session.reference.programUpdatedAt=43; },
  j => { j.session.reference.snapshotSha256='f'.repeat(64); },
  j => { j.events[0].values.load='185.5.5'; },
  j => { j.events[0].values.reps=0; }, j => { j.events[0].values.rir='2'; },
  j => { j.events[0].values.load=null; j.events[0].values.unit=null; },
  j => { j.events[0].values.unit='kg'; }, j => { j.events[1].values={}; },
  j => { j.finish.fromRevision=12; }, j => { j.finish.toRevision=11; },
  j => { j.finish.completedAt=999; }, j => { j.finish.durationMs=60001; },
  j => { j.startRevision=Number.MAX_SAFE_INTEGER; }
]) rejects(change);
assert.throws(() => prepare(' '.repeat(256*1024+1)));

function rejectsCurrent(change, raw=f.rawSnapshot) {
  const current = JSON.parse(raw);
  change(current);
  assert.throws(() => prepare(wire,JSON.stringify(current)),
    /^TypeError: Canonical workout completion rejected$/);
}
rejectsCurrent(s => { s.savedAt++; });
rejectsCurrent(s => { s.saved[0].updatedAt++; });
rejectsCurrent(s => { s.saved[0].overrides['synthetic-day:0'].sets=4; });
rejectsCurrent(s => { s.tombs['synthetic-program']=42; });
rejectsCurrent(s => { s.tombs['synthetic-workout']=1; });
rejectsCurrent(s => { s.saved.push({...s.saved[0],id:'synthetic-workout'}); });
rejectsCurrent(s => { s.history[0].updatedAt++; },prepared.candidateSnapshot);
rejectsCurrent(s => { s.history[0].perf['bb-bench'].sets[0].r=9; },prepared.candidateSnapshot);
rejectsCurrent(s => { s.history[0].programId='another-program'; },prepared.candidateSnapshot);
rejectsCurrent(s => { s.history.push(clone(s.history[0])); },prepared.candidateSnapshot);
rejectsCurrent(s => { s.tombs['synthetic-workout']=100000; },prepared.candidateSnapshot);
assert.throws(() => prepare(wire,f.rawSnapshot,f.rawSnapshot+' '),'exact origin bytes are required');

// A later independent workout must remain the latest performance after a
// crash retry of an older already-published native completion.
const newer = JSON.parse(prepared.candidateSnapshot);
newer.savedAt=90000;
newer.history.unshift({...clone(entry),id:'independent-workout',date:90000,updatedAt:90000});
newer.history[0].perf['bb-bench'].weight=200;
newer.perf['bb-bench']=clone(newer.history[0].perf['bb-bench']);
const newerRaw=JSON.stringify(newer);
assert.equal(prepare(wire,newerRaw).candidateSnapshot,newerRaw);
assert.equal(JSON.parse(prepare(wire,newerRaw).candidateSnapshot).perf['bb-bench'].weight,200);

// The snapshot hash changes before the atomic store write: no claim that
// preparing a candidate has committed or successfully rebased anything.
assert.notEqual(prepared.expectedSnapshotSha256,digest(newerRaw));

function authoredStore(change) {
  const store=JSON.parse(f.rawSnapshot);
  change(store);
  const raw=JSON.stringify(store), handoff=clone(f.handoff);
  handoff.session.reference=deriveReadOnlyProgramReference(raw,'synthetic-program');
  return () => prepareCanonicalWorkoutHistory(raw,raw,JSON.stringify(handoff));
}
assert.throws(authoredStore(s => { s.saved[0].deloadNext={'bb-bench':true}; }),
  'one-shot deload cannot be partially committed without its canonical owner');
assert.throws(authoredStore(s => {
  s.history=Array.from({length:2000},(_,i)=>({id:`old-${i}`,date:i+1,updatedAt:i+1,
    programId:'another-program',perf:{}}));
}), 'a completed workout cannot silently evict retained history');

// Canonical logging and evaluation must consume a real generated program,
// not only a manually authored transport fixture.
const equipment=['barbell','rack','bench','dumbbell','cable','machine','smith','pullup',
  'dip','legpress','hacksquat','legext','legcurl','calfmachine'];
for (const [split,days] of [['full_body',3],['upper_lower',4],['ulppl',5]]) {
  const config={name:'Synthetic lifecycle',unit:'lb',goal:'both',experience:'intermediate',
    split,days,session:'s60',weeks:6,progression:'auto',deload:false,
    equipment,focus:{},reduce:[],barbellCap:3,noBodyweight:false,noSupersets:false};
  const program=generateNextProgramForShell({config,legacyExercises:EXERCISES,
    seed:223,makeId:()=>`synthetic-${split}`}).program;
  assert.equal(program.nextEngine.program.audit.result,'pass');
  program.createdAt=1;
  program.updatedAt=42;
  const snapshot=JSON.stringify({v:13,savedAt:100,saved:[program],history:[],
    cycles:[],custom:[],gyms:[],tombs:{},perf:{}});
  const day=program.days[0], slots=[];
  for (let exerciseIndex=0;exerciseIndex<day.exercises.length;exerciseIndex++) {
    const currentId=exerciseAt(program,day,exerciseIndex,1);
    const rows=prescribeSets(program,day,EX_BY_ID[currentId],exerciseIndex,1,'lb',null,{},{},[],false)
      .filter(row=>!row.warm&&!row.sub);
    for (let setIndex=0;setIndex<rows.length;setIndex++)
      slots.push({exerciseIndex,setIndex,exerciseId:currentId});
  }
  const handoff={format:2,session:{reference:deriveReadOnlyProgramReference(snapshot,program.id),
    workoutId:`workout-${split}`,dayId:day.id,weekIndex:1,unit:'lb',startedAt:1000},
    slots,startRevision:0,
    events:slots.map((slot,i)=>({operationId:`set-${i}`,slot,disposition:'COMPLETED',
      values:{load:'100',unit:'lb',reps:5,rir:2},fromRevision:i,toRevision:i+1})),
    finish:{operationId:'finish',fromRevision:slots.length,toRevision:slots.length+1,
      completedAt:3601000,durationMs:3600000}};
  const candidate=prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(handoff));
  const history=JSON.parse(candidate.candidateSnapshot).history;
  assert.equal(history.length,1);
  assert.equal(history[0].setsDone,slots.length);
  const analysis=analyzeShellHistoryForNextEngine(program,history,EXERCISES);
  assert.equal(analysis.workoutCount,1);
  assert.equal(analysis.performedSetCount,slots.length,'each observed working set reaches the canonical evaluator once');
  const suggestion=nextWorkoutSuggestionForShell(program,history,EXERCISES,day,0,1,'lb');
  assert.ok(suggestion && Number.isFinite(suggestion.weight),'canonical next-workout progression is available');
  assert.equal(prepareCanonicalWorkoutHistory(snapshot,candidate.candidateSnapshot,JSON.stringify(handoff))
    .alreadyPresent,true);
}
console.log('PASS native finished-journal handoff, skipped ordinals, RIR provenance, revision conflicts, idempotent canonical history and three generated-program lifecycles; no store writes');
