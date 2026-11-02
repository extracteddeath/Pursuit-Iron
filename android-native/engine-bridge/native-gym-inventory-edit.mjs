/** Private native-only physical loading edit. Never recalculates training decisions. */
import { parseStoredData } from '../../modules/engine-api.js';
import { projectCanonicalSavedProgram } from './current-program-projection.mjs';
import { authoredInventory } from './native-onboarding.mjs';
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';

const object=v=>v!==null && typeof v==='object' && !Array.isArray(v);
const exact=(v,keys)=>object(v) && Object.keys(v).length===keys.length &&
  keys.every(key=>Object.prototype.hasOwnProperty.call(v,key));
const ensure=v=>{if(!v)throw new TypeError('Physical gym inventory edit rejected');};
const id=v=>typeof v==='string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/.test(v);
const sha=v=>typeof v==='string' && /^[a-f0-9]{64}$/.test(v);
const source='modules/engine-api.js:parseStoredData:native-gym-inventory';

function verifiedRequest(raw) {
  ensure(typeof raw==='string' && utf8ByteLength(raw)<=16384);
  const request=JSON.parse(raw);
  ensure(exact(request,['version','operationId','reference','editedAt','loadingInventory']) &&
    request.version===1 && id(request.operationId) &&
    Number.isSafeInteger(request.editedAt) && request.editedAt>=1 &&
    exact(request.reference,['programId','programUpdatedAt','storeSavedAt','storeVersion','snapshotSha256']) &&
    id(request.reference.programId) &&
    Number.isSafeInteger(request.reference.programUpdatedAt) &&
    Number.isSafeInteger(request.reference.storeSavedAt) &&
    request.reference.storeVersion===13 && sha(request.reference.snapshotSha256));
  return request;
}
export function prepareNativeGymInventoryEdit(currentSnapshot,editJson) {
  ensure(typeof currentSnapshot==='string' && utf8ByteLength(currentSnapshot)<=1024*1024);
  const before=parseStoredData(currentSnapshot);
  const r=verifiedRequest(editJson),ref=r.reference;
  const requestSha256=sha256Utf8(editJson),currentSha256=sha256Utf8(currentSnapshot);
  const record=before.saved.find(p=>p.id===ref.programId);
  ensure(record && before.nativeOnboarding?.programId===ref.programId &&
    record.config && ['lb','kg'].includes(record.config.unit));
  const inventory=r.loadingInventory===null ? null : authoredInventory(r.loadingInventory,record.config.unit,record);
  const prior=before.nativeGymInventoryEdit;
  const alreadyPresent=prior?.operationId===r.operationId;
  let candidateSnapshot=currentSnapshot;
  if(alreadyPresent) {
    ensure(exact(prior,['version','operationId','requestSha256','sourceSnapshotSha256','programId','editedAt']) &&
      prior.version===1 && prior.operationId===r.operationId && prior.requestSha256===requestSha256 &&
      prior.sourceSnapshotSha256===ref.snapshotSha256 &&
      prior.programId===ref.programId && prior.editedAt===r.editedAt);
  } else {
    const projected=projectCanonicalSavedProgram(currentSnapshot,ref.programId,currentSha256);
    ensure(Object.keys(projected.reference).every(key=>projected.reference[key]===ref[key]) &&
      r.editedAt>ref.programUpdatedAt && r.editedAt>ref.storeSavedAt);
    const oldInventory=record.config.loadingInventory ?? null;
    ensure(JSON.stringify(oldInventory)!==JSON.stringify(inventory));
    // Preserve every exercise, prescription, cycle, performance record and
    // original training history. Only current program equipment facts change.
    const config={...record.config};
    if(inventory===null)delete config.loadingInventory;
    else config.loadingInventory=inventory;
    const updated={...record,updatedAt:r.editedAt,config};
    const candidate={...before,saved:before.saved.map(p=>p.id===ref.programId?updated:p),
      savedAt:r.editedAt,nativeGymInventoryEdit:{version:1,operationId:r.operationId,
        requestSha256,sourceSnapshotSha256:currentSha256,programId:ref.programId,editedAt:r.editedAt}};
    candidateSnapshot=JSON.stringify(candidate);
    ensure(utf8ByteLength(candidateSnapshot)<=1024*1024);
    parseStoredData(candidateSnapshot);
  }
  const program=projectCanonicalSavedProgram(candidateSnapshot,ref.programId,
    sha256Utf8(candidateSnapshot));
  return {source,preparedOnly:true,approvedForWorkoutExecution:false,
    operationId:r.operationId,requestSha256,sourceSnapshotSha256:currentSha256,
    candidateSnapshot,candidateSnapshotSha256:sha256Utf8(candidateSnapshot),
    alreadyPresent,program};
}
