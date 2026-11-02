/** Pure, engine-issued session. The native owner must pin this before logging. */
import {
  parseStoredData, nextSessionCursor, exerciseAt, computeCell, EX_BY_ID,
  buildLifterModel, calibratedAnchorPerf, sessionSuggestion, prescribeSets,
  lastDayPerf, effectiveRest, goalForDay, weeksOf, techniqueProtocolFromCell, oneShotDeloadFactor,
  planShorterSession,secondaryOf
} from '../../modules/engine-api.js';
import { inspectNativeProgram } from './native-onboarding.mjs';
import { deriveReadOnlyProgramReference, matchesCurrentReadOnlyProgram } from './canonical-snapshot-reference.mjs';
import { sha256Utf8, utf8ByteLength, equalJson } from './json-identity.mjs';

export const NATIVE_WORKOUT_SOURCE='modules/engine-api.js:nextSessionCursor+calibratedAnchorPerf+sessionSuggestion+prescribeSets';
export const MAX_ISSUED_WORKOUT_BYTES=512*1024;
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length && keys.every(k=>Object.hasOwn(v,k));
const id=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const integer=(v,min,max)=>Number.isSafeInteger(v) && v>=min && v<=max;
const ensure=v=>{if(!v)throw new TypeError('Native workout preparation rejected');};
const bounded=(v,max)=>typeof v==='string' && v.length>0 && utf8ByteLength(v)<=max;
const validDeloads=program=>program.deloadNext==null || object(program.deloadNext) &&
  Object.entries(program.deloadNext).every(([id,factor])=>typeof factor==='number' && Object.hasOwn(EX_BY_ID,id) &&
    oneShotDeloadFactor(program,id)===factor);
const slotsFor=rows=>rows.flatMap(row=>row.sets.filter(set=>!set.warm).map((_,setIndex)=>
  ({exerciseIndex:row.slot,setIndex,exerciseId:row.id})));

