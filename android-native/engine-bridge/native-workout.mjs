/** Pure, engine-issued session. The native owner must pin this before logging. */
import {
  parseStoredData, nextSessionCursor, exerciseAt, computeCell, EX_BY_ID,
  buildLifterModel, calibratedAnchorPerf, sessionSuggestion, prescribeSets,
  lastDayPerf, effectiveRest, goalForDay, weeksOf, computeCell as cellForProtocol,
  techniqueProtocolFromCell
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
const slotsFor=rows=>rows.flatMap(row=>row.sets.filter(set=>!set.warm).map((_,setIndex)=>
  ({exerciseIndex:row.slot,setIndex,exerciseId:row.id})));

export function prepareNativeWorkout(rawSnapshot,startJson) {
  ensure(bounded(rawSnapshot,1024*1024) && bounded(startJson,16384));
  const start=JSON.parse(startJson);
  ensure(exact(start,['programId','workoutId','startedAt']) && id(start.programId) &&
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
    !Object.keys(program.deloadNext || {}).length);
  const cursor=nextSessionCursor(program,store.history),weekIndex=cursor.weekIndex;
  ensure(integer(weekIndex,1,weeksOf(program)) && integer(cursor.dayIndex,0,program.days.length-1));
  const day=program.days[cursor.dayIndex],unit=program.config.unit;
  const lifter=buildLifterModel(store.history,store.perf,program);
  const anchors=calibratedAnchorPerf(program,day,store.perf,store.history,weekIndex,lifter.effort);
  const previous=lastDayPerf(day,store.perf,store.history,program);
  const rows=day.exercises.map((_,slot)=>{
    const exerciseId=exerciseAt(program,day,slot,weekIndex),ex=EX_BY_ID[exerciseId];
    ensure(ex);
    if(!(computeCell(program,day,exerciseId,slot,weekIndex).sets>0))return null;
    const suggestion=sessionSuggestion(program,day,slot,anchors,unit,weekIndex,store.history);
    const sets=prescribeSets(program,day,ex,slot,weekIndex,unit,suggestion,anchors,store.perf,store.history,true);
    // Every engine-issued extension has its own journal slot. Never strip
    // myo/drop rows from a personal workout or synthesize new prescriptions.
    ensure(sets.filter(set=>!set.warm).length<=20);
    const last=(previous[exerciseId]?.sets || []).filter(set=>!set.warm && !set.sub && set.done!==false);
    return {id:exerciseId,slot,name:ex.name,
      restSeconds:effectiveRest(goalForDay(program,day),ex,slot===day.primaryIndex,
        program.overrides?.[`${day.id}:${slot}`]?.rest || 0,1),
      superset:!program.config.noSupersets && !!program.ss?.[`${day.id}:${slot}`],
      previous:last.map(set=>({load:String(set.w),reps:set.r,unit:previous[exerciseId].unit || unit})),sets};
  }).filter(Boolean);
  const result=JSON.parse(JSON.stringify({format:1,source:NATIVE_WORKOUT_SOURCE,preparedOnly:true,persisted:false,
    inputStartSha256:sha256Utf8(startJson),session:{reference,workoutId:start.workoutId,dayId:day.id,
      weekIndex,unit,startedAt:start.startedAt},programName:program.name,dayLabel:day.label,rows}));
  issuedNativeWorkoutFrom(rawSnapshot,JSON.stringify(result));
  return result;
}

/** Validate retained issued targets without calculating a replacement on resume. */
export function issuedNativeWorkoutFrom(originRaw,issuedRaw) {
  ensure(bounded(issuedRaw,MAX_ISSUED_WORKOUT_BYTES));
  const p=JSON.parse(issuedRaw);
  ensure(exact(p,['format','source','preparedOnly','persisted','inputStartSha256','session','programName','dayLabel','rows']) &&
    p.format===1 && p.source===NATIVE_WORKOUT_SOURCE && p.preparedOnly===true && p.persisted===false &&
    typeof p.inputStartSha256==='string' && /^[0-9a-f]{64}$/.test(p.inputStartSha256));
  const s=p.session;
  ensure(exact(s,['reference','workoutId','dayId','weekIndex','unit','startedAt']) &&
    matchesCurrentReadOnlyProgram(originRaw,s.reference) && id(s.workoutId) && id(s.dayId) &&
    integer(s.weekIndex,1,104) && ['lb','kg'].includes(s.unit) && integer(s.startedAt,1,8_640_000_000_000_000));
  const store=parseStoredData(originRaw),program=store.saved.find(row=>row.id===s.reference.programId);
  const day=program.days.find(row=>row.id===s.dayId);
  ensure(day && p.programName===program.name && p.dayLabel===day.label &&
    s.unit===program.config.unit && s.weekIndex<=weeksOf(program) &&
    Array.isArray(p.rows) && p.rows.length>=1 && p.rows.length<=48);
  const roster=day.exercises.flatMap((_,slot)=>{
    const exerciseId=exerciseAt(program,day,slot,s.weekIndex);
    return computeCell(program,day,exerciseId,slot,s.weekIndex).sets>0?[{id:exerciseId,slot}]:[];
  });
  ensure(equalJson(p.rows.map(({id,slot})=>({id,slot})),roster));
  for(const row of p.rows) {
    ensure(exact(row,['id','slot','name','restSeconds','superset','previous','sets']) &&
      row.name===EX_BY_ID[row.id]?.name && integer(row.restSeconds,0,3600) &&
      typeof row.superset==='boolean' && Array.isArray(row.previous) && row.previous.length<=20 &&
      Array.isArray(row.sets) && row.sets.length>=1 && row.sets.length<=40);
    let working=0, extension=0, extensionKind=null, reachedExtension=false;
    const protocol=techniqueProtocolFromCell(cellForProtocol(program,day,row.id,row.slot,s.weekIndex));
    for(const set of row.sets) {
      ensure(object(set) && set.done===false && set.auto===true && typeof set.warm==='boolean' &&
        typeof set.weight==='string' && typeof set.reps==='string' &&
        set.weight.length<=16 && set.reps.length<=16 && object(set.target));
      if(set.warm) {
        ensure(!reachedExtension);
      } else if(set.sub===true) {
        reachedExtension=true;extension++;
        ensure(set.prescribed===true && ['myo','drop'].includes(set.kind) &&
          !own(set.target,'decisionTrace') && !set.warm &&
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
