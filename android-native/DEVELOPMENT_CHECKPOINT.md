# Native migration checkpoint — 2026-11-01

Branch: `architecture/android-compose-foundation`. APK production stays frozen.

The source journey covers onboarding, generated review and explicit program
save, engine-issued working sets and myo/drop extensions, durable logging,
interruption recovery, canonical completion and atomic one-shot deload
consumption. This pass connects corrections to retained sets in the latest
saved native workout. This is source verification, not device acceptance.

## Canonical history corrections

`engine-bridge/native-history.mjs` provides a bounded owned-store reader and
correction candidate. It maps canonical rows to exact original observations,
including out-of-order logging. The native editor can correct load, reps and
optional actual RIR while preserving issued targets, working-set positions,
extension types, skips, omitted zero-load observations and completion identity.
`normalizeEditedHistoryEntry`, `perfAfterHistoryReplace` and `exRecords` remain
the canonical owners. The shared normalizer now preserves extension tonnage
and native observation counts when a saved set changes.

Insights displays those retained sets and canonical heaviest working-set
records. Corrections share the logger's numeric controls and are unavailable
while a workout is pending. The store rechecks that condition and the exact
current snapshot after engine execution. The corrected snapshot and updated
completion-receipt snapshot hash publish atomically. Exact operation/request
identity permits lost-acknowledgement retry without another write. Stale or
changed requests cannot overwrite subsequent edits.

## Required checks for this checkpoint

- `node android-native/engine-bridge/native-history-test.mjs`.
- `node verification/history-edit-integrity-test.mjs` and history volume/merge
  regressions, native working-set/extension/deload/completion/protocol suites.
- Canonical export, snapshot identity, source parity and packaged IIFE proof.
- `:engine-runtime:testDebugUnitTest`, `:domain-contract:testDebugUnitTest`,
  `:app:compileDebugKotlin` and instrumentation Java source compilation.
- APK-freeze contract. APK-producing workflows remain hard-disabled.

Packaged-runtime tests exercise disk-full before correction publication, lost
acknowledgement after publication, restart, exact retry, response tampering,
record recalculation and a concurrent new workout. No new APK is produced.
Shared engine edits require `node scripts/finalize-release.mjs --next-build`
before export. This refreshes source hashes/inventory without deploying the
web app.

## Next work

Broader history browsing, corrections to skipped/omitted rows or failed attempts,
duplicate exercise-slot editing, editing a set before workout completion,
native session-adjustment controls and imported web history remain separate
milestones. Physical-device interaction, interruption and accessibility
acceptance is still required for the complete agreed journey; preserve the
APK freeze until its release gate has the required evidence.
