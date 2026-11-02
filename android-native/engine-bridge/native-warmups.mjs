/** Warm-up observations and original targets. Never contributes to working performance. */
import { sha256Utf8,equalJson } from './json-identity.mjs';

const own=(v,k)=>Object.prototype.hasOwnProperty.call(v,k);
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length && keys.every(k=>own(v,k));
const integer=(v,min,max)=>Number.isSafeInteger(v) && v>=min && v<=max;
const identifier=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const ensure=v=>{if(!v)throw new TypeError('Warm-up observations unavailable');};
const slotKey=s=>`${s.exerciseIndex}:${s.warmupIndex}`;
const clone=v=>JSON.parse(JSON.stringify(v));
export const warmupHistoryOperationId=(id,index)=>'warmup-edit-'+sha256Utf8(id+':'+index).slice(0,40);

function validValues(value,unit) {
  return exact(value,['load','unit','reps','rir']) && typeof value.load==='string' &&
    /^(0|[1-9][0-9]{0,4})(\.[0-9]{1,3})?$/.test(value.load) && value.unit===unit &&
    integer(value.reps,0,100) && (value.reps>0 || Number(value.load)>0) &&
    (value.rir===null || integer(value.rir,0,10));
}

export function readNativeWarmups(facts,unit,startedAt,until,excludedOperations=[]) {
  ensure(exact(facts,['format','exercises','events']) && facts.format===1 && ['lb','kg'].includes(unit) &&
    Array.isArray(facts.exercises) && facts.exercises.length>=1 && facts.exercises.length<=48 &&
    Array.isArray(facts.events) && facts.events.length>=0 && facts.events.length<=1024);
  const roster=new Map();
  for(const row of facts.exercises) {
    ensure(exact(row,['exerciseIndex','exerciseId','name','targets']) && integer(row.exerciseIndex,0,47) &&
      identifier(row.exerciseId) && row.exerciseId.length<=80 && typeof row.name==='string' &&
      row.name.length>=1 && row.name.length<=200 && !roster.has(row.exerciseIndex) &&
      Array.isArray(row.targets) && row.targets.length<=20);
    for(const target of row.targets)ensure(object(target) && target.warm===true && !target.sub &&
      typeof target.weight==='string' && target.weight.length<=16 &&
      typeof target.reps==='string' && target.reps.length<=16);
    roster.set(row.exerciseIndex,row);
  }
  const positions=new Map(),operations=new Set(excludedOperations);
  for(const event of facts.events) {
    ensure(exact(event,['operationId','slot','disposition','values','recordedAt','replacesOperationId']) &&
      identifier(event.operationId) && !operations.has(event.operationId) &&
      exact(event.slot,['exerciseIndex','warmupIndex','exerciseId']) && integer(event.slot.exerciseIndex,0,47) &&
      integer(event.slot.warmupIndex,0,19) && integer(event.recordedAt,startedAt,until));
    const s=event.slot,row=roster.get(s.exerciseIndex),key=slotKey(s),prior=positions.get(key);
    ensure(row && s.exerciseId===row.exerciseId &&
      (prior ? event.replacesOperationId===prior.operationId && event.recordedAt>prior.recordedAt : event.replacesOperationId===null));
    if(!prior && s.warmupIndex>=row.targets.length) {
      const added=[...positions.values()].filter(e=>e.slot.exerciseIndex===s.exerciseIndex && e.slot.warmupIndex>=row.targets.length).length;
      ensure(s.warmupIndex===row.targets.length+added);
    }
    ensure(event.disposition==='SKIPPED' ? event.values===null : event.disposition==='COMPLETED' && validValues(event.values,unit));
    operations.add(event.operationId);positions.set(key,event);
  }
  const result=[];
  for(const row of [...roster.values()].sort((a,b)=>a.exerciseIndex-b.exerciseIndex)) {
    const observed=[...positions.values()].filter(e=>e.slot.exerciseIndex===row.exerciseIndex);
    const count=Math.max(row.targets.length,...observed.map(e=>e.slot.warmupIndex+1));
    for(let index=0;index<count;index++) {
      const slot={exerciseIndex:row.exerciseIndex,warmupIndex:index,exerciseId:row.exerciseId};
      const event=positions.get(slotKey(slot)),target=row.targets[index];
      result.push({slot,name:row.name,targetLoad:target?.weight??null,targetReps:target?.reps??null,
        disposition:event?.disposition??null,values:event?.values??null});
    }
  }
  return result;
}

export function nativeWarmupFacts(issued,journal) {
  ensure(issued && journal.format===6 && Array.isArray(journal.warmups) && (journal.warmups.length>0 || issued.warmupLogging===true));
  const facts={format:1,exercises:issued.rows.map(row=>({exerciseIndex:row.slot,exerciseId:row.id,
    name:row.name,targets:row.sets.filter(set=>set.warm)})),events:journal.warmups};
  readNativeWarmups(facts,journal.session.unit,journal.session.startedAt,journal.finish.completedAt,
    [...journal.events.map(e=>e.operationId),journal.finish.operationId,...(journal.note?[journal.note.operationId]:[])]);
  return facts;
}

export function nativeWarmupHistoryRows(entry) {
  return readNativeWarmups(entry.nativeWarmups,entry.unit,1,entry.updatedAt||entry.date,
    entry.nativeSessionNote?[entry.nativeSessionNote.operationId]:[]);
}

export function applyNativeWarmupChanges(original,request,updatedAt) {
  const rows=nativeWarmupHistoryRows(original),facts=clone(original.nativeWarmups);
  ensure(facts.events.length+request.changes.length<=1024);
  const seen=new Set();
  for(const [index,change] of request.changes.entries()) {
    ensure(exact(change,['slot','disposition','values']) &&
      rows.some(row=>equalJson(row.slot,change.slot)) && !seen.has(slotKey(change.slot)));
    seen.add(slotKey(change.slot));
    const prior=facts.events.filter(e=>equalJson(e.slot,change.slot)).at(-1);
    facts.events.push({operationId:warmupHistoryOperationId(request.operationId,index),slot:clone(change.slot),
      disposition:change.disposition,values:clone(change.values),recordedAt:updatedAt,replacesOperationId:prior?.operationId??null});
  }
  readNativeWarmups(facts,original.unit,1,updatedAt,original.nativeSessionNote?[original.nativeSessionNote.operationId]:[]);
  return {...clone(original),nativeWarmups:facts};
}
