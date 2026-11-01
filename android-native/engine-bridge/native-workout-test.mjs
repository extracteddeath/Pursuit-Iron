import assert from 'node:assert/strict';
import { prepareNativeProgram,nativeProgramOptions } from './native-onboarding.mjs';
import { prepareNativeWorkout,issuedNativeWorkoutFrom } from './native-workout.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson,dispatchBackupSchemaJson } from './protocol.mjs';
import { parseStoredData,nextSessionCursor,buildLifterModel,calibratedAnchorPerf,sessionSuggestion,
  prescribeSets,EX_BY_ID } from '../../modules/engine-api.js';

export function finishedIssuedJournal(plan,{skipSecond=true}={}) {
  const slots=plan.rows.flatMap(row=>row.sets.filter(s=>!s.warm).map((_,setIndex)=>
    ({exerciseIndex:row.slot,setIndex,exerciseId:row.id})));
  const events=slots.map((slot,index)=>({operationId:`set-${index}`,slot,
    disposition:skipSecond && index===1?'SKIPPED':'COMPLETED',
    values:skipSecond && index===1?null:{load:String(40+slot.exerciseIndex*5),unit:plan.session.unit,
      reps:Math.max(1,Number(plan.rows.find(r=>r.slot===slot.exerciseIndex).sets.filter(s=>!s.warm)[slot.setIndex].reps)||6),
      rir:index%2===0?2:null},fromRevision:index,toRevision:index+1}));
  return {format:2,session:plan.session,slots,startRevision:0,events,finish:{operationId:'finish-workout',
    fromRevision:slots.length,toRevision:slots.length+1,completedAt:plan.session.startedAt+60000,durationMs:60000}};
}

const profile={name:'Native workout test',goal:'both',experience:'intermediate',unit:'kg',split:'full_body',
  days:3,session:'s60',equipment:nativeProgramOptions().equipment,allowBodyweight:true,allowSupersets:true,barbellCap:3};
