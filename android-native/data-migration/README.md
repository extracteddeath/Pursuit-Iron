# Backup intake — read-only canonical validation

The web App callbacks own persistence, merge, compatibility and Undo. The backup panel is UI-only, and browser storage is protected by `modules/app-storage-ownership.js`. Android must never bypass those owners.

Two read-only commands inspect an explicitly supplied backup **copy**:
- `node android-native/data-migration/inspect-backup.mjs /path/to/copy.json` shows structural keys and counts without record values.
- `node android-native/data-migration/validate-canonical-backup.mjs /path/to/copy.json` uses the actual web `parseStoredData` and `STORE_VERSION` exports, returning only schema versions and collection counts. This may migrate **in memory** but never writes to disk.

Current canonical store schema is **v13**. `parseStoredData` validates list/object shapes, programs, cycles, reminders and rejects newer versions; `migrateStore` migrates earlier schema versions. History revisions use `updatedAt` and tombstones during `mergeStores`. The Android app must reuse the same rules rather than inventing a Kotlin merge algorithm.

Neither command imports a backup into Android, stores a copy, or certifies native round-trip safety. Do not commit real backups or their output if sensitive. The next gate is deterministic full-record round-trip and conflict resolution parity using **synthetic** fixtures, then Android runtime execution.

The read-only `roundtrip-revisions-test.mjs` uses **synthetic** records to check v13 parsing, migration from v12, per-session edit-clock precedence, tombstones, explicit resurrection and chronology-safe current performance. This is a contract/parity step, not a stored Android importer; it never writes user files.
