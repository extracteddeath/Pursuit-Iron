/** Owned native history only. Canonical JS owns summaries, mirrors and records. */
import { parseStoredData,normalizeEditedHistoryEntry,perfAfterHistoryReplace,exRecords,EX_BY_ID } from '../../modules/engine-api.js';
import { sha256Utf8 as sha,utf8ByteLength,equalJson } from './json-identity.mjs';
import { readNativeHistoryBasis } from './native-history-basis.mjs';

const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const own=(v,k)=>Object.prototype.hasOwnProperty.call(v,k);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length && keys.every(k=>own(v,k));
const integer=(v,min,max)=>Number.isSafeInteger(v) && v>=min && v<=max;
const identifier=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const hash=v=>typeof v==='string' && /^[0-9a-f]{64}$/.test(v);
const load=v=>typeof v==='string' && /^(0|[1-9][0-9]{0,4})(\.[0-9]{1,3})?$/.test(v);
const ensure=v=>{if(!v)throw new TypeError('Native history unavailable');};
const clone=v=>JSON.parse(JSON.stringify(v));
const semanticSha=v=>sha(JSON.stringify(v,(_k,value)=>object(value)
  ? Object.fromEntries(Object.keys(value).sort().map(k=>[k,value[k]])):value));
export const NATIVE_HISTORY_PAGE_SIZE=12;
const emptyFilter=()=>({search:'',fromInclusive:null,untilExclusive:null});
function historyFilter(value) {
  ensure(exact(value,['search','fromInclusive','untilExclusive']) && typeof value.search==='string' &&
    value.search.length<=80 && !/[\u0000-\u001f\u007f-\u009f]/.test(value.search) &&
    (value.fromInclusive===null || integer(value.fromInclusive,-8640000000000000,8640000000000000)) &&
    (value.untilExclusive===null || integer(value.untilExclusive,-8640000000000000,8640000000000001)) &&
    (value.fromInclusive===null || value.untilExclusive===null || value.fromInclusive<value.untilExclusive));
  return value;
}
function storeFrom(raw) {
  ensure(typeof raw==='string' && utf8ByteLength(raw)<=1024*1024);
  const source=JSON.parse(raw);
  ensure(source.v===13 && integer(source.savedAt,0,Number.MAX_SAFE_INTEGER) &&
    Array.isArray(source.history) && object(source.perf) && object(source.tombs));
  return parseStoredData(raw);
}
function entryFrom(store,historyId) {
  ensure(identifier(historyId) && !own(store.tombs,historyId));
  const entries=store.history.filter(h=>h.id===historyId);
  ensure(entries.length===1);
  const entry=entries[0];
  ensure(entry.nativeCompletion?.format===2 && entry.nativeSetObservations?.format===1 &&
    Array.isArray(entry.nativeSetObservations.events) && object(entry.perf) &&
    ['lb','kg'].includes(entry.unit) && integer(entry.date,1,8640000000000000));
  return entry;
}
// Resolve each retained canonical set back to its own original observation.
// Omitted mixed-zero rows and skips stay untouched. New completions pin exact
// locations for repeated lifts; legacy ambiguous data remains summary-only.
function mappedSets(entry) {
  const events=entry.nativeSetObservations.events;
  ensure(events.length>=1 && events.length<=480);
  const positions=new Set();
  for(const event of events) {
    const s=event.slot,v=event.values;
    ensure(exact(event,['slot','disposition','values']) && exact(s,['exerciseIndex','setIndex','exerciseId']) &&
      integer(s.exerciseIndex,0,47) && integer(s.setIndex,0,19) && identifier(s.exerciseId));
    const key=`${s.exerciseIndex}:${s.setIndex}`;ensure(!positions.has(key));positions.add(key);
    ensure(event.disposition==='SKIPPED' ? v===null : event.disposition==='COMPLETED' &&
      exact(v,['load','unit','reps','rir']) && load(v.load) && v.unit===entry.unit &&
      integer(v.reps,1,100) && (v.rir===null || integer(v.rir,0,10)));
  }
  let locations=null;
  if(own(entry,'nativeSetLocations')) {
    const data=entry.nativeSetLocations;
    ensure(exact(data,['format','sets']) && data.format===1 && Array.isArray(data.sets) && data.sets.length<=480);
    locations=new Map();
    for(const location of data.sets) {
      const slot=location.slot;
      ensure(exact(location,['exerciseId','setIndex','slot']) && identifier(location.exerciseId) &&
        integer(location.setIndex,0,479) && exact(slot,['exerciseIndex','setIndex','exerciseId']) &&
        slot.exerciseId===location.exerciseId && integer(slot.exerciseIndex,0,47) && integer(slot.setIndex,0,19));
      const key=`${location.exerciseId}:${location.setIndex}`;
      ensure(!locations.has(key));locations.set(key,slot);
    }
  }
  const used=new Set(),out=[];
  for(const [exerciseId,p] of Object.entries(entry.perf)) {
    ensure(own(EX_BY_ID,exerciseId) && Array.isArray(p.sets) && p.sets.length>=1 && p.sets.length<=480 &&
      (locations || !p.duplicateExerciseSlots && p.sets.length<=20) && integer(p.prescription?.sets,1,20));
    const slots=new Set();let previous=-1;
    for(const [setIndex,set] of p.sets.entries()) {
      ensure(!set.warm && integer(set.r,1,100) && Number.isFinite(set.w) && set.w>=0 &&
        (!set.sub || ['myo','drop'].includes(set.kind)));
      const location=locations?.get(`${exerciseId}:${setIndex}`);
      ensure(!locations || location);
      const candidates=events.map((event,index)=>({event,index})).filter(({event,index})=>
        !used.has(index) && event.disposition==='COMPLETED' && event.slot.exerciseId===exerciseId &&
        (!location || location.exerciseIndex===event.slot.exerciseIndex && location.setIndex===event.slot.setIndex) &&
        Number(event.values.load)===set.w && event.values.reps===set.r &&
        (set.rirReported===true ? event.values.rir===set.rir : event.values.rir===null) &&
        (set.sub ? !!location || event.slot.setIndex>=p.prescription.sets :
          event.slot.exerciseIndex===set.decisionTrace?.slot && event.slot.setIndex===set.workingSetIndex))
        .sort((a,b)=>a.event.slot.exerciseIndex-b.event.slot.exerciseIndex || a.event.slot.setIndex-b.event.slot.setIndex);
      ensure(candidates.length>=1 && (!location && set.sub || candidates.length===1));
      const matched=candidates[0];used.add(matched.index);
      const position=matched.event.slot.exerciseIndex*20+matched.event.slot.setIndex;
      ensure(position>previous);previous=position;slots.add(matched.event.slot.exerciseIndex);
      out.push({exerciseId,setIndex,set,event:matched.event,eventIndex:matched.index});
    }
    if(p.duplicateExerciseSlots)ensure(Array.isArray(p.duplicateExerciseSlots) &&
      p.duplicateExerciseSlots.length>=2 && p.duplicateExerciseSlots.length===slots.size &&
      p.duplicateExerciseSlots.every((slot,i)=>integer(slot,0,47) && slot===[...slots][i]));
    else ensure(slots.size===1);
  }
  ensure(out.length<=480 && (!locations || locations.size===out.length));
  return out;
}
/** Snapshot-bound pages of owned native completions; never a backup/import reader. */
export function inspectNativeHistoryPage(rawSnapshot,queryJson) {
  try {
    ensure(typeof queryJson==='string' && utf8ByteLength(queryJson)<=1024);
    const query=JSON.parse(queryJson),snapshotSha256=sha(rawSnapshot);
    ensure(exact(query,own(query,'filter')?['offset','snapshotSha256','filter']:['offset','snapshotSha256']) && integer(query.offset,0,1992) &&
      query.offset%NATIVE_HISTORY_PAGE_SIZE===0 &&
      (query.snapshotSha256===null ? query.offset===0 : hash(query.snapshotSha256) && query.snapshotSha256===snapshotSha256));
    const filter=historyFilter(own(query,'filter')?query.filter:emptyFilter());
    const terms=filter.search.toLowerCase().trim().split(/\s+/u).filter(Boolean);
    const store=storeFrom(rawSnapshot);ensure(store.history.length<=2000);
    const native=store.history.filter(entry=>entry.nativeCompletion?.format===2);
    const ids=new Set();
    for(const entry of native) {
      ensure(identifier(entry.id) && !ids.has(entry.id));ids.add(entry.id);
    }
    const entries=native.filter(entry=>!own(store.tombs,entry.id)).map(entry=>{
      ensure(typeof entry.programName==='string' && entry.programName.length>=1 && entry.programName.length<=200 &&
        typeof entry.dayLabel==='string' && entry.dayLabel.length>=1 && entry.dayLabel.length<=200 &&
        integer(entry.weekIndex,1,104) && ['lb','kg'].includes(entry.unit) &&
        integer(entry.date,1,8640000000000000) && integer(entry.totalSets,1,480) &&
        integer(entry.setsDone,0,entry.totalSets) && integer(entry.volume,0,Number.MAX_SAFE_INTEGER));
      return entry;
    }).sort((a,b)=>b.date-a.date || (a.id<b.id?-1:a.id>b.id?1:0));
    const unfilteredTotal=entries.length;
    const matches=entries.filter(entry=>(filter.fromInclusive===null || entry.date>=filter.fromInclusive) &&
      (filter.untilExclusive===null || entry.date<filter.untilExclusive) &&
      terms.every(term=>(entry.programName+' '+entry.dayLabel).toLowerCase().includes(term)));
    const offset=query.offset,total=matches.length;
    ensure(offset===0 || offset<total);
    const page=matches.slice(offset,offset+NATIVE_HISTORY_PAGE_SIZE).map(entry=>{
      let detailsAvailable=false;
      try {
        const owned=entryFrom(store,entry.id),mapped=mappedSets(owned);
        if(own(owned,'nativeHistoryBasis'))readNativeHistoryBasis(owned,mapped);
        detailsAvailable=true;
      } catch { /* Summary only. */ }
      return {historyId:entry.id,programName:entry.programName,dayLabel:entry.dayLabel,
        weekIndex:entry.weekIndex,unit:entry.unit,date:entry.date,setsDone:entry.setsDone,
        totalSets:entry.totalSets,volume:entry.volume,detailsAvailable};
    });
    return {source:'modules/engine-api.js:parseStoredData',snapshotSha256,offset,total,
      nextOffset:offset+page.length<total?offset+page.length:null,entries:page,filter,unfilteredTotal};
  } catch {throw new TypeError('Native history page unavailable');}
}
export function inspectNativeHistory(rawSnapshot,historyId) {
  try {
    const store=storeFrom(rawSnapshot),entry=entryFrom(store,historyId),mapped=mappedSets(entry);
    const editRows=own(entry,'nativeHistoryBasis')?readNativeHistoryBasis(entry,mapped):null;
    return {source:'modules/engine-api.js:normalizeEditedHistoryEntry+perfAfterHistoryReplace+exRecords',
      snapshotSha256:sha(rawSnapshot),entrySha256:semanticSha(entry),historyId:entry.id,
      dayLabel:entry.dayLabel,weekIndex:entry.weekIndex,unit:entry.unit,date:entry.date,
      setsDone:entry.setsDone,totalSets:entry.totalSets,volume:entry.volume,
      exercises:Object.keys(entry.perf).map(id=>({id,name:EX_BY_ID[id].name,
        sets:mapped.filter(s=>s.exerciseId===id).map(({setIndex,set,event})=>({setIndex,
          exerciseIndex:event.slot.exerciseIndex,kind:set.sub?set.kind:'working',ordinal:event.slot.setIndex+1,
          load:event.values.load,reps:event.values.reps,rir:event.values.rir})),
        record:exRecords(store.history,id,entry.unit).maxWeight})),
      ...(editRows?{editRows:editRows.map(row=>({slot:row.slot,name:EX_BY_ID[row.slot.exerciseId]?.name,
        kind:row.kind,retained:row.retained,disposition:row.event.disposition,values:row.event.values}))}:{})};
  } catch {throw new TypeError('Native history unavailable');}
}
function applyObservationChanges(original,request,updatedAt) {
  const mapped=mappedSets(original),basis=readNativeHistoryBasis(original,mapped);
  const slotKey=slot=>`${slot.exerciseIndex}:${slot.setIndex}`;
  const bySlot=new Map(basis.map(row=>[slotKey(row.slot),row])),changes=new Map();
  for(const change of request.changes) {
    const slot=change.slot,value=change.values;
    ensure(exact(change,['slot','disposition','values']) && exact(slot,['exerciseIndex','setIndex','exerciseId']) &&
      integer(slot.exerciseIndex,0,47) && integer(slot.setIndex,0,19) && identifier(slot.exerciseId));
    const row=bySlot.get(slotKey(slot));ensure(row && equalJson(row.slot,slot) && !changes.has(slotKey(slot)));
    ensure(change.disposition==='SKIPPED' ? value===null : change.disposition==='COMPLETED' &&
      exact(value,['load','unit','reps','rir']) && load(value.load) && value.unit===original.unit &&
      integer(value.reps,1,100) && (value.rir===null || integer(value.rir,0,10)));
    changes.set(slotKey(slot),change);
  }
  ensure([...changes.values()].some(change=>{
    const before=bySlot.get(slotKey(change.slot)).event;
    return !equalJson({disposition:before.disposition,values:before.values},
      {disposition:change.disposition,values:change.values});
  }));
  const base=clone(original),draft=clone(original),performance={},locations=[],occurrences=new Map();
  const omitted=new Map(original.nativeHistoryBasis.rows.map(row=>[row.exerciseIndex,[]]));
  for(const row of basis) {
    const change=changes.get(slotKey(row.slot));
    const event=change?{slot:row.slot,disposition:change.disposition,values:change.values}:row.event;
    base.nativeSetObservations.events[row.eventIndex]=clone(event);
    // Unchanged zero-load omissions retain their original representation.
    if(event.disposition==='SKIPPED' || !row.retained && !change) {
      omitted.get(row.slot.exerciseIndex).push({setIndex:row.slot.setIndex,template:row.template});continue;
    }
    const id=row.slot.exerciseId,values=event.values;
    const set={...clone(row.template),w:Number(values.load),r:values.reps,
      ...(values.rir!==null?{rir:values.rir,rirReported:true}:
        own(row.template,'tr')?{rir:row.template.tr,rirReported:false}:{})};
    if(set.decisionTrace)set.decisionTrace.applied={load:set.w,reps:set.r,owner:'user'};
    if(!performance[id]) {
      performance[id]={...clone(row.performance),date:original.date,sets:[]};occurrences.set(id,[]);
      if(original.perf[id]?.prescriptionEdited===true)performance[id].prescriptionEdited=true;
    }
    const slots=occurrences.get(id);if(!slots.includes(row.slot.exerciseIndex))slots.push(row.slot.exerciseIndex);
    const setIndex=performance[id].sets.length;
    performance[id].sets.push(set);locations.push({exerciseId:id,setIndex,slot:row.slot});
  }
  for(const [id,slots] of occurrences)if(slots.length>1)Object.assign(performance[id],
    {prescriptionEdited:true,duplicateExerciseSlots:slots});
  base.nativeHistoryBasis={format:1,rows:original.nativeHistoryBasis.rows.map(row=>
    ({...clone(row),omitted:omitted.get(row.exerciseIndex)}))};
  base.nativeSetLocations={format:1,sets:locations};
  if(base.nativeSetAdjustments) {
    ensure(exact(base.nativeSetAdjustments,['format','events']) && base.nativeSetAdjustments.format===1 &&
      Array.isArray(base.nativeSetAdjustments.events));
    base.nativeSetAdjustments.events=base.nativeSetAdjustments.events.filter(adjustment=>
      base.nativeSetObservations.events.find(event=>equalJson(event.slot,adjustment.slot))?.disposition==='SKIPPED');
  }
  draft.perf=performance;
  // Actual RIR ownership follows its original slot across insertion/removal.
  // The canonical edit owner still computes every summary and set count.
  base.perf=performance;
  const corrected=clone(normalizeEditedHistoryEntry(base,draft));
  ensure(Object.keys(corrected.perf).length===Object.keys(performance).length &&
    Object.entries(performance).every(([id,p])=>corrected.perf[id]?.sets.length===p.sets.length));
  const audit=original.nativeHistoryRevisions || {format:1,events:[]};
  ensure(exact(audit,['format','events']) && audit.format===1 && Array.isArray(audit.events) &&
    audit.events.length+request.changes.length<=1024);
  corrected.nativeHistoryRevisions={format:1,events:[...clone(audit.events),...request.changes.map(change=>{
    const before=bySlot.get(slotKey(change.slot)).event;
    return {operationId:request.operationId,slot:change.slot,correctedAt:updatedAt,
      from:{disposition:before.disposition,values:before.values},
      to:{disposition:change.disposition,values:change.values}};
  })]};
  return corrected;
}
export function prepareNativeHistoryCorrection(currentSnapshot,correctionJson) {
  try {
    ensure(typeof correctionJson==='string' && utf8ByteLength(correctionJson)<=16384);
    const request=JSON.parse(correctionJson);
    const observationMode=own(request,'format');
    ensure((!observationMode || request.format===2) &&
      exact(request,['operationId','snapshotSha256','historyId','entrySha256','correctedAt','changes',...(observationMode?['format']:[])]) &&
      identifier(request.operationId) && hash(request.snapshotSha256) && hash(request.entrySha256) &&
      integer(request.correctedAt,1,8640000000000000) && Array.isArray(request.changes) &&
      request.changes.length>=1 && request.changes.length<=48);
    const store=storeFrom(currentSnapshot),original=entryFrom(store,request.historyId);
    const requestSha256=semanticSha(request);
    const prior=original.nativeCorrection;
    let candidateSnapshot,alreadyPresent=false;
    if(prior?.operationId===request.operationId) {
      ensure(prior.requestSha256===requestSha256);
      candidateSnapshot=currentSnapshot;alreadyPresent=true;
    } else {
      ensure(sha(currentSnapshot)===request.snapshotSha256 && semanticSha(original)===request.entrySha256);
      const updatedAt=Math.max(request.correctedAt,Number(original.updatedAt || original.date)+1,store.savedAt+1);
      ensure(Number.isSafeInteger(updatedAt));
      let corrected;
      if(observationMode)corrected=applyObservationChanges(original,request,updatedAt);
      else {
      const mapped=mappedSets(original),base=clone(original),draft=clone(original),seen=new Set();
      for(const change of request.changes) {
        ensure(exact(change,['exerciseId','setIndex','load','reps','rir']) && identifier(change.exerciseId) &&
          integer(change.setIndex,0,479) && load(change.load) && integer(change.reps,1,100) &&
          (change.rir===null || integer(change.rir,0,10)));
        const key=`${change.exerciseId}:${change.setIndex}`;ensure(!seen.has(key));seen.add(key);
        const match=mapped.find(s=>s.exerciseId===change.exerciseId && s.setIndex===change.setIndex);ensure(match);
        base.nativeSetObservations.events[match.eventIndex].values={load:change.load,unit:original.unit,reps:change.reps,rir:change.rir};
        const set=draft.perf[change.exerciseId].sets[change.setIndex];
        set.w=Number(change.load);set.r=change.reps;set.rir=change.rir;set.rirReported=change.rir!==null;
        if(set.decisionTrace?.applied)set.decisionTrace.applied={...set.decisionTrace.applied,
          load:set.w,reps:set.r,owner:'user'};
      }
      corrected=clone(normalizeEditedHistoryEntry(base,draft));
      ensure(Object.keys(corrected.perf).length===Object.keys(original.perf).length &&
        Object.entries(original.perf).every(([id,p])=>corrected.perf[id]?.sets.length===p.sets.length));
      }
      corrected.updatedAt=updatedAt;
      corrected.nativeCorrection={format:1,operationId:request.operationId,requestSha256,
        priorEntrySha256:request.entrySha256,correctedAt:corrected.updatedAt};
      const checked=mappedSets(corrected); // Reject disagreement with canonical evidence.
      if(own(corrected,'nativeHistoryBasis'))readNativeHistoryBasis(corrected,checked);
      const history=store.history.map(h=>h.id===original.id?corrected:h);
      const perf=perfAfterHistoryReplace(store.history,store.perf,original.id,corrected);
      candidateSnapshot=JSON.stringify({...store,savedAt:corrected.updatedAt,history,perf});
      storeFrom(candidateSnapshot);
    }
    ensure(utf8ByteLength(candidateSnapshot)<=1024*1024);
    return {source:'modules/engine-api.js:normalizeEditedHistoryEntry+perfAfterHistoryReplace+exRecords',
      expectedSnapshotSha256:sha(currentSnapshot),inputCorrectionSha256:sha(correctionJson),
      candidateSnapshotSha256:sha(candidateSnapshot),candidateSnapshot,alreadyPresent,
      history:inspectNativeHistory(candidateSnapshot,request.historyId)};
  } catch {throw new TypeError('Native history correction rejected');}
}
