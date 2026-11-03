# Native Kotlin workout journal store — isolated integration candidate

**Status: JVM-tested integration candidate, NOT the production Android workout
owner, and NOT the final end-to-end Kotlin cutover.** The Android app does not
depend on this module. The legacy JavaScript runtime remains unchanged.

## What exists

`KotlinNativeJournalFileStore` provides a **private-directory, per-athlete,
per-workout atomic compare-and-swap** boundary for the already-developed
immutable `KotlinStagedWorkout`, `KotlinStoredWorkoutIdentity`,
`KotlinJournalOperation` and `KotlinWorkoutJournalReplayDomain`.

- A new session requires an independently authenticated, already pinned
  immutable Kotlin-issued workout snapshot and identity. `issue` is
  idempotent for identical source; conflicting snapshots refuse to overwrite.
- `append` obtains a cross-process file lock, reloads and checksum-verifies
  current bytes, replays **all original receipts**, checks the expected
  journal revision and deduplicates event IDs, then writes an atomically
  replaced file after flushing file contents. It never alters a target.
- A retry after a lost acknowledgment finds the previous receipt and returns
  `replayed=true`, rather than producing a duplicate set or finish.
- `open` never creates a missing workout. It detects invalid source,
  mismatched storage identity, incomplete/corrupt receipt history and
  contradictory finished state.
- The binary protocol uses a fixed magic/version, bounded receipt count and
  length, SHA-256 checksum, and snapshot fingerprint covering **all immutable
  issued targets**. It is a corruption check, **not a cryptographic signature**.
- All application-facing I/O errors are explicit refusals. There is no
  fallback to JavaScript or a non-atomic write mode.

## Important incomplete security and durability gates

1. **Authenticated native storage owner**: only the real authenticated
   athlete's current private `filesDir` may supply `ownerKey`. Neither this
   stand-alone module nor the SHA checksum authenticates that identity. The
   owner must protect files from untrusted/symlink paths and scope requests.
2. **Native-issued snapshot storage**: the full Kotlin-approved immutable
   snapshot and grant must be durably stored and checked before `issue`.
   Passing a caller-constructed snapshot directly to `issue` without that
   verification is forbidden. The module persists journal receipts only.
3. **Power-loss durability**: the journal temp file is flushed and atomically
   renamed, but parent directory metadata flush behavior differs on Android
   filesystems. Validate it on real supported devices and choose an
   appropriately proven native transaction layer before production.
4. **No encryption built in**: rely on correctly scoped app-private Android
   storage until a separately specified at-rest encryption design is reviewed.
   Backups, privacy, logout and per-user cleanup are not yet integrated.
5. **No release integration**: no Compose screen uses this module, and the
   production JS workout engine still owns live preview workflows. Offline
   full session issuance, restore, history migration, user-approved cutover,
   and physical-device tests are outstanding.

## Verification

The `android-kotlin-rebuild.yml` workflow runs the entire Kotlin coaching
suite and `:native-coach-store:testDebugUnitTest` (local JVM tests of the
Android-compatible java.nio implementation, not instrumentation tests).
Regression cases include cold reopen after process restart, idempotent replay,
revision conflicts, correction history, different athlete scopes, altered
issued targets and corrupted on-disk bytes. These results are necessary but
not proof of Android device crash/power-loss acceptance.

See [complete JS replacement contract](../kotlin-engine/FULL_REPLACEMENT_CONTRACT.md).
