# Native migration checkpoint — 2026-11-01

Branch: `architecture/android-compose-foundation`. APK production stays frozen.

The source journey covers onboarding, generated review and explicit program
save, engine-issued working sets and myo/drop extensions, durable logging,
interruption recovery, canonical completion and atomic one-shot deload
consumption. This pass adds durable corrections before workout completion, alongside the
existing latest-saved-workout editor. This is source verification, not device
acceptance.

## Active-workout corrections

Completed or skipped working/myo/drop rows can be corrected before Finish.
The shared logger still displays only acknowledged receipts and pinned targets.
Each edit names the exact previous operation for its slot and appends a new
receipt. A stale edit cannot overwrite a newer one. Complete, Skip and restore
use the same path; progress and finish counts resolve each slot once.

Unedited journals retain format 2 and its exact existing handoff. Edited
journals use format 3 with an ordered receipt chain. The native reader rejects
repeated or malformed durable events while allowing exact API retries. The
journal reserves storage for all remaining observations and Finish before
accepting an edit, so edits cannot consume the final logging capacity.

Canonical completion uses the latest value per slot and retains the full
original chain in `nativeSetRevisions`, alongside the effective
`nativeSetObservations`. The atomic store checks both against the journal before
retiring recovery. Original targets and working-set positions are unchanged;
no progression formulas moved into Kotlin. The JS tests compare the resulting
performance against the actual canonical logger on the final observations.
Subsequent saved-history corrections retain the in-workout audit.

Both correction surfaces share `NativeSetEditSheet` and numeric inputs, with
keyboard scrolling and dismissal protection during an unacknowledged save.
The sheet uses the current Material 3 bottom-sheet state factory. Pending edits
retain their operation and disposition for exact retries and close after the
selected receipt changes, rather than optimistically marking the edit saved.

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

- `node android-native/engine-bridge/native-workout-correction-test.mjs` and
  `node android-native/engine-bridge/native-history-test.mjs`.
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

## Verified locally for this pass

- 103 domain JVM tests and 20 packaged-runtime JVM tests passed, with no skips.
- Compose Kotlin and instrumentation Java source compilation passed. The last
  shared-sheet refinement was compiled again successfully.
- Native history, workout, extension, deload, protocol, onboarding, backup,
  progression and history edit/volume/merge regression checks passed.
- Canonical source export, independent parity, snapshot identity, actual IIFE
  bundle verification passed. The canonical training modules and web release
  package remain unchanged in this pass (source candidate 1010).
- APK-freeze checks passed; zero APK files were produced under `android-native`.

Saved-history reload and correction are serialized against starting/finishing
an active workout. Active corrections remain available until durable Finish. Row buttons and the correction sheet use the same distinct
working-set or numbered myo/drop label. Canonical correction decoding also
checks the requested operation and prior-entry identity before publication.

## Publication boundary

The user explicitly approved publishing the correction commits to
`extracteddeath/Pursuit-Iron`, branch `architecture/android-compose-foundation`,
on the condition that the other programmer's native app and the web app stay
unchanged. Publication is limited to this development branch. Preserve every
other branch, including `main`, `native-app` and `native-smoke`; do not merge,
deploy or produce an APK. The live GitHub Pages deployment is sourced from
`main`. Branch heads and live index/service-worker/app/records bytes are checked
before and after publication. The verification results above are local results;
GitHub CI results must be checked independently for the pushed revision.

## Next work

Broader history browsing, saved-history correction of skipped/omitted rows or
failed attempts, duplicate exercise-slot history editing, native session-
adjustment controls and imported web history remain separate milestones. Physical-device interaction, interruption and accessibility
acceptance is still required for the complete agreed journey; preserve the
APK freeze until its release gate has the required evidence.
