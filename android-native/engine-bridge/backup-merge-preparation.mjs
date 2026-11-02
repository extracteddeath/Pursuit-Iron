/**
 * Privileged but NON-WRITING second phase after backup preflight.
 * Produces a candidate only if the exact sources and expected preview hash
 * still match, and there is no silent history loss or unsupported storage size.
 * The returned candidate is private transport data, NEVER UI data or import
 * authority. A separate atomic owner/Undo protocol must authorize publication.
 */
import { mergeStores, parseStoredData } from '../../modules/engine-api.js';
import { previewCanonicalBackupMerge, MERGE_PREVIEW_LIMIT } from './backup-merge-preview.mjs';
import { sha256Utf8, utf8ByteLength, equalJson } from './json-identity.mjs';
import { canonicalBackupSchemaCheck } from './backup-schema-check.mjs';

const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
const exact=(v,keys)=>v!==null && typeof v==='object' && !Array.isArray(v) &&
  Object.keys(v).length===keys.length && keys.every(k=>own(v,k));
const sha=/^[0-9a-f]{64}$/;

export function prepareCanonicalBackupMerge(params) {
  if(!exact(params,['currentSnapshot','incomingSnapshot','expectedCandidateSha256']) ||
      typeof params.expectedCandidateSha256!=='string' ||
      !sha.test(params.expectedCandidateSha256))
    throw new TypeError('Exact approved preview identity required');
  const {currentSnapshot,incomingSnapshot,expectedCandidateSha256}=params;
  const preview=previewCanonicalBackupMerge(currentSnapshot,incomingSnapshot);
  if(preview.hypotheticalMergedSha256!==expectedCandidateSha256 ||
      preview.historyDropped!==0 || preview.hypotheticalMergedBytes>MERGE_PREVIEW_LIMIT ||
      preview.flags.length!==0)
    throw new TypeError('Merge risks require a separate approved policy');
  const current=parseStoredData(currentSnapshot);
  const merged=mergeStores(current,parseStoredData(incomingSnapshot));
  if(!merged || !merged.data || !merged.stats || merged.stats.historyDropped!==0)
    throw new TypeError('Canonical merge unavailable');
  // Preflight's generic conflict statistic does not detect deletion by
  // incoming tombstone, and scalar setting precedence can change user
  // choices without a record collision. Refuse both until a detailed,
  // explicit, user-approved conflict/deletion policy has been certified.
  for(const key of ['saved','history','cycles','custom','gyms']) {
    const candidates=new Map((merged.data[key]||[]).map(row=>[row.id,row]));
    if((current[key]||[]).some(row=>!candidates.has(row.id) ||
        !equalJson(row,candidates.get(row.id))))
      throw new TypeError('Existing owned record would be changed');
  }
  // Recheck excluded structural metadata independently; a deletion clock
  // can poison a future merge, and perf is an authoritative cache for the UI.
  if(Object.entries(merged.data.tombs || {}).some(([id,clock]) =>
      !own(current.tombs || {},id) || current.tombs[id]!==clock))
    throw new TypeError('Deletion clocks would be changed');
  if(Object.entries(current.perf || {}).some(([id,value]) =>
      !own(merged.data.perf || {},id) || !equalJson(value,merged.data.perf[id])))
    throw new TypeError('Existing performance mirror would change');
  // An append-only preparation may introduce new non-conflicting records,
  // but must not silently change the existing device's settings, notes,
  // bodyweight, equipment or preferences.
  const exempt=new Set(['v','savedAt','saved','history','cycles','custom',
    'gyms','tombs','perf']);
  for(const key of Object.keys(current)) {
    if(!exempt.has(key) && !equalJson(current[key],merged.data[key]))
      throw new TypeError('Existing owned setting would change');
  }
  const candidateSnapshot=JSON.stringify(merged.data);
  const candidateBytes=utf8ByteLength(candidateSnapshot);
  canonicalBackupSchemaCheck({rawSnapshot:candidateSnapshot});
  if(candidateBytes!==preview.hypotheticalMergedBytes ||
      candidateBytes>MERGE_PREVIEW_LIMIT ||
      sha256Utf8(candidateSnapshot)!==expectedCandidateSha256)
    throw new TypeError('Canonical candidate changed since preflight');
  return {
    source:'modules/engine-api.js:mergeStores',
    validation:'canonical-v13-merge-preparation',
    preparedOnly:true,persisted:false,approvedForImport:false,
    approvedForWorkoutExecution:false,
    requiresAtomicUndo:true,storeVersion:13,
    currentSha256:preview.currentSha256,incomingSha256:preview.incomingSha256,
    candidateSnapshotSha256:expectedCandidateSha256,candidateSnapshotBytes:candidateBytes,
    candidateSnapshot
  };
}
