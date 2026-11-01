/**
 * Pure, environment-neutral canonical v13 SAVED PROGRAM projection.
 * Android and Node share one program-structure mapping; no Kotlin engine
 * replica. The supplied SHA is a CLAIM: only trusted native byte hashing or
 * Node's full snapshot-reference check can authenticate it.
 *
 * No storage access, workout prescription, writes, or backup import.
 */
import { parseStoredData, STORE_VERSION } from '../../modules/engine-api.js';

export const PROGRAM_SUMMARY_SOURCE = 'modules/engine-api.js:parseStoredData';
export const PROGRAM_SUMMARY_PROTOCOL = 1;
const idPattern = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;
const exerciseIdPattern = /^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$/;
const shaPattern = /^[0-9a-f]{64}$/;
const integer = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;
const plain = v => v !== null && typeof v === 'object' && !Array.isArray(v);
const text = (v, max) => typeof v === 'string' && v.length > 0 &&
  v.length <= max && v.trim().length > 0 && !/[\x00-\x1f\x7f-\x9f]/.test(v);
const owns = (object,key) => Object.prototype.hasOwnProperty.call(object,key);

function utf8Bytes(string) {
  let count = 0;
  for (let i = 0; i < string.length; i++) {
    const cp = string.codePointAt(i);
    if (cp > 65535) i++;
    count += cp < 128 ? 1 : cp < 2048 ? 2 : cp < 65536 ? 3 : 4;
  }
  return count;
}

export function validCurrentProjectionParams(params) {
  return plain(params) && Object.keys(params).length === 3 &&
    typeof params.rawSnapshot === 'string' &&
    params.rawSnapshot.length > 0 && params.rawSnapshot.length <= 1024*1024 &&
    typeof params.programId === 'string' && idPattern.test(params.programId) &&
    typeof params.snapshotSha256 === 'string' && shaPattern.test(params.snapshotSha256);
}

export function projectCanonicalSavedProgram(rawSnapshot, programId, snapshotSha256) {
  if (!validCurrentProjectionParams({rawSnapshot,programId,snapshotSha256}))
    throw new TypeError('Invalid bounded current program projection');
  const raw = JSON.parse(rawSnapshot);
  if (!plain(raw) || raw.v !== 13 || STORE_VERSION !== 13)
    throw new TypeError('Explicit canonical v13 snapshot required');
  const store = parseStoredData(rawSnapshot);
  if (store.v !== 13 || !integer(store.savedAt,0,Number.MAX_SAFE_INTEGER) ||
      !Array.isArray(store.saved) || !plain(store.tombs))
    throw new TypeError('Unprojectable canonical revision metadata');
  const rows = store.saved.filter(row => row?.id === programId);
  if (rows.length !== 1)
    throw new TypeError('Current canonical program reference is unavailable');
  const record = rows[0];
  if (!plain(record) || !integer(record.updatedAt,0,Number.MAX_SAFE_INTEGER))
    throw new TypeError('Unprojectable saved program revision');
  const deletedAt = owns(store.tombs,programId) ? store.tombs[programId] : null;
  if (deletedAt !== null && !integer(deletedAt,0,Number.MAX_SAFE_INTEGER))
    throw new TypeError('Unprojectable canonical tombstone');
  if (deletedAt !== null && deletedAt >= record.updatedAt)
    throw new TypeError('Current canonical program reference is unavailable');
  if (!text(record.name,120) || !plain(record.config) ||
      !text(record.config.goal,40) || !integer(record.weeks,1,104) ||
      (record.custom !== undefined && typeof record.custom !== 'boolean') ||
      !Array.isArray(record.days) || record.days.length < 1 || record.days.length > 7)
    throw new TypeError('Unprojectable saved-program metadata');
  const seenDays = new Set();
  const days = record.days.map(day => {
    if (!plain(day) || typeof day.id !== 'string' ||
        !idPattern.test(day.id) || seenDays.has(day.id) ||
        !text(day.label,120) || !Array.isArray(day.exercises) ||
        day.exercises.length > 30 ||
        !day.exercises.every(ex => typeof ex === 'string' && exerciseIdPattern.test(ex)))
      throw new TypeError('Unprojectable saved-program day');
    seenDays.add(day.id);
    return { id:day.id, label:day.label, exerciseIds:[...day.exercises] };
  });
  const envelope = {
    protocolVersion:PROGRAM_SUMMARY_PROTOCOL,
    source:PROGRAM_SUMMARY_SOURCE,
    readOnly:true,
    reference:{
      programId, programUpdatedAt:record.updatedAt,
      storeSavedAt:store.savedAt, storeVersion:store.v, snapshotSha256
    },
    program:{
      id:record.id,name:record.name,custom:record.custom === true,
      goal:record.config.goal,weeks:record.weeks,days
    }
  };
  if (utf8Bytes(JSON.stringify(envelope)) > 65536)
    throw new TypeError('Oversized read-only program projection');
  return envelope;
}
