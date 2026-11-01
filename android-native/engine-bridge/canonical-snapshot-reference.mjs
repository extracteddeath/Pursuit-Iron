/**
 * Read-only identity for a SAVED program in an explicitly supplied canonical v13
 * backup snapshot. This is not an importer, program prescription, UI data
 * source, persistent cache, or engine implementation.
 *
 * The digest covers the exact UTF-8 snapshot bytes, not a reserialized object.
 * Any edit elsewhere in the store invalidates the reference conservatively.
 * The caller MUST revalidate current bytes before any future read-only use.
 */
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';
import { parseStoredData, STORE_VERSION } from '../../modules/engine-api.js';

const PROGRAM_ID = /^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$/;
const DIGEST = /^[0-9a-f]{64}$/;
const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
const clock = value => Number.isSafeInteger(value) && value >= 0;
const MAX_SNAPSHOT_BYTES = 50 * 1024 * 1024;

function currentV13Store(raw) {
  if (typeof raw !== 'string' || !raw || utf8ByteLength(raw) > MAX_SNAPSHOT_BYTES)
    throw new TypeError('Expected a bounded UTF-8 snapshot');
  // The canonical parser can migrate a legacy version in memory. This reference
  // intentionally requires source v13 to avoid mistaking an inferred clock for
  // one supplied in the original on-disk record.
  const header = JSON.parse(raw);
  if (!header || typeof header !== 'object' || Array.isArray(header) ||
      header.v !== STORE_VERSION || STORE_VERSION !== 13)
    throw new TypeError('An explicit canonical v13 store is required');
  const parsed = parseStoredData(raw); // The ONLY owner of the backup schema.
  if (parsed.v !== 13 || !clock(parsed.savedAt) ||
      !Array.isArray(parsed.saved) || !parsed.tombs ||
      typeof parsed.tombs !== 'object' || Array.isArray(parsed.tombs))
    throw new TypeError('Incomplete canonical saved-program revision metadata');
  return parsed;
}

/**
 * Returns null for an absent or tombstoned program. Throws on ambiguous or
 * malformed metadata, rather than silently selecting a different revision.
 * Never includes program content, exercise data, preferences or history.
 */
export function deriveReadOnlyProgramReference(raw, programId) {
  if (typeof programId !== 'string' || !PROGRAM_ID.test(programId))
    throw new TypeError('Invalid program identity');
  const store = currentV13Store(raw);
  const rows = store.saved.filter(row => row.id === programId);
  if (rows.length === 0) return null;
  if (rows.length !== 1) throw new TypeError('Ambiguous saved-program identity');
  const program = rows[0];
  if (!clock(program.updatedAt))
    throw new TypeError('Program revision clock missing or invalid');
  const tombstone = own(store.tombs, programId) ? store.tombs[programId] : null;
  if (tombstone !== null && !clock(tombstone))
    throw new TypeError('Invalid program tombstone clock');
  if (tombstone !== null && tombstone >= program.updatedAt) return null;
  return Object.freeze({
    programId,
    programUpdatedAt: program.updatedAt,
    storeSavedAt: store.savedAt,
    storeVersion: store.v,
    snapshotSha256: sha256Utf8(raw)
  });
}

/** Compares an explicit prior reference against CURRENT raw bytes; fail closed. */
export function matchesCurrentReadOnlyProgram(raw, reference) {
  if (!reference || typeof reference !== 'object' || Array.isArray(reference) ||
      Object.keys(reference).length !== 5 ||
      typeof reference.programId !== 'string' || !PROGRAM_ID.test(reference.programId) ||
      reference.storeVersion !== 13 ||
      !clock(reference.programUpdatedAt) || !clock(reference.storeSavedAt) ||
      typeof reference.snapshotSha256 !== 'string' ||
      !DIGEST.test(reference.snapshotSha256)) return false;
  try {
    const current = deriveReadOnlyProgramReference(raw, reference.programId);
    return current !== null && Object.keys(current).every(key => current[key] === reference[key]);
  } catch {
    return false;
  }
}