let fixture;
for(const [split,days] of [['full_body',3],['upper_lower',4],['ppl',3],['ulppl',5]]) {
  let snapshot=prepareNativeProgram(JSON.stringify({creationId:'create-real',programId:'program-real',createdAt:1000,
    seed:223,profile:{...profile,split,days}})).snapshot;
  for(let rotation=0;rotation<=days;rotation++) {
    const startJson=JSON.stringify({programId:'program-real',workoutId:`workout-${rotation}`,startedAt:2000+rotation*70000});
    const plan=prepareNativeWorkout(snapshot,startJson),issued=JSON.stringify(plan);
    const retained=issuedNativeWorkoutFrom(snapshot,issued);
    assert.deepEqual(retained.plan,plan);
    const store=parseStoredData(snapshot),program=store.saved[0],cursor=nextSessionCursor(program,store.history);
    assert.equal(plan.session.dayId,program.days[cursor.dayIndex].id);
    assert.equal(plan.session.weekIndex,cursor.weekIndex);
    if(rotation===days)assert.equal(plan.session.weekIndex,2,'distinct completed days advance the canonical week');
    const day=program.days[cursor.dayIndex],lifter=buildLifterModel(store.history,store.perf,program);
    const anchors=calibratedAnchorPerf(program,day,store.perf,store.history,cursor.weekIndex,lifter.effort);
    for(const row of plan.rows) {
      const suggestion=sessionSuggestion(program,day,row.slot,anchors,'kg',cursor.weekIndex,store.history);
      const expected=prescribeSets(program,day,EX_BY_ID[row.id],row.slot,cursor.weekIndex,'kg',suggestion,
        anchors,store.perf,store.history,true);
      assert.deepEqual(row.sets,JSON.parse(JSON.stringify(expected)),'issued rows must match the actual web logger path');
    }
    const handoff=JSON.stringify(finishedIssuedJournal(plan));
    const result=prepareCanonicalWorkoutHistory(snapshot,snapshot,handoff,issued),entry=result.historyEntry;
    assert.deepEqual(entry.nativeSetObservations.events,JSON.parse(handoff).events.map(({slot,disposition,values})=>({slot,disposition,values})),
      'all native observations and skips must be durable before the journal is retired');
    for(const row of plan.rows) {
      const saved=entry.perf[row.id]?.sets || [];
      for(const set of saved) {
        const target=row.sets.filter(s=>!s.warm)[set.workingSetIndex].target;
        const {applied,...trace}=set.decisionTrace;
        assert.deepEqual(trace,target.decisionTrace,'history retains the targets actually issued before manual edits');
        assert.equal(applied.owner,'user');
      }
    }
    const first=entry.perf[plan.rows[0].id].sets;
    assert.equal(first[0].rirReported,true);
    if(plan.rows[0].sets.filter(s=>!s.warm).length>2)assert.equal(first[1].workingSetIndex,2,'skip keeps working ordinals');
    assert.equal(prepareCanonicalWorkoutHistory(snapshot,result.candidateSnapshot,handoff,issued).alreadyPresent,true);
    if(!fixture)fixture={snapshot,startJson,plan,issued,handoff,result};
    snapshot=result.candidateSnapshot;
  }
}
const send=(method,params)=>JSON.stringify({protocolVersion:1,id:'native-workout-test',method,params});
for(const [method,params] of [
  ['prepareNativeWorkout',{rawSnapshot:fixture.snapshot,startJson:fixture.startJson}],
  ['prepareIssuedWorkoutHistory',{originSnapshot:fixture.snapshot,currentSnapshot:fixture.snapshot,
    finishedJournal:fixture.handoff,issuedWorkout:fixture.issued}]
]) {
  const request=send(method,params);
  assert.equal(JSON.parse(dispatchWorkoutStoreJson(request)).ok,true);
  assert.equal(JSON.parse(dispatchEngineJson(request)).ok,false);
  assert.equal(JSON.parse(dispatchBackupSchemaJson(request)).ok,false);
}
const corrupt=structuredClone(fixture.plan);corrupt.rows[0].sets.find(s=>!s.warm).target.decisionTrace.slot=47;
assert.throws(()=>issuedNativeWorkoutFrom(fixture.snapshot,JSON.stringify(corrupt)));
assert.throws(()=>prepareCanonicalWorkoutHistory(fixture.result.candidateSnapshot,fixture.result.candidateSnapshot,fixture.handoff,fixture.issued));
const other=structuredClone(fixture.plan);other.session.workoutId='another-workout';
assert.throws(()=>prepareCanonicalWorkoutHistory(fixture.snapshot,fixture.snapshot,fixture.handoff,JSON.stringify(other)));
const deload=JSON.parse(fixture.snapshot);deload.saved[0].deloadNext={main:true};
assert.throws(()=>prepareNativeWorkout(JSON.stringify(deload),fixture.startJson));
const mixed=JSON.parse(fixture.handoff);
mixed.events[0].values.load='0';
const mixedResult=prepareCanonicalWorkoutHistory(fixture.snapshot,fixture.snapshot,JSON.stringify(mixed),fixture.issued);
const mixedSaved=parseStoredData(mixedResult.candidateSnapshot).history[0];
assert.equal(mixedSaved.nativeSetObservations.events[0].values.load,'0');
assert.ok(!mixedSaved.perf[fixture.plan.rows[0].id].sets.some(set=>set.workingSetIndex===0),
  'canonical loaded-set summarization must remain unchanged');
