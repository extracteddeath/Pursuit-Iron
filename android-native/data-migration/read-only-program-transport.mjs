/**
 * INACTIVE read-only transport prototype. A caller must supply exact current
 * canonical v13 bytes AND the matching active saved-program reference.
 * This module never opens device storage, imports backups, computes training
 * targets or authorizes set logging. Only bounded program structure is copied.
 */
import { parseStoredData } from '../../modules/engine-api.js';
import { matchesCurrentReadOnlyProgram } from './read-only-program-reference.mjs';

export const PROGRAM_SUMMARY_SOURCE = 'modules/engine-api.js:parseStoredData';
export const PROGRAM_SUMMARY_PROTOCOL = 1;
const MAX_WIRE_BYTES = 65536;
const id = (value, max = 128) =>
  typeof value === 'string' && value.length <= max &&
  /^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(value);
const text = (value, max) =>
  typeof value === 'string' && value.length > 0 && value.length <= max &&
  value.trim().length > 0 && !/[\x00-\x1f\x7f-\x9f]/.test(value);
const integer = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;
const plain = value => value && typeof value === 'object' && !Array.isArray(value);

export function projectReadOnlySavedProgram(rawSnapshot, currentReference) {
  if (!matchesCurrentReadOnlyProgram(rawSnapshot, currentReference))
    throw new TypeError('Current canonical program reference is unavailable');
  // Same canonical parser as web; deliberately no second native schema/merge.
  const store = parseStoredData(rawSnapshot);
  const rows = store.saved.filter(row => row.id === currentReference.programId);
  if (rows.length !== 1)
    throw new TypeError('Saved-program identity is no longer unambiguous');
  const record = rows[0];
  if (!id(record.id) || !text(record.name, 120) ||
      !plain(record.config) || !text(record.config.goal, 40) ||
      !integer(record.weeks, 1, 104) ||
      (record.custom !== undefined && typeof record.custom !== 'boolean') ||
      !Array.isArray(record.days) || record.days.length < 1 ||
      record.days.length > 7)
    throw new TypeError('Unprojectable saved-program metadata');
  const dayIds = new Set();
  const days = record.days.map(day => {
    if (!plain(day) || !id(day.id) || dayIds.has(day.id) ||
        !text(day.label, 120) || !Array.isArray(day.exercises) ||
        day.exercises.length > 30 || !day.exercises.every(ex => id(ex, 80)))
      throw new TypeError('Unprojectable saved-program day');
    dayIds.add(day.id);
    return {
      id: day.id,
      label: day.label,
      exerciseIds: [...day.exercises]
    };
  });
  // Separate references and metadata retain one exact revision. There are no
  // calculated working weights, rep/RIR ranges, edits, or history fields.
  const envelope = {
    protocolVersion: PROGRAM_SUMMARY_PROTOCOL,
    source: PROGRAM_SUMMARY_SOURCE,
    readOnly: true,
    reference: {
      programId: currentReference.programId,
      programUpdatedAt: currentReference.programUpdatedAt,
      storeSavedAt: currentReference.storeSavedAt,
      storeVersion: currentReference.storeVersion,
      snapshotSha256: currentReference.snapshotSha256
    },
    program: {
      id: record.id,
      name: record.name,
      custom: record.custom === true,
      goal: record.config.goal,
      weeks: record.weeks,
      days
    }
  };
  if (Buffer.byteLength(JSON.stringify(envelope), 'utf8') > MAX_WIRE_BYTES)
    throw new TypeError('Oversized read-only program projection');
  return envelope;
}
