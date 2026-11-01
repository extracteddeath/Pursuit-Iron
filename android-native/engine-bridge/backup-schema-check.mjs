/**
 * Read-only canonical v13 backup schema PROBE (not an importer). Only a
 * deliberately small JSON source string is accepted. The authoritative
 * parser remains modules/engine-api.js; no secondary schema/merge is created.
 * Results expose counts only: never program names, records or history.
 */
import { parseStoredData, STORE_VERSION } from '../../modules/engine-api.js';

const plain = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const clock = v => Number.isSafeInteger(v) && v >= 0;
const allowed = ['rawSnapshot'];
export const BACKUP_PROBE_SOURCE = 'modules/engine-api.js:parseStoredData';
export const MAX_BACKUP_PROBE_CHARS = 65536;

export function validBackupSchemaParams(params) {
  return plain(params) && Object.keys(params).length === allowed.length &&
    Object.prototype.hasOwnProperty.call(params, 'rawSnapshot') &&
    typeof params.rawSnapshot === 'string' &&
    params.rawSnapshot.length > 0 &&
    params.rawSnapshot.length <= MAX_BACKUP_PROBE_CHARS;
}

export function canonicalBackupSchemaCheck(params) {
  if (!validBackupSchemaParams(params))
    throw new TypeError('Invalid bounded source');
  const source = JSON.parse(params.rawSnapshot);
  if (!plain(source) || source.v !== 13 || STORE_VERSION !== 13 ||
      !clock(source.savedAt) || !Array.isArray(source.saved) ||
      !Array.isArray(source.history) || !Array.isArray(source.cycles) ||
      !Array.isArray(source.custom) || !Array.isArray(source.gyms) ||
      !plain(source.perf) || !plain(source.tombs))
    throw new TypeError('Explicit canonical v13 source metadata is required');
  const parsed = parseStoredData(params.rawSnapshot);
  if (!parsed || parsed.v !== STORE_VERSION || !clock(parsed.savedAt))
    throw new TypeError('Canonical schema parse rejected source');
  const keys = ['saved','history','cycles','custom','gyms'];
  const counts = {};
  for (const key of keys) {
    if (!Array.isArray(parsed[key]) || parsed[key].length > 100000)
      throw new TypeError('Invalid canonical collection');
    counts[key] = parsed[key].length;
  }
  return Object.freeze({
    source: BACKUP_PROBE_SOURCE,
    readOnly: true,
    validation: 'canonical-v13-structure-only',
    imported: false,
    approvedForWorkoutExecution: false,
    storeVersion: parsed.v,
    counts
  });
}
