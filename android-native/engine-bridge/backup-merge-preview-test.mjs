import assert from 'node:assert/strict';
import { previewCanonicalBackupMerge, MERGE_PREVIEW_LIMIT } from './backup-merge-preview.mjs';
import { dispatchWorkoutStoreJson } from './workout-store-protocol.mjs';
import { dispatchEngineJson, dispatchBackupSchemaJson } from './protocol.mjs';

// Only invented records. The dry-run must never read, retain or write files.
const history = (id, load, updatedAt, date = 1000) => ({
  id, date, updatedAt, programId: 'synthetic-plan', dayId: 'day-a',
  perf: { 'bb-bench': { weight: load, reps: 8,
    sets: [{ w: load, r: 8, rir: 2 }] } },
  setsDone: 1, volume: load * 8
});
const store = (savedAt, entries = [], tombs = {}) => ({
  v: 13, savedAt, saved: [], history: entries, cycles: [], custom: [],
  gyms: [], perf: {}, tombs
});
const current = JSON.stringify(store(100, [history('old', 200, 10)]));
const incoming = JSON.stringify(store(90, [history('old', 185, 20),
  history('new', 225, 21, 2000)]));
const before = [current, incoming];
const preview = previewCanonicalBackupMerge(current, incoming);
assert.equal(preview.source, 'modules/engine-api.js:mergeStores');
assert.equal(preview.readOnly, true);
assert.equal(preview.imported, false);
assert.equal(preview.approvedForImport, false);
assert.equal(preview.approvedForWorkoutExecution, false);
assert.equal(preview.requiresExplicitApproval, true);
assert.equal(preview.validation, 'canonical-v13-merge-preview');
assert.deepEqual(preview.counts.current, { saved: 0, history: 1, cycles: 0, custom: 0, gyms: 0 });
assert.equal(preview.counts.incoming.history, 2);
assert.equal(preview.counts.hypothetical.history, 2);
assert.equal(preview.conflicts, 1);
assert.ok(preview.flags.includes('RECORD_CONFLICTS_REQUIRE_REVIEW'));
assert.deepEqual([current, incoming], before, 'both sources stay byte-exact');
assert.ok(!JSON.stringify(preview).includes('bb-bench'));
assert.ok(!JSON.stringify(preview).includes('"weight":225'));
assert.ok(!JSON.stringify(preview).includes('synthetic-plan'));
assert.equal(preview.currentSha256.length, 64);
assert.equal(preview.incomingSha256.length, 64);
assert.equal(preview.hypotheticalMergedSha256.length, 64);
assert.deepEqual(preview, previewCanonicalBackupMerge(current, incoming), 'deterministic summary');

const deleted = JSON.stringify(store(130, [], { old: 30 }));
const tombPreview = previewCanonicalBackupMerge(current, deleted);
assert.equal(tombPreview.counts.hypothetical.history, 0,
  'a newer deletion clock must defeat an older record');
const restored = JSON.stringify(store(135, [history('old', 190, 40)], { old: 30 }));
assert.equal(previewCanonicalBackupMerge(deleted, restored).counts.hypothetical.history, 1,
  'a later explicit edit may resurrect a deleted record');

const firstHalf = JSON.stringify(store(100, Array.from({ length: 310 }, (_, i) =>
  history('one-' + i, 100 + i, 20 + i, 1000 + i))));
const secondHalf = JSON.stringify(store(101, Array.from({ length: 310 }, (_, i) =>
  history('two-' + i, 100 + i, 20 + i, 3000 + i))));
const capacity = previewCanonicalBackupMerge(firstHalf, secondHalf);
assert.ok(capacity.historyDropped > 0, 'canonical history cap loss is never silent');
assert.ok(capacity.flags.includes('HISTORY_CAP_LOSS'));
assert.ok(capacity.counts.hypothetical.history <
  capacity.counts.current.history + capacity.counts.incoming.history);
assert.ok(capacity.hypotheticalMergedBytes > 0);

const wire = (method, params) => JSON.stringify({
  protocolVersion: 1, id: 'merge-preflight-1', method, params
});
const allowed = JSON.parse(dispatchWorkoutStoreJson(wire('previewBackupMerge', {
  currentSnapshot: current, incomingSnapshot: incoming
})));
assert.equal(allowed.ok, true);
assert.deepEqual(allowed.result, preview);
for (const dispatcher of [dispatchEngineJson, dispatchBackupSchemaJson]) {
  assert.equal(JSON.parse(dispatcher(wire('previewBackupMerge', {
    currentSnapshot: current, incomingSnapshot: incoming
  }))).ok, false, 'no backup merge operation in the public diagnostic channels');
}
const reject = (a, b) => {
  assert.throws(() => previewCanonicalBackupMerge(a, b));
  const result = JSON.parse(dispatchWorkoutStoreJson(
    wire('previewBackupMerge', { currentSnapshot: a, incomingSnapshot: b })
  ));
  assert.equal(result.ok, false);
  assert.ok(!JSON.stringify(result).includes('synthetic-plan'));
};
reject(current, JSON.stringify({ ...JSON.parse(incoming), v: 14 }));
reject(current, JSON.stringify({ ...JSON.parse(incoming), savedAt: -1 }));
reject(current, JSON.stringify({ ...JSON.parse(incoming),
  history: [...JSON.parse(incoming).history, JSON.parse(incoming).history[0]] }));
reject(current, JSON.stringify({ ...JSON.parse(incoming),
  tombs: { old: -1 } }));
reject(current, JSON.stringify({ ...JSON.parse(incoming),
  history: [{ ...history('bad', 200, -1) }] }));
reject(current, 'x'.repeat(MERGE_PREVIEW_LIMIT + 1));
reject('', incoming);
const withExtra = JSON.parse(dispatchWorkoutStoreJson(wire('previewBackupMerge', {
  currentSnapshot: current, incomingSnapshot: incoming, publish: true
})));
assert.equal(withExtra.ok, false);
assert.equal(JSON.parse(dispatchWorkoutStoreJson(wire('publishBackupMerge', {
  currentSnapshot: current, incomingSnapshot: incoming
}))).ok, false, 'the worker exposes no import writer');
console.log('PASS canonical v13 merge dry-run, redaction, deletion/correction revisions, history cap, bounds and write-channel isolation');