assert.equal(mixedSaved.nativeSetObservations.events.length,mixed.events.length);
assert.equal(prepareCanonicalWorkoutHistory(fixture.snapshot,mixedResult.candidateSnapshot,JSON.stringify(mixed),fixture.issued).alreadyPresent,true);
// Real engine-issued advanced-technique extensions must survive the entire
// native session, stay distinct from working-set ordinals and round-trip to
// canonical history. This is not a synthetic hand-authored row.
for (const [tech,kind,count] of [['myo-reps','myo',3],['drop set','drop',2]]) {
  const store=JSON.parse(fixture.snapshot);
  const first=fixture.plan.rows[0];
  const key=`${fixture.plan.session.dayId}:${first.slot}`;
  const week=fixture.plan.session.weekIndex;
  assert.ok(store.saved[0].nextWeekPrescriptions[key]?.[week],
    'test needs an actual canonical weekly prescription');
  store.saved[0].nextWeekPrescriptions[key][week].tech=tech;
  const snapshot=JSON.stringify(store);
  const plan=prepareNativeWorkout(snapshot,fixture.startJson);
  const extensions=plan.rows[0].sets.filter(row=>row.sub);
  assert.equal(extensions.length,count);
  assert.ok(extensions.every(row=>row.kind===kind && row.target.rest===15));
  assert.equal(issuedNativeWorkoutFrom(snapshot,JSON.stringify(plan)).slots.length,
    plan.rows.reduce((sum,row)=>sum+row.sets.filter(set=>!set.warm).length,0));
  const journal=JSON.stringify(finishedIssuedJournal(plan,{skipSecond:false}));
  const prepared=prepareCanonicalWorkoutHistory(snapshot,snapshot,journal,JSON.stringify(plan));
  const history=prepared.historyEntry;
  assert.equal(history.nativeSetObservations.events.length,JSON.parse(journal).slots.length);
  const persisted=history.perf[first.id].sets.filter(set=>set.sub);
  assert.equal(persisted.length,count);
  assert.ok(persisted.every(set=>set.kind===kind));
  assert.ok(persisted.every(set=>!Object.hasOwn(set,'workingSetIndex')),
    'extensions must not count as ordinary working-set ordinals');
  assert.equal(prepareCanonicalWorkoutHistory(snapshot,prepared.candidateSnapshot,journal,JSON.stringify(plan)).alreadyPresent,true);
  const extensionIndex=plan.rows[0].sets.filter(set=>!set.warm).findIndex(set=>set.sub);
  assert.ok(extensionIndex>=1);
  const locateExtension=copy=>copy.events.find(event=>event.slot.exerciseIndex===first.slot &&
    event.slot.setIndex===extensionIndex);
  const skipped=JSON.parse(journal);
  const skippedExtension=locateExtension(skipped);
  skippedExtension.disposition='SKIPPED';skippedExtension.values=null;
  const skippedHistory=prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(skipped),JSON.stringify(plan)).historyEntry;
  assert.equal(skippedHistory.perf[first.id].sets.filter(set=>set.sub).length,count-1);
  assert.equal(skippedHistory.nativeSetObservations.events.filter(event=>event.disposition==='SKIPPED').length,1);
  const zero=JSON.parse(journal);
  locateExtension(zero).values.load='0';
  const mixed=prepareCanonicalWorkoutHistory(snapshot,snapshot,JSON.stringify(zero),JSON.stringify(plan)).historyEntry;
  assert.equal(mixed.nativeSetObservations.events.find(event=>event.slot.exerciseIndex===first.slot &&
    event.slot.setIndex===extensionIndex).values.load,'0');
  assert.equal(mixed.perf[first.id].sets.filter(set=>set.sub).length,count-1,
    'mixed-load zero extensions survive in raw journal observations without changing the web summary');
  const truncated=structuredClone(plan);
  truncated.rows[0].sets.pop();
  assert.throws(()=>issuedNativeWorkoutFrom(snapshot,JSON.stringify(truncated)),
    'extension omission must fail before the native journal opens');
}
console.log('PASS native workouts: four real generated splits, web-path targets, rotation/week advance, retained traces and observations, engine-issued myo/drop extensions (including mixed zero loads), actual RIR, skipped ordinals, exact retry and isolated channels');