export function prepareNativeWorkout(rawSnapshot,startJson) {
  ensure(bounded(rawSnapshot,1024*1024) && bounded(startJson,16384));
  const start=JSON.parse(startJson);
  const shortened=Object.hasOwn(start,'minutes');
  ensure(exact(start,shortened?['programId','workoutId','startedAt','minutes']:
    ['programId','workoutId','startedAt']) &&
    (!shortened || [20,30,45].includes(start.minutes)) && id(start.programId) &&
    id(start.workoutId) && start.workoutId!==start.programId && integer(start.startedAt,1,8_640_000_000_000_000));
  // Only an explicitly saved native program enters execution. Backup inspection
  // and read-only day navigation do not call or authorize this flow.
  const owned=inspectNativeProgram(rawSnapshot);
  ensure(owned.program.program.id===start.programId);
  const store=parseStoredData(rawSnapshot),program=store.saved.find(p=>p.id===start.programId);
  const reference=deriveReadOnlyProgramReference(rawSnapshot,start.programId);
  ensure(reference && start.startedAt>=program.createdAt &&
    !Object.hasOwn(store.tombs,start.workoutId) &&
    ['saved','history','cycles','custom','gyms'].every(key=>!store[key].some(row=>row.id===start.workoutId)) &&
    validDeloads(program));
  const cursor=nextSessionCursor(program,store.history),weekIndex=cursor.weekIndex;
  ensure(integer(weekIndex,1,weeksOf(program)) && integer(cursor.dayIndex,0,program.days.length-1));
  const day=program.days[cursor.dayIndex],unit=program.config.unit;
  // Only the canonical policy may reduce the issued roster and working dose.
  // Unfit requests fail closed; the full session remains available unchanged.
  const sessionLengthPlan=shortened?planShorterSession(program,day,weekIndex,start.minutes):null;
  ensure(!sessionLengthPlan || sessionLengthPlan.fits);
  const issuedDay=sessionLengthPlan?{...day,sessionLengthPlan}:day;
  const lifter=buildLifterModel(store.history,store.perf,program);
  const anchors=calibratedAnchorPerf(program,day,store.perf,store.history,weekIndex,lifter.effort);
  const previous=lastDayPerf(day,store.perf,store.history,program);
  const rows=day.exercises.map((_,slot)=>{
    const exerciseId=exerciseAt(program,day,slot,weekIndex),ex=EX_BY_ID[exerciseId];
    ensure(ex);
    if(!(computeCell(program,issuedDay,exerciseId,slot,weekIndex).sets>0))return null;
    const suggestion=sessionSuggestion(program,day,slot,anchors,unit,weekIndex,store.history);
    const sets=prescribeSets(program,issuedDay,ex,slot,weekIndex,unit,suggestion,anchors,store.perf,store.history,true);
    // Every engine-issued extension has its own journal slot. Never strip
    // myo/drop rows from a personal workout or synthesize new prescriptions.
    ensure(sets.filter(set=>!set.warm).length<=20);
    const prior=previous[exerciseId];
    const repeated=!!prior?.duplicateExerciseSlots || day.exercises.filter((_,i)=>
      exerciseAt(program,day,i,weekIndex)===exerciseId).length>1;
    const last=(prior?.sets || []).filter(set=>!set.warm && !set.sub && set.done!==false &&
      (!repeated || set.decisionTrace?.programId===program.id && set.decisionTrace.dayId===day.id &&
        set.decisionTrace.exerciseId===exerciseId && set.decisionTrace.slot===slot));
    const positioned=last.some(set=>integer(set.workingSetIndex,0,19));
    const previousSets=last.map((set,index)=>({workingSetIndex:positioned?set.workingSetIndex:index,
      load:String(set.w),reps:set.r,unit:prior.unit || unit}));
    return {id:exerciseId,slot,name:ex.name,
      restSeconds:effectiveRest(goalForDay(program,day),ex,slot===day.primaryIndex,
        program.overrides?.[`${day.id}:${slot}`]?.rest || 0,1),
      superset:!program.config.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
      previous:previousSets,sets};
  }).filter(Boolean);
  const result=JSON.parse(JSON.stringify({format:shortened?2:1,source:NATIVE_WORKOUT_SOURCE,preparedOnly:true,persisted:false,
    inputStartSha256:sha256Utf8(startJson),session:{reference,workoutId:start.workoutId,dayId:day.id,
      weekIndex,unit,startedAt:start.startedAt},programName:program.name,dayLabel:day.label,
    ...(sessionLengthPlan?{sessionLengthPlan}:{}),
    ...(program.config.percentScheme==='gzclp'?{allowFailedAttempts:true}:{}),rows}));
  issuedNativeWorkoutFrom(rawSnapshot,JSON.stringify(result));
  return result;
}

/**
 * Read-only canonical projection for a prospective next workout.
 * This never enters the native journal and cannot modify pinned prescriptions.
 */
export function previewNativeShorterSession(rawSnapshot,programId,minutes) {
  ensure(bounded(rawSnapshot,1024*1024) && id(programId) && [20,30,45].includes(minutes));
  const owned=inspectNativeProgram(rawSnapshot),store=parseStoredData(rawSnapshot);
  ensure(owned.program.program.id===programId && !Object.hasOwn(store.tombs,programId));
  const program=store.saved.find(p=>p.id===programId);
  ensure(program && validDeloads(program));
  const cursor=nextSessionCursor(program,store.history);
  ensure(integer(cursor.dayIndex,0,program.days.length-1) && integer(cursor.weekIndex,1,weeksOf(program)));
  const day=program.days[cursor.dayIndex];
  const preview=planShorterSession(program,day,cursor.weekIndex,minutes);
  ensure(preview.schemaVersion===1 && preview.programId===program.id &&
    preview.dayId===day.id && preview.weekIndex===cursor.weekIndex &&
    preview.minutes===minutes && Array.isArray(preview.cuts) &&
    preview.cuts.length<=48 && Number.isSafeInteger(preview.originalMinutes) &&
    Number.isSafeInteger(preview.estimatedMinutes));
  return {source:'modules/engine-api.js:planShorterSession',snapshotSha256:sha256Utf8(rawSnapshot),
    programId,dayId:day.id,weekIndex:cursor.weekIndex,minutes,
    originalMinutes:preview.originalMinutes,estimatedMinutes:preview.estimatedMinutes,
    fits:preview.fits,changed:preview.changed,
    cuts:preview.cuts.map(c=>({slot:c.slot,name:c.name,fromSets:c.fromSets,
      toSets:c.toSets,amplifierRemoved:c.amplifierRemoved})),
    readOnly:true,applied:false};
}

