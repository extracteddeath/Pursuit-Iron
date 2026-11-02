/** Original issued evidence for saved observation edits. No summaries or writes. */
import { equalJson } from './json-identity.mjs';
import { EX_BY_ID } from '../../modules/engine-api.js';

const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const own=(v,k)=>Object.prototype.hasOwnProperty.call(v,k);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length && keys.every(k=>own(v,k));
const integer=(v,min,max)=>Number.isSafeInteger(v) && v>=min && v<=max;
const identifier=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/.test(v);
const clone=v=>JSON.parse(JSON.stringify(v));
const ensure=v=>{if(!v)throw new TypeError('Native history edit basis unavailable');};
const key=slot=>`${slot.exerciseIndex}:${slot.setIndex}`;

/** Remove actual values; targets and context are copied, never re-prescribed. */
export function nativeSetTemplate(set) {
  const out=clone(set);
  for(const field of ['w','r','rir','rirReported','failedAttempt'])delete out[field];
  if(out.decisionTrace)delete out.decisionTrace.applied;
  return out;
}
export function nativePerformanceTemplate(performance) {
  const out=clone(performance);
  for(const field of ['weight','reps','date','sets'])delete out[field];
  return out;
}

/** Every original slot resolves once, from retained evidence or an omitted template. */
export function readNativeHistoryBasis(entry,mapped) {
  const basis=entry.nativeHistoryBasis;
  ensure(exact(basis,['format','rows']) && basis.format===1 && Array.isArray(basis.rows) &&
    basis.rows.length>=1 && basis.rows.length<=48 && entry.nativeSetLocations?.format===1);
  const events=entry.nativeSetObservations.events;
  ensure(events.length===entry.totalSets && events.filter(e=>e.disposition==='COMPLETED').length===entry.setsDone);
  const retained=new Map(mapped.map(set=>[key(set.event.slot),set]));
  const observations=new Map(events.map((event,eventIndex)=>[key(event.slot),{event,eventIndex}]));
  const result=[];let previous=-1;
  for(const row of basis.rows) {
    ensure(exact(row,['exerciseIndex','exerciseId','setCount','performance','omitted']) &&
      integer(row.exerciseIndex,0,47) && row.exerciseIndex>previous && identifier(row.exerciseId) && own(EX_BY_ID,row.exerciseId) &&
      integer(row.setCount,1,20) && object(row.performance) &&
      equalJson(row.performance,nativePerformanceTemplate(row.performance)) &&
      integer(row.performance.prescription?.sets,1,20) && Array.isArray(row.omitted) && row.omitted.length<=row.setCount);
    previous=row.exerciseIndex;
    const omitted=new Map();
    for(const value of row.omitted) {
      ensure(exact(value,['setIndex','template']) && integer(value.setIndex,0,row.setCount-1) &&
        !omitted.has(value.setIndex) && object(value.template) && equalJson(value.template,nativeSetTemplate(value.template)));
      omitted.set(value.setIndex,value.template);
    }
    for(let setIndex=0;setIndex<row.setCount;setIndex++) {
      const slot={exerciseIndex:row.exerciseIndex,setIndex,exerciseId:row.exerciseId};
      const observation=observations.get(key(slot)),saved=retained.get(key(slot));
      ensure(observation && equalJson(observation.event.slot,slot) &&
        (saved ? !omitted.has(setIndex) : omitted.has(setIndex)));
      const template=saved?nativeSetTemplate(saved.set):omitted.get(setIndex);
      const sub=template.sub===true;
      ensure(!template.warm && (!own(template,'sub') || typeof template.sub==='boolean') &&
        (!own(template,'tr') || typeof template.tr==='number' && Number.isFinite(template.tr) && template.tr>=0 && template.tr<=10));
      if(sub)ensure(['myo','drop'].includes(template.kind) && !own(template,'workingSetIndex') && !own(template,'decisionTrace'));
      else {
        const trace=template.decisionTrace;
        ensure(template.workingSetIndex===setIndex && trace?.schemaVersion===1 &&
          trace.programId===entry.programId && trace.dayId===entry.dayId && trace.weekIndex===entry.weekIndex &&
          trace.exerciseId===row.exerciseId && trace.slot===row.exerciseIndex && trace.workingSetIndex===setIndex);
      }
      if(!saved && observation.event.disposition==='COMPLETED')ensure(Number(observation.event.values.load)===0);
      result.push({slot,template,performance:row.performance,kind:sub?template.kind:'working',
        retained:!!saved,perfIndex:saved?.setIndex ?? null,...observation});
    }
  }
  ensure(result.length===events.length && new Set(result.map(row=>key(row.slot))).size===events.length);
  return result;
}
