/**
 * Dry-run only: compare a selected v13 backup with a separate, explicitly
 * supplied current v13 snapshot. The canonical merge owner remains
 * modules/engine-api.js; no result contains records or publishable bytes.
 *
 * A later import flow must separately re-authenticate both exact sources,
 * obtain explicit consent, journal an Undo snapshot, and atomically publish.
 */
import { mergeStores, parseStoredData } from '../../modules/engine-api.js';
import { canonicalBackupSchemaCheck } from './backup-schema-check.mjs';
import { sha256Utf8, utf8ByteLength } from './json-identity.mjs';

export const MERGE_PREVIEW_LIMIT = 1024 * 1024;
const COLLECTIONS = ['saved', 'history', 'cycles', 'custom', 'gyms'];
const own = (obj, name) => Object.prototype.hasOwnProperty.call(obj, name);

function inspect(raw) {
  if (typeof raw !== 'string' || raw.length === 0 ||
      raw.length > MERGE_PREVIEW_LIMIT ||
      utf8ByteLength(raw) > MERGE_PREVIEW_LIMIT)
    throw new TypeError('Bounded exact snapshot required');
  canonicalBackupSchemaCheck({ rawSnapshot: raw });
  const store = parseStoredData(raw);
  if (store.v !== 13) throw new TypeError('Unmigrated source');

  // The web merge coalesces rows by id. An ambiguous source must not
  // silently lose duplicate IDs before the user even reviews the preview.
  for (const collection of COLLECTIONS) {
    const seen = new Set();
    for (const record of store[collection]) {
      if (!record || typeof record !== 'object' || Array.isArray(record) ||
          typeof record.id !== 'string' || record.id.length < 1 ||
          record.id.length > 128 || seen.has(record.id) ||
          (record.updatedAt != null &&
            (!Number.isSafeInteger(record.updatedAt) || record.updatedAt < 0)))
        throw new TypeError('Unverifiable record identity');
      seen.add(record.id);
    }
  }
  for (const [id, clock] of Object.entries(store.tombs)) {
    if (!id || id.length > 128 || !Number.isSafeInteger(clock) || clock < 0)
      throw new TypeError('Invalid deletion revision');
  }
  return store;
}

const counts = store => Object.fromEntries(
  COLLECTIONS.map(name => [name, store[name].length])
);

export function previewCanonicalBackupMerge(currentSnapshot, incomingSnapshot) {
  const current = inspect(currentSnapshot);
  const incoming = inspect(incomingSnapshot);
  const prior = counts(current), selected = counts(incoming);
  const result = mergeStores(current, incoming);
  if (!result || !result.data || !result.stats ||
      result.data.v !== 13) throw new TypeError('Merge not available');
  const merged = result.data;
  // Do not promote a preview if the canonical union generated an invalid
  // store or ambiguous identity after the same merge policy ran.
  inspectSerializedResult(merged);
  const candidateText = JSON.stringify(merged);
  const candidateBytes = utf8ByteLength(candidateText);
  const historyDropped = result.stats.historyDropped;
  const conflicts = result.stats.conflicts;
  if (!Number.isSafeInteger(historyDropped) || historyDropped < 0 ||
      !Number.isSafeInteger(conflicts) || conflicts < 0)
    throw new TypeError('Invalid canonical merge summary');
  const capacityExceeded = candidateBytes > MERGE_PREVIEW_LIMIT;
  const flags = [];
  if (historyDropped > 0) flags.push('HISTORY_CAP_LOSS');
  if (capacityExceeded) flags.push('NATIVE_STORE_CAPACITY');
  if (conflicts > 0) flags.push('RECORD_CONFLICTS_REQUIRE_REVIEW');
  // A nonzero risk list is not an authorization policy: even a clean
  // summary is only a read-only estimate, never a consent or write receipt.
  return Object.freeze({
    source: 'modules/engine-api.js:mergeStores',
    validation: 'canonical-v13-merge-preview',
    readOnly: true, imported: false, approvedForImport: false,
    approvedForWorkoutExecution: false,
    requiresExplicitApproval: true,
    storeVersion: 13,
    currentSha256: sha256Utf8(currentSnapshot),
    incomingSha256: sha256Utf8(incomingSnapshot),
    hypotheticalMergedSha256: sha256Utf8(candidateText),
    hypotheticalMergedBytes: candidateBytes,
    counts: { current: prior, incoming: selected, hypothetical: counts(merged) },
    conflicts, historyDropped, flags
  });
}

function inspectSerializedResult(store) {
  for (const collection of COLLECTIONS) {
    if (!Array.isArray(store[collection])) throw new TypeError('Invalid merged rows');
    const seen = new Set();
    for (const row of store[collection]) {
      if (!row || typeof row.id !== 'string' || row.id.length < 1 ||
          row.id.length > 128 || seen.has(row.id))
        throw new TypeError('Ambiguous merged identity');
      seen.add(row.id);
    }
  }
  if (!store.tombs || typeof store.tombs !== 'object' ||
      !own(store, 'perf') || !store.perf || typeof store.perf !== 'object')
    throw new TypeError('Incomplete merged store');
}