/**
 * Canonical muscle involvement from an immutable issued workout.
 * No medical judgment, diagnosis, prescription change or journal write.
 */
export function inspectNativeWorkoutMuscles(rawSnapshot,issuedJson) {
  ensure(bounded(rawSnapshot,1024*1024) && bounded(issuedJson,MAX_ISSUED_WORKOUT_BYTES));
  const {plan}=issuedNativeWorkoutFrom(rawSnapshot,issuedJson);
  const program=parseStoredData(rawSnapshot).saved.find(p=>p.id===plan.session.reference.programId);
  ensure(program && validDeloads(program));
  const rows=plan.rows.map(row=>{
    const ex=EX_BY_ID[row.id];
    ensure(ex && typeof ex.part==='string');
    const parts=[...new Set([ex.part,...secondaryOf(ex,program.engineV).map(([part])=>part)])].sort();
    ensure(parts.length>=1 && parts.length<=20 && parts.every(part=>/^[a-z][a-z_]{0,39}$/.test(part)));
    return {exerciseIndex:row.slot,exerciseId:row.id,name:row.name,
      primaryPart:ex.part,involvedParts:parts};
  });
  const parts=[...new Set(rows.flatMap(row=>row.involvedParts))].sort();
  ensure(parts.length>0 && parts.length<=48);
  return {source:'modules/engine-api.js:EX_BY_ID+secondaryOf',
    snapshotSha256:sha256Utf8(rawSnapshot),issuedWorkoutSha256:sha256Utf8(issuedJson),
    workoutId:plan.session.workoutId,parts,rows,readOnly:true,applied:false};
}

