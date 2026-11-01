# Backup intake — read-only canonical validation

The web App callbacks own persistence, merge, compatibility and Undo. The backup panel is UI-only, and browser storage is protected by `modules/app-storage-ownership.js`. Android must never bypass those owners.

Two read-only commands inspect an explicitly supplied backup **copy**:
- `node android-native/data-migration/inspect-backup.mjs /path/to/copy.json` shows structural keys and counts without record values.
- `node android-native/data-migration/validate-canonical-backup.mjs /path/to/copy.json` uses the actual web `parseStoredData` and `STORE_VERSION` exports, returning only schema versions and collection counts. This may migrate **in memory** but never writes to disk.

Current canonical store schema is **v13**. `parseStoredData` validates list/object shapes, programs, cycles, reminders and rejects newer versions; `migrateStore` migrates earlier schema versions. History revisions use `updatedAt` and tombstones during `mergeStores`. The Android app must reuse the same rules rather than inventing a Kotlin merge algorithm.

Neither command imports a backup into Android, stores a copy, or certifies native round-trip safety. Do not commit real backups or their output if sensitive. The next gate is deterministic full-record round-trip and conflict resolution parity using **synthetic** fixtures, then Android runtime execution.

The read-only `roundtrip-revisions-test.mjs` uses **synthetic** records to check v13 parsing, migration from v12, per-session edit-clock precedence, tombstones, explicit resurrection and chronology-safe current performance. This is a contract/parity step, not a stored Android importer; it never writes user files.

## Read-only saved-program identity (inactive in the app)

`read-only-program-reference.mjs` accepts **only an explicitly supplied v13
snapshot string**, validates it with the canonical `parseStoredData` owner, and
derives an immutable five-field identity for one unambiguous entry in `saved`:
program ID, per-program `updatedAt`, store `savedAt`, schema version, and
SHA-256 of the **exact UTF-8 snapshot bytes**. It rejects invalid or ambiguous
metadata, and refuses deleted programs whose tombstones are at/after the
record revision. `matchesCurrentReadOnlyProgram` checks the same reference
again against current bytes, failing closed if any record or other store field
changes. Older backups can still be inspected with the previous commands,
but must be migrated and revalidated through the canonical owner before
ever qualifying for the v13 reference gate.

This helper does not import, deserialize program details for display, cache
private data, resolve `drafts`/`custom`/`cycles`, execute native JS, grant an
engine backend permission or enable Workout. There is no saved-program picker
in the Android preview. The test suite uses only invented data and canonical
`mergeStores` outcomes, including equal/newer tombstones and resurrection.
For future Android use the snapshot provenance, device-safe reading, complete
data lifecycle, and native DTO decoding still require independent acceptance.

## Source-bound program structure transport (no app import)

The **inactive** `read-only-program-transport.mjs` projects only bounded
saved-program metadata from exact canonical **v13** bytes. The caller supplies
a `ReadOnlyProgramReference` for the same current snapshot. The canonical
`parseStoredData` validates the source; exact snapshot SHA-256, saved-program
edit clock and tombstones are rechecked before projection.

The payload contains: read-only/source/schema markers, exact snapshot
reference, program ID/name, goal/custom status/weeks, and ordered day IDs,
labels and stable exercise IDs. It contains **no working loads, rep targets,
RIR, history, personal records or write instructions**. Ambiguous or malformed
structure fails closed; unrecognized layouts are not guessed or normalized.

`domain-contract/ReadOnlyProgramTransportCodec.kt` independently checks
the exact keys, all scalar types, bounds, source and expected reference.
The same wholly synthetic v13 snapshot and wire envelope are tested by Node
and Kotlin via `src/test/resources/read-only-transport-golden.json`.

**Remaining barriers:** native source acquisition/import consent, authentic
snapshot provenance, current-store revision/tombstone revalidation on device,
lifecycle refresh, access controls, privacy review and full device acceptance.
The app does not call this code, show personal data, store the snapshot or
unlock workout execution. Do not use this diagnostic payload to drive
prescriptions or analytics.
