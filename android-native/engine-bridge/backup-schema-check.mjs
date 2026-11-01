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

/**
 * Explicit backup-only PROGRAM INVENTORY, never a live program projection.
 * Returns bounded day labels/counts; no exercise IDs, history, load, RIR,
 * storage writes, or permission to activate a program.
 */
const programId = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;
const safeName = name => typeof name === 'string' && name.trim().length > 0 &&
  name.length <= 120 && !/[\x00-\x1f\x7f-\x9f]/.test(name);
const own = (v, k) => Object.prototype.hasOwnProperty.call(v, k);

export function canonicalBackupProgramInventory(params) {
  const schema = canonicalBackupSchemaCheck(params);
  const store = parseStoredData(params.rawSnapshot); // same canonical schema owner
  if (store.saved.length > 24) throw new TypeError('Too many saved programs for bounded preview');
  const seen = new Set();
  const programs = [];
  for (const program of store.saved) {
    if (!plain(program) || typeof program.id !== 'string' ||
        !programId.test(program.id) || seen.has(program.id) ||
        !clock(program.updatedAt))
      throw new TypeError('Ambiguous program revision in backup');
    seen.add(program.id);
    const tomb = own(store.tombs, program.id) ? store.tombs[program.id] : null;
    if (tomb !== null && !clock(tomb))
      throw new TypeError('Invalid program deletion clock');
    if (tomb !== null && tomb >= program.updatedAt) continue;
    if (!safeName(program.name) || !Array.isArray(program.days) ||
        program.days.length < 1 || program.days.length > 7)
      throw new TypeError('Invalid active backup program');
    let exerciseSlots = 0;
    // Project only labels and exercise counts. No exercise IDs, weights,
    // targets, sets or history may travel through the backup inventory.
    const days = program.days.map(day => {
      if (!plain(day) || !Array.isArray(day.exercises) ||
          day.exercises.length > 30 ||
          (day.label != null && !safeName(day.label)))
        throw new TypeError('Invalid saved-program day');
      exerciseSlots += day.exercises.length;
      return {
        label: day.label == null ? null : day.label,
        exerciseSlots: day.exercises.length
      };
    });
    programs.push({
      id: program.id,
      name: program.name,
      updatedAt: program.updatedAt,
      trainingDays: days.length,
      exerciseSlots,
      days
    });
  }
  return Object.freeze({
    source: BACKUP_PROBE_SOURCE,
    readOnly: true,
    validation: 'canonical-v13-backup-program-inventory',
    snapshotOnly: true,
    imported: false,
    approvedForWorkoutExecution: false,
    storeVersion: schema.storeVersion,
    storeSavedAt: store.savedAt,
    programs
  });
}