/** Validate retained issued targets without calculating a replacement on resume. */
export function issuedNativeWorkoutFrom(originRaw,issuedRaw) {
  ensure(bounded(issuedRaw,MAX_ISSUED_WORKOUT_BYTES));
  const p=JSON.parse(issuedRaw);
  ensure([1,2].includes(p?.format) && exact(p,['format','source','preparedOnly','persisted','inputStartSha256',
    'session','programName','dayLabel',...(p.format===2?['sessionLengthPlan']:[]),
    ...(Object.hasOwn(p,'allowFailedAttempts')?['allowFailedAttempts']:[]),'rows']) &&
    p.source===NATIVE_WORKOUT_SOURCE && p.preparedOnly===true && p.persisted===false &&
    typeof p.inputStartSha256==='string' && /^[0-9a-f]{64}$/.test(p.inputStartSha256));
  const s=p.session;
  ensure(exact(s,['reference','workoutId','dayId','weekIndex','unit','startedAt']) &&
    matchesCurrentReadOnlyProgram(originRaw,s.reference) && id(s.workoutId) && id(s.dayId) &&
    integer(s.weekIndex,1,104) && ['lb','kg'].includes(s.unit) && integer(s.startedAt,1,8_640_000_000_000_000));
  const store=parseStoredData(originRaw),program=store.saved.find(row=>row.id===s.reference.programId);
  ensure(validDeloads(program));
  ensure(!Object.hasOwn(p,'allowFailedAttempts') || p.allowFailedAttempts===true && program.config.percentScheme==='gzclp');
  const day=program.days.find(row=>row.id===s.dayId);
  const lengthPlan=p.format===2 && day && planShorterSession(program,day,s.weekIndex,p.sessionLengthPlan?.minutes);
  ensure(p.format===1 || lengthPlan?.fits===true && equalJson(lengthPlan,p.sessionLengthPlan));
  const issuedDay=lengthPlan?{...day,sessionLengthPlan:lengthPlan}:day;
  ensure(day && p.programName===program.name && p.dayLabel===day.label &&
    s.unit===program.config.unit && s.weekIndex<=weeksOf(program) &&
    Array.isArray(p.rows) && p.rows.length>=1 && p.rows.length<=48);
  const roster=day.exercises.flatMap((_,slot)=>{
    const exerciseId=exerciseAt(program,day,slot,s.weekIndex);
    return computeCell(program,issuedDay,exerciseId,slot,s.weekIndex).sets>0?[{id:exerciseId,slot}]:[];
  });
  ensure(equalJson(p.rows.map(({id,slot})=>({id,slot})),roster));
  for(const row of p.rows) {
    ensure(exact(row,['id','slot','name','restSeconds','superset','previous','sets']) &&
      row.name===EX_BY_ID[row.id]?.name && integer(row.restSeconds,0,3600) &&
      typeof row.superset==='boolean' && Array.isArray(row.previous) && row.previous.length<=20 &&
      Array.isArray(row.sets) && row.sets.length>=1 && row.sets.length<=40);
    const positioned=row.previous.length>0 && Object.hasOwn(row.previous[0],'workingSetIndex');
    const previousPositions=new Set();
    for(const [i,previous] of row.previous.entries()) {
      const position=positioned?previous.workingSetIndex:i;
      ensure(exact(previous,['load','reps','unit',...(positioned?['workingSetIndex']:[])]) &&
        integer(position,0,19) && !previousPositions.has(position) &&
        typeof previous.load==='string' && /^(0|[1-9][0-9]{0,4})(\.[0-9]{1,3})?$/.test(previous.load) &&
        integer(previous.reps,0,100) && ['lb','kg'].includes(previous.unit));
      previousPositions.add(position);
    }
    let working=0, extension=0, extensionKind=null, reachedExtension=false;
    const prescribedCell=computeCell(program,issuedDay,row.id,row.slot,s.weekIndex);
    const protocol=techniqueProtocolFromCell(prescribedCell);
    for(const set of row.sets) {
      ensure(object(set) && set.done===false && set.auto===true && typeof set.warm==='boolean' &&
        typeof set.weight==='string' && typeof set.reps==='string' &&
        set.weight.length<=16 && set.reps.length<=16 && object(set.target));
      if(set.warm) {
        ensure(!reachedExtension);
      } else if(set.sub===true) {
        reachedExtension=true;extension++;
        ensure(set.prescribed===true && ['myo','drop'].includes(set.kind) &&
          !Object.hasOwn(set.target,'decisionTrace') && !set.warm &&
          (set.kind==='myo' && set.myo===true || set.kind==='drop' && typeof set.dropF==='number') &&
          integer(set.target.rest,1,60) &&
          (extensionKind===null || extensionKind===set.kind));
        extensionKind=set.kind;
      } else {
        ensure(!reachedExtension && !set.sub);
        const trace=set.target.decisionTrace;
        ensure(trace?.schemaVersion===1 && trace.programId===program.id && trace.dayId===day.id &&
          trace.exerciseId===row.id && trace.slot===row.slot && trace.weekIndex===s.weekIndex &&
          trace.workingSetIndex===working++);
      }
    }
    ensure(working>=1 && working<=20 && working+extension<=20);
    if(lengthPlan) ensure(working===lengthPlan.slots[row.slot]?.sets);
    // A forged or incomplete extension roster is never a valid issued session.
    if(protocol.type==='myo_reps')
      ensure(extensionKind==='myo' && extension===protocol.miniSets.minimum);
    else if(protocol.type==='drop_set')
      ensure(extensionKind==='drop' && extension===protocol.drops.length);
    else ensure(extension===0);
  }
  const slots=slotsFor(p.rows);ensure(slots.length>=1 && slots.length<=480);
  return {plan:p,slots};
}
