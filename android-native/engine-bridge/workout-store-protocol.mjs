/** Dedicated, pure workout-store preparation channel. It never writes storage. */
import { canonicalBackupSchemaCheck } from './backup-schema-check.mjs';
import { prepareCanonicalWorkoutHistory } from './workout-history-completion.mjs';
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';
import { nativeProgramOptions,prepareNativeProgram,inspectNativeProgram } from './native-onboarding.mjs';
import { prepareNativeWorkout,MAX_ISSUED_WORKOUT_BYTES } from './native-workout.mjs';
import { inspectNativeHistory,prepareNativeHistoryCorrection } from './native-history.mjs';

export const WORKOUT_STORE_LIMITS=Object.freeze({
  snapshotBytes:1024*1024,journalBytes:256*1024,
  requestBytes:12*1024*1024,responseBytes:8*1024*1024
});
const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length &&
  keys.every(key=>Object.prototype.hasOwnProperty.call(v,key));
const bounded=(v,limit)=>typeof v==='string' && v.length>0 && v.length<=limit &&
  utf8ByteLength(v)<=limit;
const error=(id,code)=>JSON.stringify({protocolVersion:1,id,ok:false,
  error:{code,message:'Canonical workout-store preparation unavailable'}});

export function dispatchWorkoutStoreJson(json) {
  if (!bounded(json,WORKOUT_STORE_LIMITS.requestBytes)) return error(null,'INVALID_REQUEST');
  let request,id=null;
  try {
    request=JSON.parse(json);
    if (!exact(request,['protocolVersion','id','method','params']) ||
        typeof request.id!=='string' || !/^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(request.id))
      return error(null,'INVALID_REQUEST');
    id=request.id;
    if (request.protocolVersion!==1) return error(id,'UNSUPPORTED_VERSION');
    const p=request.params;
    let result;
    if (request.method==='inspectNativeHistory') {
      if(!exact(p,['rawSnapshot','historyId']) || !bounded(p.rawSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes) ||
        !bounded(p.historyId,128))return error(id,'INVALID_REQUEST');
      result=inspectNativeHistory(p.rawSnapshot,p.historyId);
    } else if (request.method==='prepareNativeHistoryCorrection') {
      if(!exact(p,['currentSnapshot','correctionJson']) || !bounded(p.currentSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes) ||
        !bounded(p.correctionJson,16384))return error(id,'INVALID_REQUEST');
      result=prepareNativeHistoryCorrection(p.currentSnapshot,p.correctionJson);
    } else if (request.method==='prepareNativeWorkout') {
      if(!exact(p,['rawSnapshot','startJson']) || !bounded(p.rawSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes) ||
        !bounded(p.startJson,16384))return error(id,'INVALID_REQUEST');
      result=prepareNativeWorkout(p.rawSnapshot,p.startJson);
    } else if (request.method==='nativeProgramOptions') {
      if (!exact(p,[])) return error(id,'INVALID_REQUEST');
      result=nativeProgramOptions();
    } else if (request.method==='prepareNativeProgram') {
      if (!exact(p,['setupJson']) || !bounded(p.setupJson,16384)) return error(id,'INVALID_REQUEST');
      result=prepareNativeProgram(p.setupJson);
    } else if (request.method==='inspectNativeProgram') {
      if (!exact(p,['rawSnapshot']) || !bounded(p.rawSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes))
        return error(id,'INVALID_REQUEST');
      result=inspectNativeProgram(p.rawSnapshot);
    } else if (request.method==='validateOwnedStore') {
      if (!exact(p,['rawSnapshot']) || !bounded(p.rawSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes))
        return error(id,'INVALID_REQUEST');
      canonicalBackupSchemaCheck(p); // canonical v13 parser; no Kotlin schema replica
      result={source:'modules/engine-api.js:parseStoredData',validated:true,
        storeVersion:13,snapshotSha256:sha256Utf8(p.rawSnapshot),approvedForWorkoutExecution:false};
    } else if (request.method==='prepareWorkoutHistory' || request.method==='prepareIssuedWorkoutHistory') {
      const issued=request.method==='prepareIssuedWorkoutHistory';
      if (!exact(p,['originSnapshot','currentSnapshot','finishedJournal',...(issued?['issuedWorkout']:[])]) ||
          !bounded(p.originSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes) ||
          !bounded(p.currentSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes) ||
          !bounded(p.finishedJournal,WORKOUT_STORE_LIMITS.journalBytes) ||
          issued && !bounded(p.issuedWorkout,MAX_ISSUED_WORKOUT_BYTES))
        return error(id,'INVALID_REQUEST');
      result=prepareCanonicalWorkoutHistory(p.originSnapshot,p.currentSnapshot,p.finishedJournal,issued?p.issuedWorkout:null);
      if (!bounded(result.candidateSnapshot,WORKOUT_STORE_LIMITS.snapshotBytes))
        return error(id,'PREPARATION_REJECTED');
    } else return error(id,'UNKNOWN_METHOD');
    const response=JSON.stringify({protocolVersion:1,id,ok:true,result});
    return utf8ByteLength(response)<=WORKOUT_STORE_LIMITS.responseBytes
      ? response : error(id,'PREPARATION_REJECTED');
  } catch {
    // Schema exceptions may contain the entire raw source. Never expose them.
    return error(id,'PREPARATION_REJECTED');
  }
}
